package esusdata.run.acquisition;

import esusdata.source.pec.CapabilityContract;
import esusdata.source.pec.CapabilityQueryReader;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.GZIPInputStream;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The comparison of the Rust × JDBC differential tests of the foundation capabilities (ADR 0030),
 * test-only: what the execution plane wrote into a canonical v2 extract, part by part, against what
 * {@link CapabilityQueryReader} read with the same frozen query and binds. Rows are compared as
 * multisets — neither side guarantees an order — after both are rendered the same way: every field
 * of the extract's JSON value model (text, date and integer as strings, booleans, text lists, null)
 * under its column name, in name order.
 *
 * <p>Rows can be kept as rendered text, for a synthetic fixture, or reduced to a SHA-256 digest each,
 * so that nothing a source holds ever reaches a log or a failure message: a comparison only ever
 * reports counts and hashes.
 */
final class CapabilityDifferential {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private CapabilityDifferential() {}

    /** One part's rows as the extract holds them, with the contract checks counted by failing row. */
    record PartRows(List<String> keys, long otherMunicipality, long outsideWindow, long missingRequired) {

        PartRows {
            keys = Collections.unmodifiableList(new ArrayList<>(keys));
        }

        long violations() {
            return otherMunicipality + outsideWindow + missingRequired;
        }
    }

    /** How the two sides of one capability compare: counts and hashes, never a row. */
    record Comparison(
            String capability,
            long rustRows,
            long jdbcRows,
            long rustOnly,
            long jdbcOnly,
            String rustHash,
            String jdbcHash) {

        boolean agrees() {
            return rustRows == jdbcRows && rustOnly == 0 && jdbcOnly == 0;
        }

        @Override
        public String toString() {
            return capability + ": rust=" + rustRows + " jdbc=" + jdbcRows + " rustOnly=" + rustOnly + " jdbcOnly="
                    + jdbcOnly;
        }
    }

    /** The part of {@code contract} that binds {@code binds}: only the parameters its descriptor declares. */
    static AcquisitionPart part(CapabilityContract contract, CapabilityQueryReader.Binds binds) {
        Map<String, List<String>> arrays = new TreeMap<>();
        Map<String, LocalDate> dates = new TreeMap<>();
        for (CapabilityContract.Bind bind : contract.binds()) {
            switch (bind.type()) {
                case "TEXT_ARRAY" -> arrays.put(bind.name(), binds.arrayParams().get(bind.name()));
                case "DATE" -> dates.put(bind.name(), binds.dateParams().get(bind.name()));
                default -> {
                    // municipality and window come from the command itself
                }
            }
        }
        return new AcquisitionPart(
                contract.capability(),
                contract.adapterVersion(),
                contract.queryChecksum(),
                contract.recordKind(),
                binds.periodStart(),
                binds.periodEndExclusive(),
                new TreeMap<>(arrays),
                new TreeMap<>(dates));
    }

    /**
     * Reads the extract the execution plane published: one {@link PartRows} per part, in part order.
     *
     * @throws IllegalStateException for a line of an unknown part or of another record kind than the
     *     part's
     */
    static List<PartRows> readExtract(
            Path extract,
            List<AcquisitionPart> parts,
            List<CapabilityContract> contracts,
            String municipality,
            boolean digest)
            throws IOException {
        List<List<String>> keys = new ArrayList<>();
        long[][] counts = new long[parts.size()][3];
        for (int index = 0; index < parts.size(); index++) {
            keys.add(new ArrayList<>());
        }
        try (BufferedReader lines = new BufferedReader(
                new InputStreamReader(new GZIPInputStream(Files.newInputStream(extract)), StandardCharsets.UTF_8))) {
            String line = lines.readLine();
            while (line != null) {
                JsonNode entry = MAPPER.readTree(line);
                int index = entry.path("part").asInt(-1);
                if (index < 0 || index >= parts.size()) {
                    throw new IllegalStateException("the extract has a line of an unknown part");
                }
                if (!parts.get(index).recordKind().equals(entry.path("kind").asString())) {
                    throw new IllegalStateException("part " + index + " has a line of another record kind");
                }
                Map<String, Object> record = plain(entry.get("record"));
                check(contracts.get(index), parts.get(index), municipality, record, counts[index]);
                String rendered = render(record);
                keys.get(index).add(digest ? digest(rendered) : rendered);
                line = lines.readLine();
            }
        }
        List<PartRows> rows = new ArrayList<>();
        for (int index = 0; index < parts.size(); index++) {
            rows.add(new PartRows(keys.get(index), counts[index][0], counts[index][1], counts[index][2]));
        }
        return rows;
    }

    /** The rows a JDBC read returned, rendered like the extract's. */
    static List<String> jdbcKeys(CapabilityQueryReader.Result result, boolean digest) {
        List<String> keys = new ArrayList<>();
        for (Map<String, Object> row : result.rows()) {
            String rendered = render(row);
            keys.add(digest ? digest(rendered) : rendered);
        }
        return keys;
    }

    /** Order-insensitive comparison of two sides' keys. */
    static Comparison compare(String capability, List<String> rust, List<String> jdbc) {
        List<String> left = new ArrayList<>(rust);
        List<String> right = new ArrayList<>(jdbc);
        Collections.sort(left);
        Collections.sort(right);
        long rustOnly = 0;
        long jdbcOnly = 0;
        int l = 0;
        int r = 0;
        while (l < left.size() && r < right.size()) {
            int order = left.get(l).compareTo(right.get(r));
            if (order == 0) {
                l++;
                r++;
            } else if (order < 0) {
                rustOnly++;
                l++;
            } else {
                jdbcOnly++;
                r++;
            }
        }
        rustOnly += left.size() - l;
        jdbcOnly += right.size() - r;
        return new Comparison(capability, left.size(), right.size(), rustOnly, jdbcOnly, hashOf(left), hashOf(right));
    }

    /** The checks the execution plane applies to each record, by count of failing rows. */
    private static void check(
            CapabilityContract contract,
            AcquisitionPart part,
            String municipality,
            Map<String, Object> record,
            long[] counts) {
        if (!municipality.equals(record.get(contract.municipalityColumn()))) {
            counts[0]++;
        }
        if (contract.scopeDateColumn() != null && record.get(contract.scopeDateColumn()) instanceof String date) {
            LocalDate scope = LocalDate.parse(date);
            if (scope.isBefore(part.periodStart()) || !scope.isBefore(part.periodEndExclusive())) {
                counts[1]++;
            }
        }
        for (CapabilityContract.Column column : contract.columns()) {
            if (column.required() && record.get(column.name()) == null) {
                counts[2]++;
            }
        }
    }

    /** A record's fields as plain values: strings, booleans, lists of strings and null. */
    private static Map<String, Object> plain(JsonNode record) {
        Map<String, Object> fields = new TreeMap<>();
        for (String name : record.propertyNames()) {
            JsonNode value = record.get(name);
            if (value.isNull()) {
                fields.put(name, null);
            } else if (value.isBoolean()) {
                fields.put(name, value.booleanValue());
            } else if (value.isArray()) {
                List<String> elements = new ArrayList<>();
                value.forEach(element -> elements.add(element.isNull() ? null : element.asString()));
                fields.put(name, elements);
            } else {
                fields.put(name, value.asString());
            }
        }
        return fields;
    }

    private static String render(Map<String, Object> fields) {
        return MAPPER.writeValueAsString(new TreeMap<>(fields));
    }

    private static String hashOf(List<String> sortedKeys) {
        return digest(String.join("\n", sortedKeys));
    }

    private static String digest(String text) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
