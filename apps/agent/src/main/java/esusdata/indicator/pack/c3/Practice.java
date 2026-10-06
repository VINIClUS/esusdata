package esusdata.indicator.pack.c3;

/** The eleven good practices of the Quadro 01 (p.5), in the descriptor's order. */
enum Practice {
    A,
    B,
    C,
    D,
    E,
    F,
    G,
    H,
    I,
    J,
    K;

    /** E and J: "consideram a pontuação integral para eAP, tipo 76" (24 b, p.2). */
    boolean creditedForEap76() {
        return this == E || this == J;
    }
}
