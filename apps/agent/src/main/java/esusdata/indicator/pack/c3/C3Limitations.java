package esusdata.indicator.pack.c3;

import java.util.List;

/**
 * The standing limitations of C3 (descriptor): what the local PEC cannot see and what the ficha
 * leaves open. Each cites its source in the transcription ({@code docs/metodologia/
 * c3-gestacao-puerperio.md}) or a gap of the DW (L1–L8).
 */
final class C3Limitations {

    static final List<String> STANDING = List.of(
            "Registros fora do PEC local não são vistos: a ficha conta registros de qualquer estabelecimento"
                    + " da APS \"no país\" (4.4, p.5).",
            "Doses registradas só no RIA/RNDS não são vistas, salvo transcrição no PEC local (Quadro 06, p.7;"
                    + " lacuna L4).",
            "O óbito do CadSUS não está no PEC local; só exclui o óbito registrado localmente (item 15, p.2;"
                    + " item 33, p.4).",
            "O vínculo nacional segue a NT nº 30/2025 e é apurado pelo SIAPS; aqui é aproximado pelo cadastro"
                    + " individual local vigente no corte (item 14, p.1; lacuna L8).",
            "O tipo de equipe não está no DW: sem tipo comprovado, a pontuação integral de E e J para eAP"
                    + " tipo 76 não é aplicada (24 b, p.2; lacuna L1; AMB-C3-13).",
            "A data de desfecho da gestação não está no DW: usa-se a data de resolução da condição de"
                    + " gravidez W78 na LPC (guia T3; manual do PEC) e, sem ela, DUM+294 (item 17, p.2; lacuna L2;"
                    + " MET-21).",
            "A pressão arterial da visita domiciliar não é lida (Quadro 03, p.6; lacuna L6).",
            "As \"Práticas em Saúde\" da ficha seguem a numeração da ficha CDS e são lidas como LEDI: 01"
                    + " antropometria = 20, 02 flúor = 2, 04 escovação = 9 (leitura declarada); MIAC com só"
                    + " uma das condições (atividade 05/06, prática) fica RULE_AMBIGUITY (24 e, p.3; Quadros 04"
                    + " e 08; AMB-C3-19).",
            "\"3224 Técnico em Saúde Bucal\" é lido como as ocupações 3224-05 e 3224-25; outra ocupação"
                    + " da família 3224 fica RULE_AMBIGUITY em C e K (Quadros 03 e 08; AMB-C3-20).",
            "Equipe sem tipo 70 ou 76 comprovado no corte exclui o episódio; cadastro sem INE não é"
                    + " vínculo (24 b, p.2; item 14, p.1).",
            "O mês entra na consolidação quando um episódio atinge D+42, lido como o 42º dia de puerpério;"
                    + " a leitura D+41 mudaria o mês em alguns casos (AMB-C3-04 (ii)).",
            "Em K, procedimentos do MIP não são avaliados: a ficha não lista SIGTAP para eles (Quadro 08," + " p.8).",
            "Os códigos rápidos ABP de pré-natal e de puerpério não são enumerados pela ficha e não são"
                    + " usados (24 f, p.3; AMB-C3-10).",
            "Os itens CIAP-2 \"48\" e \"49\" da lista de puerpério não são mapeados (24 f, p.3; AMB-C3-09).",
            "Os trimestres não são definidos pela ficha: G e H com evidência na gestação ficam"
                    + " RULE_AMBIGUITY (item 16, p.2; AMB-C3-02).",
            "Denominador mensal: gestantes e puérperas ativas na competência, cada episódio uma vez (4.1,"
                    + " p.5; AMB-C3-06).",
            "Semana ordinal (12ª e 20ª) e fronteiras de 294 e 42 dias ficam RULE_AMBIGUITY nas faixas em"
                    + " que as leituras divergem (AMB-C3-01; AMB-C3-04).",
            "O corte local de extração não reproduz o 20º dia útil do SIAPS (item 11, p.1; AMB-C3-21).",
            "Códigos do 24 f sem DUM nem IG na janela de 13 meses podem ser de uma gestação cuja DUM foi"
                    + " registrada antes da janela; ficam RULE_AMBIGUITY, possivelmente sem ambiguidade real"
                    + " (24 f, p.3; AMB-C3-03).",
            "A IG é registrada em semanas inteiras: uma DUM derivada da IG até 6 dias depois de uma DUM"
                    + " registrada é tratada como a mesma DUM (4.1, p.5; AMB-C3-03).",
            "A alocação do profissional em equipe tipo 70 ou 76 não é verificada (Quadros 02 e 08;"
                    + " lacuna L1; AMB-C3-13).",
            "São lidas pessoas nascidas nos últimos 130 anos: a ficha não tem faixa etária.");

    private C3Limitations() {}
}
