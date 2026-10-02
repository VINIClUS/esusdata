package esusdata.indicator.pack.c7;

import esusdata.indicator.model.AgeAt;
import esusdata.indicator.model.AgeAt.AnniversaryRule;
import esusdata.indicator.model.CanonicalPerson;
import esusdata.indicator.model.CanonicalRegistration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Who enters C7 on the last day of the competência (itens 14, 15 e 4.1–4.2 da ficha, pp. 2 e 5):
 * age 9 to 69, the sex × gender-identity combinations the ficha enumerates, and the team link
 * resolved from the registration versions up to that day (§1.7.3) — never from the latest
 * encounter. Every person read gets a decision with a stable reason code (ENG-36).
 */
final class C7Cohort {

    /**
     * AMB-C7-03: the ficha does not say how an age completed on 29/02 is counted. Declared here:
     * Lei nº 810/1949, art. 3º — the anniversary falls on 01/03 in a non-leap year.
     */
    static final AnniversaryRule ANNIVERSARY = AnniversaryRule.NEXT_DAY;

    static final int MIN_AGE = 9;
    static final int MAX_AGE = 69;

    static final String ELEGIVEL_SEXO_FEMININO = "ELEGIVEL_SEXO_FEMININO";
    static final String ELEGIVEL_HOMEM_TRANSGENERO = "ELEGIVEL_HOMEM_TRANSGENERO";
    static final String EXCLUIDO_FORA_FAIXA_ETARIA = "EXCLUIDO_FORA_FAIXA_ETARIA";
    static final String EXCLUIDO_MULHER_TRANSGENERO = "EXCLUIDO_MULHER_TRANSGENERO";
    static final String EXCLUIDO_SEXO_NAO_ELEGIVEL = "EXCLUIDO_SEXO_NAO_ELEGIVEL";
    static final String EXCLUIDO_OBITO = "EXCLUIDO_OBITO";
    static final String EXCLUIDO_SEM_VINCULO = "EXCLUIDO_SEM_VINCULO";
    static final String EXCLUIDO_SAIDA_TERRITORIO = "EXCLUIDO_SAIDA_TERRITORIO";
    static final String EXCLUIDO_CADASTRO_INATIVO = "EXCLUIDO_CADASTRO_INATIVO";
    static final String EXCLUIDO_RECUSA_CADASTRO = "EXCLUIDO_RECUSA_CADASTRO";
    static final String EXCLUIDO_CADASTRO_SIMPLIFICADO = "EXCLUIDO_CADASTRO_SIMPLIFICADO";
    static final String EXCLUIDO_VINCULO_CONFLITANTE = "EXCLUIDO_VINCULO_CONFLITANTE";

    private C7Cohort() {}

    /**
     * One person as C7 sees them on {@code reference}.
     *
     * @param eligible in the denominator of at least the subgroups of their age
     * @param reason {@code ELEGIVEL_*} or {@code EXCLUIDO_*}
     * @param transMan sex MASCULINO with identity "Homem transgênero" (item 4.1.2)
     */
    record Member(
            String personKey,
            LocalDate birth,
            long age,
            boolean transMan,
            boolean eligible,
            String reason,
            String cnes,
            String ine) {}

    /** Decides every person once (the first record of a key wins), sorted by key. */
    static List<Member> resolve(
            List<CanonicalPerson> persons, List<CanonicalRegistration> registrations, LocalDate reference) {
        Map<String, List<CanonicalRegistration>> versions = new TreeMap<>();
        for (CanonicalRegistration r : registrations) {
            versions.computeIfAbsent(r.personKey(), k -> new ArrayList<>()).add(r);
        }
        Map<String, CanonicalPerson> unique = new TreeMap<>();
        for (CanonicalPerson p : persons) {
            unique.putIfAbsent(p.personKey(), p);
        }
        List<Member> members = new ArrayList<>(unique.size());
        for (CanonicalPerson p : unique.values()) {
            members.add(decide(p, versions.getOrDefault(p.personKey(), List.of()), reference));
        }
        return members;
    }

