package esusdata.run.extract;

import esusdata.indicator.model.RecordKind;
import esusdata.indicator.model.SourceRef;
import esusdata.source.pec.CapabilityContract;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;

/**
 * Turns one record of a canonical v2 extract line into its canonical record (ADR 0030). "A SQL é o
 * esquema": every column of the capability's descriptor is the snake_case name of a component of
 * the record kind's type, so a column added to a descriptor reaches the record with no code here,
 * and a column with no component — or a component type that cannot hold the column type — fails
 * when the mapper is built, never by silently dropping data.
 *
 * <p>Values arrive as the execution plane writes them: text, dates ({@code yyyy-MM-dd}), integers
 * and canonical decimals as JSON strings, booleans as JSON booleans, {@code text[]} as arrays of
 * strings and absent values as {@code null}. Every value is checked against its column type; a
 * required column must be present and not blank; an undeclared property is refused. The record's
 * {@link SourceRef} is namespaced by the manifest's source id (§1.4.3) — the line never carries
 * it. The canonical constructor is the one used: a record keeps its secondary constructors for an
 * amended contract, and none of them is ever chosen here.
 */
final class CanonicalRecordMapper {

    private static final Pattern ISO_DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
    private static final Pattern INTEGER = Pattern.compile("-?\\d+");
    private static final Pattern DECIMAL = Pattern.compile("-?\\d+(?:\\.\\d+)?");
    private static final String BOOL = "bool";
    private static final String TEXT_ARRAY = "text[]";

    private final CapabilityContract contract;
    private final RecordKind kind;
    private final Constructor<? extends Record> constructor;
    private final List<Slot> slots;
    private final Map<String, CapabilityContract.Column> columns;

    private CanonicalRecordMapper(
            CapabilityContract contract,
            RecordKind kind,
            Constructor<? extends Record> constructor,
            List<Slot> slots,
            Map<String, CapabilityContract.Column> columns) {
        this.contract = contract;
        this.kind = kind;
        this.constructor = constructor;
        this.slots = List.copyOf(slots);
        this.columns = Collections.unmodifiableMap(columns);
    }

    /**
     * Builds the mapper of one capability.
     *
     * @throws IllegalStateException when the descriptor and the record type disagree
     */
    static CanonicalRecordMapper forContract(CapabilityContract contract) {
        RecordKind kind = RecordKind.fromWireName(contract.recordKind());
        Class<? extends Record> type = kind.recordType();
        Map<String, CapabilityContract.Column> columns = new LinkedHashMap<>();
        for (CapabilityContract.Column column : contract.columns()) {
            columns.put(column.name(), column);
        }
        RecordComponent[] components = type.getRecordComponents();
        Class<?>[] parameterTypes = new Class<?>[components.length];
        List<Slot> slots = new ArrayList<>(components.length);
        Set<String> consumed = new HashSet<>();
        for (int i = 0; i < components.length; i++) {
            parameterTypes[i] = components[i].getType();
            Slot slot = slot(contract, components[i], columns);
            slots.add(slot);
            consumed.addAll(slot.columnsUsed());
        }
        for (String column : columns.keySet()) {
            if (!consumed.contains(column)) {
                throw new IllegalStateException("column " + column + " of capability " + contract.capability()
                        + " has no component in " + type.getSimpleName());
            }
        }
        try {
            return new CanonicalRecordMapper(
                    contract, kind, type.getDeclaredConstructor(parameterTypes), slots, columns);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("no canonical constructor on " + type.getSimpleName(), e);
        }
    }

    RecordKind kind() {
        return kind;
    }

    /**
     * Validates one record read for the window {@code [windowStart, windowEnd)} and builds it.
     *
     * @throws IllegalStateException for an undeclared, missing, blank, mistyped or out-of-scope
     *     value
     */
    Record map(JsonNode record, String sourceId, String municipalityIbge, LocalDate windowStart, LocalDate windowEnd) {
        for (String name : record.propertyNames()) {
            if (!columns.containsKey(name)) {
                throw invalid("an undeclared column " + name);
            }
        }
        for (CapabilityContract.Column column : columns.values()) {
            checkValue(column, record.get(column.name()));
        }
        String municipality = requiredText(record, contract.municipalityColumn());
        if (!municipalityIbge.equals(municipality)) {
            throw invalid("municipality " + municipality + " outside the manifest municipality " + municipalityIbge);
        }
        checkScopeDate(record, windowStart, windowEnd);
        Object[] arguments = new Object[slots.size()];
        for (int i = 0; i < arguments.length; i++) {
            arguments[i] = slots.get(i).value(this, record, sourceId);
        }
        try {
            return constructor.newInstance(arguments);
        } catch (InvocationTargetException e) {
            throw invalid(
                    "a value its record refuses: " + e.getTargetException().getMessage(), e);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot build " + kind.wireName() + " records", e);
        }
    }

    private void checkScopeDate(JsonNode record, LocalDate windowStart, LocalDate windowEnd) {
        String scopeColumn = contract.scopeDateColumn();
        if (scopeColumn == null) {
            return;
        }
        LocalDate date = LocalDate.parse(requiredText(record, scopeColumn));
        if (date.isBefore(windowStart) || !date.isBefore(windowEnd)) {
            throw invalid(
                    scopeColumn + " " + date + " outside the part window [" + windowStart + ", " + windowEnd + ")");
        }
    }

    private String requiredText(JsonNode record, String column) {
        JsonNode value = record.get(column);
        if (value == null || !value.isString() || value.stringValue().isBlank()) {
            throw invalid("no value for " + column);
        }
        return value.stringValue();
    }

