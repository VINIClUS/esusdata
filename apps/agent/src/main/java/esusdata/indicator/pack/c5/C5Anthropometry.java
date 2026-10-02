package esusdata.indicator.pack.c5;

import esusdata.indicator.model.SourceRef;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.function.Predicate;

/**
 * Practice C's days: weight and height gathered per civil date from every accepted record of the
 * person (Quadro 04, p. 5, «Registros realizados no mesmo dia.»; AMB-C5-07). A date counts only
 * when it has both; the records that gave either on that date are its evidence, each once.
 */
final class C5Anthropometry {

    private final Predicate<C5Event> accepted;
    private final NavigableMap<LocalDate, DateRecords> days = new TreeMap<>();

    C5Anthropometry(Predicate<C5Event> accepted) {
        this.accepted = accepted;
    }

    /** «01.01.04.002-4» (avaliação antropométrica) or «01.01.04.008-3» (medição de peso). */
    static boolean givesWeight(List<String> sigtapCodes) {
        return sigtapCodes.contains(C5Codes.SIGTAP_AVALIACAO_ANTROPOMETRICA)
                || sigtapCodes.contains(C5Codes.SIGTAP_MEDICAO_PESO);
    }

    /** «01.01.04.002-4» (avaliação antropométrica) or «01.01.04.007-5» (medição de altura). */
    static boolean givesHeight(List<String> sigtapCodes) {
        return sigtapCodes.contains(C5Codes.SIGTAP_AVALIACAO_ANTROPOMETRICA)
                || sigtapCodes.contains(C5Codes.SIGTAP_MEDICAO_ALTURA);
    }

    /** Records what {@code event} gave on its date, when it gave anything and is accepted. */
    void add(C5Event event, boolean weight, boolean height) {
        if ((weight || height) && accepted.test(event)) {
            days.computeIfAbsent(event.date(), d -> new DateRecords()).add(event, weight, height);
        }
    }

    /** The records of the most recent date with weight and height, or empty when none has both. */
    List<C5Event> latestComplete() {
        for (DateRecords day : days.descendingMap().values()) {
            if (day.weight && day.height) {
                return List.copyOf(day.events.values());
            }
        }
        return List.of();
    }

    private static final class DateRecords {
        private boolean weight;
        private boolean height;
        private final Map<SourceRef, C5Event> events = new LinkedHashMap<>();

        void add(C5Event event, boolean gaveWeight, boolean gaveHeight) {
            weight |= gaveWeight;
            height |= gaveHeight;
            events.putIfAbsent(event.identity(), event);
        }
    }
}