    private static Member decide(CanonicalPerson person, List<CanonicalRegistration> versions, LocalDate reference) {
        LocalDate birth = LocalDate.parse(person.birthDate());
        long age = birth.isAfter(reference) ? -1 : AgeAt.completedYears(birth, reference, ANNIVERSARY);
        boolean transMan = C7Codes.SEXO_MASCULINO.equals(person.sex())
                && C7Codes.IDENTIDADE_HOMEM_TRANSGENERO.equals(person.genderIdentity());
        Link link = link(versions, reference);
        String reason = exclusion(person, age, link, reference);
        boolean eligible = reason == null;
        if (eligible) {
            reason = transMan ? ELEGIVEL_HOMEM_TRANSGENERO : ELEGIVEL_SEXO_FEMININO;
        }
        return new Member(person.personKey(), birth, age, transMan, eligible, reason, link.cnes(), link.ine());
    }

    /** The first reason, in the ficha's order (entrada, then interrupção), or null when eligible. */
    private static String exclusion(CanonicalPerson person, long age, Link link, LocalDate reference) {
        if (age < MIN_AGE || age > MAX_AGE) {
            return EXCLUIDO_FORA_FAIXA_ETARIA;
        }
        String sex = sexReason(person);
        if (sex != null) {
            return sex;
        }
        if (person.deathDate() != null && !LocalDate.parse(person.deathDate()).isAfter(reference)) {
            return EXCLUIDO_OBITO;
        }
        return link.exclusion();
    }

    /**
     * Item 4.1 (p. 5): "Registro de sexo feminino; ou Registro de sexo masculino e identidade de
     * gênero “Homem transgênero”"; item 4.2: sexo feminino e "Mulher transgênero" fica fora. Any
     * other combination is outside the ficha's closed list (AMB-C7-12) — never inferred.
     */
    private static String sexReason(CanonicalPerson person) {
        if (C7Codes.SEXO_FEMININO.equals(person.sex())) {
            return C7Codes.IDENTIDADE_MULHER_TRANSGENERO.equals(person.genderIdentity())
                    ? EXCLUIDO_MULHER_TRANSGENERO
                    : null;
        }
        if (C7Codes.SEXO_MASCULINO.equals(person.sex())
                && C7Codes.IDENTIDADE_HOMEM_TRANSGENERO.equals(person.genderIdentity())) {
            return null;
        }
        return EXCLUIDO_SEXO_NAO_ELEGIVEL;
    }

    /** The registration in force on {@code reference}: the latest version up to that day. */
    private static Link link(List<CanonicalRegistration> versions, LocalDate reference) {
        LocalDate latest = null;
        List<CanonicalRegistration> current = new ArrayList<>();
        for (CanonicalRegistration r : versions) {
            LocalDate date = LocalDate.parse(r.registrationDate());
            if (date.isAfter(reference)) {
                continue;
            }
            if (latest == null || date.isAfter(latest)) {
                latest = date;
                current.clear();
            }
            if (date.equals(latest)) {
                current.add(r);
            }
        }
        if (current.isEmpty()) {
            return new Link(null, null, EXCLUIDO_SEM_VINCULO);
        }
        current.sort(Comparator.comparing(r -> r.sourceRef().recordId()));
        CanonicalRegistration chosen = current.get(0);
        for (CanonicalRegistration r : current) {
            if (!Objects.equals(r.ine(), chosen.ine())) {
                return new Link(null, null, EXCLUIDO_VINCULO_CONFLITANTE);
            }
        }
        return new Link(chosen.cnes(), blankToNull(chosen.ine()), versionExclusion(chosen));
    }

    /** Item 15 (p. 2) and the registration's own state; null when the version links the person. */
    private static String versionExclusion(CanonicalRegistration r) {
        if (C7Codes.SAIDA_MUDANCA_TERRITORIO.equals(r.exitReason())) {
            return EXCLUIDO_SAIDA_TERRITORIO;
        }
        if (C7Codes.SAIDA_OBITO.equals(r.exitReason())) {
            return EXCLUIDO_OBITO;
        }
        if (Boolean.TRUE.equals(r.refused())) {
            return EXCLUIDO_RECUSA_CADASTRO;
        }
        if (Boolean.TRUE.equals(r.inactive())) {
            return EXCLUIDO_CADASTRO_INATIVO;
        }
        if (Boolean.TRUE.equals(r.simplified())) {
            return EXCLUIDO_CADASTRO_SIMPLIFICADO;
        }
        return blankToNull(r.ine()) == null ? EXCLUIDO_SEM_VINCULO : null;
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text;
    }

    private record Link(String cnes, String ine, String exclusion) {}
}