    private void checkValue(CapabilityContract.Column column, JsonNode value) {
        if (value == null || value.isNull()) {
            if (column.required()) {
                throw invalid("no value for the required column " + column.name());
            }
            return;
        }
        if (!matchesType(column.type(), value)) {
            throw invalid("a " + column.type() + " column " + column.name() + " holding " + value);
        }
        if (column.required() && value.isString() && value.stringValue().isBlank()) {
            throw invalid("a blank required column " + column.name());
        }
    }

    private boolean matchesType(String type, JsonNode value) {
        return switch (type) {
            case BOOL -> value.isBoolean();
            case TEXT_ARRAY -> isStringArray(value);
            case "date" -> isDate(value);
            case "integer" ->
                value.isString() && INTEGER.matcher(value.stringValue()).matches();
            case "decimal" ->
                value.isString() && DECIMAL.matcher(value.stringValue()).matches();
            case "text" -> value.isString();
            default ->
                throw new IllegalStateException(
                        "capability " + contract.capability() + " declares an unknown column type " + type);
        };
    }

    private static boolean isStringArray(JsonNode value) {
        if (!value.isArray()) {
            return false;
        }
        for (JsonNode element : value.values()) {
            if (!element.isString()) {
                return false;
            }
        }
        return true;
    }

    private static boolean isDate(JsonNode value) {
        if (!value.isString() || !ISO_DATE.matcher(value.stringValue()).matches()) {
            return false;
        }
        try {
            LocalDate.parse(value.stringValue());
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    private IllegalStateException invalid(String what) {
        return new IllegalStateException(
                "Extract record of capability " + contract.capability() + " has " + what + " (ENG-20)");
    }

    private IllegalStateException invalid(String what, Throwable cause) {
        IllegalStateException failure = invalid(what);
        failure.initCause(cause);
        return failure;
    }

    private static Slot slot(
            CapabilityContract contract, RecordComponent component, Map<String, CapabilityContract.Column> columns) {
        if (component.getType() == SourceRef.class) {
            return new Slot(SlotKind.SOURCE_REF, null, contract.entityTypeColumn(), contract.recordIdColumn());
        }
        String columnName = snakeCase(component.getName());
        SlotKind slotKind = slotKind(component);
        CapabilityContract.Column column = columns.get(columnName);
        if (column == null) {
            return new Slot(slotKind, null, null, null);
        }
        boolean compatible = switch (slotKind) {
            case BOOLEAN -> BOOL.equals(column.type());
            case STRING_LIST -> TEXT_ARRAY.equals(column.type());
            case STRING -> !BOOL.equals(column.type()) && !TEXT_ARRAY.equals(column.type());
            case SOURCE_REF -> false;
        };
        if (!compatible) {
            throw new IllegalStateException("column " + columnName + " (" + column.type() + ") of capability "
                    + contract.capability() + " cannot fill " + component.getName() + " of type "
                    + component.getGenericType());
        }
        return new Slot(slotKind, columnName, null, null);
    }

    private static SlotKind slotKind(RecordComponent component) {
        Class<?> type = component.getType();
        if (type == String.class) {
            return SlotKind.STRING;
        }
        if (type == Boolean.class) {
            return SlotKind.BOOLEAN;
        }
        if (type == List.class && isListOfString(component.getGenericType())) {
            return SlotKind.STRING_LIST;
        }
        throw new IllegalStateException("record component " + component.getName() + " has a type no column maps to: "
                + component.getGenericType());
    }

    private static boolean isListOfString(Type type) {
        return type instanceof ParameterizedType parameterized
                && parameterized.getActualTypeArguments().length == 1
                && parameterized.getActualTypeArguments()[0] == String.class;
    }

    /** {@code personKey} → {@code person_key}; {@code systolicMmhg} → {@code systolic_mmhg}. */
    static String snakeCase(String camelCase) {
        StringBuilder snake = new StringBuilder(camelCase.length() + 8);
        for (int i = 0; i < camelCase.length(); i++) {
            char c = camelCase.charAt(i);
            if (Character.isUpperCase(c)) {
                snake.append('_').append(Character.toLowerCase(c));
            } else {
                snake.append(c);
            }
        }
        return snake.toString();
    }

    private enum SlotKind {
        SOURCE_REF,
        STRING,
        BOOLEAN,
        STRING_LIST
    }

    /**
     * Where one constructor argument comes from: a column, the two source-reference columns, or
     * nothing (a record field the capability does not read stays {@code null}).
     */
    private record Slot(SlotKind kind, String column, String entityTypeColumn, String recordIdColumn) {

        List<String> columnsUsed() {
            if (kind == SlotKind.SOURCE_REF) {
                return List.of(entityTypeColumn, recordIdColumn);
            }
            return column == null ? List.of() : List.of(column);
        }

        Object value(CanonicalRecordMapper mapper, JsonNode record, String sourceId) {
            if (kind == SlotKind.SOURCE_REF) {
                return new SourceRef(
                        sourceId,
                        mapper.requiredText(record, entityTypeColumn),
                        mapper.requiredText(record, recordIdColumn));
            }
            JsonNode value = column == null ? null : record.get(column);
            if (value == null || value.isNull()) {
                return null;
            }
            return switch (kind) {
                case BOOLEAN -> value.booleanValue();
                case STRING_LIST ->
                    value.values().stream().map(JsonNode::stringValue).toList();
                case STRING, SOURCE_REF -> value.stringValue();
            };
        }
    }
}
