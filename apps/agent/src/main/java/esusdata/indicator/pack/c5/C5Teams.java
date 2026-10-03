package esusdata.indicator.pack.c5;

import esusdata.indicator.model.CanonicalTeam;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The CNES team types the source shows for each INE (item 24 b, p. 2): «equipes de Saúde da Família
 * (eSF), e equipes de Atenção Primária (eAP), tipo 70 e 76, respectivamente». Most sources have no
 * team type (lacuna L1); an INE without a known type is neither validated nor excepted.
 */
final class C5Teams {

    /** eSF (item 24 b, p. 2). */
    static final String ESF_70 = "70";

    /** eAP, the team type exempted from practice D (item 24 b, p. 2; AMB-C5-01). */
    static final String EAP_76 = "76";

    private final Map<String, Set<String>> typesByIne = new HashMap<>();

    private C5Teams(List<CanonicalTeam> teams) {
        for (CanonicalTeam team : teams) {
            String type = team.teamTypeCode() == null ? "" : team.teamTypeCode().strip();
            if (team.ine() != null && !type.isEmpty()) {
                typesByIne.computeIfAbsent(team.ine(), k -> new HashSet<>()).add(type);
            }
        }
    }

    static C5Teams of(List<CanonicalTeam> teams) {
        return new C5Teams(teams);
    }

    /** The INE is shown as an eAP tipo 76. */
    boolean isEap76(String ine) {
        return typesOf(ine).contains(EAP_76);
    }

    /** The INE has a known type and it is neither eSF 70 nor eAP 76: its people do not enter. */
    boolean isIneligible(String ine) {
        Set<String> types = typesOf(ine);
        return !types.isEmpty() && !types.contains(ESF_70) && !types.contains(EAP_76);
    }

    private Set<String> typesOf(String ine) {
        return ine == null ? Set.of() : typesByIne.getOrDefault(ine, Set.of());
    }
}
