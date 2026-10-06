package esusdata.indicator.model;

import java.util.List;
import java.util.stream.Stream;

/**
 * The capability ids of the canonical v2 extract and the names of the code-list binds they take
 * (ADR 0030; descriptors in {@code contracts/compatibility/capabilities/}). Packs name capabilities
 * through these constants so a typo fails to compile instead of failing a run.
 */
@SuppressWarnings("PMD.DataClass") // a table of frozen identifiers, like MunicipalIsolationContract
public final class Capabilities {

    public static final String CITIZEN = "citizen";
    public static final String INDIVIDUAL_REGISTRATION = "individual_registration";
    public static final String CARE_ENCOUNTER = "care_encounter";
    public static final String DENTAL_ENCOUNTER = "dental_encounter";
    public static final String HOME_VISIT = "home_visit";
    public static final String IMMUNIZATION_HISTORY = "immunization_history";
    public static final String EXAM_REQUEST_EVALUATION = "exam_request_evaluation";
    public static final String PROCEDURE_PERFORMED = "procedure_performed";
    public static final String CONDITION_LIST = "condition_list";
    /** Weight, height and blood pressure written outside an encounter: MIP and MIAC (ADR 0030). */
    public static final String MEASUREMENT_RECORD = "measurement_record";

    /**
     * The team type and its validity, read from the transactional schema ({@code PEC_OLTP}, ADR
     * 0031). Not in {@link #ALL}: that list is the DW foundation, validated as one set.
     */
    public static final String TEAM = "team";

    /** {@code text[]} bind of SIGTAP codes, digits only. */
    public static final String PROCEDURE_CODES = "procedure_codes";

    /** {@code text[]} bind of immunobiological codes, as the fichas list them. */
    public static final String IMMUNOBIOLOGICAL_CODES = "immunobiological_codes";

    /** {@code text[]} bind of CIAP-2 codes. */
    public static final String CIAP_CODES = "ciap_codes";

    /** {@code text[]} bind of CID-10 codes. */
    public static final String CID_CODES = "cid_codes";

    /** Every canonical v2 capability of the foundation. */
    public static final List<String> ALL = List.of(
            CITIZEN,
            INDIVIDUAL_REGISTRATION,
            CARE_ENCOUNTER,
            DENTAL_ENCOUNTER,
            HOME_VISIT,
            IMMUNIZATION_HISTORY,
            EXAM_REQUEST_EVALUATION,
            PROCEDURE_PERFORMED,
            CONDITION_LIST,
            MEASUREMENT_RECORD);

    /** Every packaged canonical v2 capability: the foundation and {@link #TEAM}. */
    public static final List<String> PACKAGED =
            Stream.concat(ALL.stream(), Stream.of(TEAM)).toList();

    private Capabilities() {}
}
