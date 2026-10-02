package esusdata.indicator.model;

/**
 * A person as the source knows them (§1.7 "Pessoa"; ADR 0030 kind {@code person}). {@code
 * personKey} is the source's opaque citizen key that links this person's records across facts —
 * never a name, CPF or CNS (§1.12.1). Dates are ISO {@code LocalDate} strings.
 *
 * @param sex {@code FEMININO}, {@code MASCULINO}, {@code INDETERMINADO} or {@code null} when the
 *     source does not say
 * @param genderIdentity the source's own gender-identity code, or {@code null}; C7 maps it with
 *     the combinations its ficha lists, never by inference (§2.4)
 * @param deathDate when the source records a death, else {@code null} (a missing date is not
 *     proof of life: the CadSUS death the fichas cite is not in a municipal PEC)
 */
public record CanonicalPerson(
        SourceRef sourceRef,
        String municipalityIbge,
        String personKey,
        String birthDate,
        String sex,
        String genderIdentity,
        String deathDate) {}
