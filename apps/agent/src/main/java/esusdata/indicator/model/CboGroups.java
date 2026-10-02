package esusdata.indicator.model;

import java.util.List;
import java.util.Locale;

/**
 * CBO matching as the fichas write it: a list of four-character families ({@code 2235}, {@code 2251})
 * and six-character occupations ({@code 5151-05}). A CBO matches when it starts with a family or
 * equals an occupation. Hyphens, dots and spaces are ignored on both sides; letters are kept, since
 * the source has codes such as {@code 2235C3} (compare as text, never as a number).
 */
public record CboGroups(List<String> codes) {

    public CboGroups {
        codes = codes.stream().map(CboGroups::normalized).toList();
        for (String code : codes) {
            if (code.length() != 4 && code.length() != 6) {
                throw new IllegalArgumentException("a CBO group is 4 or 6 characters: " + code);
            }
        }
    }

    public static CboGroups of(String... codes) {
        return new CboGroups(List.of(codes));
    }

    public boolean matches(String cbo) {
        if (cbo == null) {
            return false;
        }
        String occupation = normalized(cbo);
        for (String code : codes) {
            if (occupation.startsWith(code)) {
                return true;
            }
        }
        return false;
    }

    private static String normalized(String text) {
        return text.replaceAll("[-.\\s]", "").toUpperCase(Locale.ROOT);
    }
}
