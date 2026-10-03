package esusdata.indicator.pack.c7;

/**
 * The four subpopulations of the item 23 (pp. 2–3), each with its age range (inclusive, completed
 * years — AMB-C7-03) and its window in civil months (Quadro 01, p. 5). An age of 14 is in B and in
 * C ("09 e 14", "14 e 69"); one person enters every subgroup of their age.
 */
enum C7Subgroup {
    /** "entre 25 e 64 anos", 36 meses (60 só para 02.02.10.025-1). */
    A(25, 64, 36),
    /** "entre 09 e 14 anos"; sem janela na ficha — 60 meses da NT nº 8/2026 delimitam a AMB-C7-06. */
    B(9, 14, 60),
    /** "entre 14 e 69 anos", 12 meses. */
    C(14, 69, 12),
    /** "entre 50 e 69 anos", 24 meses. */
    D(50, 69, 24);

    /** "60 meses" of the HPV molecular exam (Quadro 02). */
    static final int HPV_MOLECULAR_MONTHS = 60;

    private final int minAge;
    private final int maxAge;
    private final int months;

    C7Subgroup(int minAge, int maxAge, int months) {
        this.minAge = minAge;
        this.maxAge = maxAge;
        this.months = months;
    }

    int minAge() {
        return minAge;
    }

    int maxAge() {
        return maxAge;
    }

    int months() {
        return months;
    }

    boolean includes(long age) {
        return age >= minAge && age <= maxAge;
    }
}
