package esusdata.indicator.pack.c3;

import esusdata.indicator.model.Limitation;
import java.util.List;

/**
 * The standing limitations of C3 (descriptor): what the local PEC cannot see and the readings of
 * the ficha that were decided. Each text is the final disclosure text of {@code docs/indicadores/
 * decisoes/c3-gestacao-puerperio.md}, prefixed with its stable code ({@code C3-LIM-nn}); the
 * class of each (BLOCKING_GAP, DECLARED_CONVENTION, OUT_OF_REACH) is in that record. C3-LIM-05 is
 * the only blocking gap.
 */
final class C3Limitations {

    static final List<Limitation> STANDING = List.of(
            Limitation.outOfReach(
                    "C3-LIM-01",
                    "Registros fora do PEC local não são vistos: a ficha conta registros de qualquer"
                            + " estabelecimento da APS no país (4.4, p.5)."),
            Limitation.outOfReach(
                    "C3-LIM-02",
                    "Doses registradas só no RIA/RNDS não são vistas, salvo transcrição no PEC local"
                            + " (Quadro 06, p.7; lacuna L4)."),
            Limitation.outOfReach(
                    "C3-LIM-03",
                    "O óbito do CadSUS não está no PEC local; só exclui o óbito registrado localmente"
                            + " (item 15, p.2; item 33, p.4)."),
            Limitation.outOfReach(
                    "C3-LIM-04",
                    "O vínculo nacional segue a NT nº 30/2025 e é apurado pelo SIAPS; aqui é aproximado"
                            + " pelo cadastro individual local vigente no corte (item 14, p.1; lacuna L8)."),
            Limitation.convention(
                    "C3-LIM-05",
                    "E e J são creditadas integralmente (9 pontos cada) ao episódio de equipe eAP 76 que não as"
                            + " cumpriu (24 b, p.2; C3-D1); a visita observada continua como evidência. O tipo é o"
                            + " vigente no último dia da competência."),
            Limitation.convention(
                    "C3-LIM-06",
                    "A data de desfecho da gestação não está no DW (lacuna L2): usa-se a resolução do"
                            + " problema W78 na LPC e, sem ela, DUM+294 (item 17, p.2). Desfecho depois de DUM+294 é"
                            + " ignorado. Código de parto, puerpério ou aborto não define a data. A contagem de episódios"
                            + " por origem sai dos códigos de motivo das evidências (ELEGIVEL_*). Sem fechamento do W78, a consulta puerperal"
                            + " anterior a DUM+294 conta como gestação."),
            Limitation.outOfReach(
                    "C3-LIM-07",
                    "A pressão arterial da visita domiciliar não está registrada no DW desta instalação"
                            + " (PEC 5.5.28; Quadro 03, p.6; lacuna L6): C pode ficar abaixo do SIAPS."),
            Limitation.convention(
                    "C3-LIM-08",
                    "As \"Práticas em Saúde\" da ficha seguem a numeração da ficha CDS e são lidas como"
                            + " LEDI: 01 antropometria = 20, 02 flúor = 2, 04 escovação = 9. O MIAC conta só com"
                            + " atividade 05/06 e a prática; com só uma das duas condições, não conta (24 e, p.3;"
                            + " Quadros 04 e 08; AMB-C3-19)."),
            Limitation.convention(
                    "C3-LIM-09",
                    "\"3224 Técnico em Saúde Bucal\" é lido como as ocupações 3224-05 e 3224-25; as demais"
                            + " ocupações da família 3224 não contam em C e K (Quadros 03 e 08; AMB-C3-20)."),
            Limitation.convention(
                    "C3-LIM-10",
                    "Equipe sem tipo, com dois tipos no último dia da competência ou com tipo diferente de 70 e"
                            + " 76 exclui o episódio, com a contagem por motivo (24 b, p.2; C3-D2); cadastro sem INE"
                            + " não é vínculo (item 14, p.1)."),
            Limitation.convention(
                    "C3-LIM-11",
                    "A gestação inclui o dia D e o puerpério vai de D+1 a D+42, com D+42 inclusive. O mês"
                            + " entra na consolidação quando um episódio elegível atinge D+42 (item 5 e item 17; NT"
                            + " 8/2026). A leitura D+41 mudaria o mês em alguns casos (AMB-C3-04)."),
            Limitation.convention(
                    "C3-LIM-12",
                    "Em K, procedimentos do MIP não são avaliados: a ficha não lista SIGTAP para eles"
                            + " (Quadro 08, p.8). K pode ficar abaixo do SIAPS."),
            Limitation.outOfReach(
                    "C3-LIM-13",
                    "Os códigos rápidos ABP de pré-natal e de puerpério não são enumerados pela ficha e"
                            + " não são usados (24 f, p.3; AMB-C3-10). Uma gestante identificada só por código ABP"
                            + " não entra."),
            Limitation.outOfReach(
                    "C3-LIM-14",
                    "Os itens CIAP-2 \"48\" e \"49\" da lista de puerpério não são mapeados (24 f, p.3;"
                            + " AMB-C3-09)."),
            Limitation.convention(
                    "C3-LIM-15",
                    "Trimestres: o 1º vai da DUM até a IG 13s6d (DUM+97) e o 3º, da IG 28s0d (DUM+196)"
                            + " até o desfecho. A ficha não define; a convenção segue o CAB 32 e o PCDT de"
                            + " transmissão vertical do MS (AMB-C3-02)."),
            Limitation.convention(
                    "C3-LIM-16",
                    "Denominador mensal: gestantes e puérperas ativas na competência, cada gestação"
                            + " (episódio) uma vez, inclusive as em curso (4.1, p.5; AMB-C3-06)."),
            Limitation.convention(
                    "C3-LIM-17",
                    "Semana gestacional por semanas completas: A até IG 12s6d (DUM+90) e F a partir da"
                            + " IG 20s0d (DUM+140), como no FAQ do Previne Brasil (AMB-C3-01)."),
            Limitation.outOfReach(
                    "C3-LIM-18",
                    "O corte local de extração é o último dia da competência e não reproduz o 20º dia"
                            + " útil do SIAPS (item 11, p.1; AMB-C3-21)."),
            Limitation.convention(
                    "C3-LIM-19",
                    "A gestação exige DUM ou IG e um código do 24 f na janela. Código sem DUM nem IG, e"
                            + " DUM ou IG sem código, não entram no denominador; as duas contagens saem dos"
                            + " códigos de motivo das evidências (EXCLUIDO_SEM_*) (24 f, p.3; 4.1, p.5; AMB-C3-03)."),
            Limitation.convention(
                    "C3-LIM-20",
                    "Vale a DUM do registro mais antigo da gestação; a IG é registrada em semanas"
                            + " inteiras e uma DUM derivada da IG pode errar até 6 dias. DUM fora de [data do"
                            + " atendimento − 294, data do atendimento] não é lida (4.1, p.5; AMB-C3-03)."),
            Limitation.convention(
                    "C3-LIM-21",
                    "A alocação do profissional em equipe tipo 70 ou 76 não é verificada; valem registros"
                            + " de qualquer profissional da APS (4.4; Quadros 02 e 08; AMB-C3-13)."),
            Limitation.convention(
                    "C3-LIM-22", "São lidas pessoas nascidas nos últimos 130 anos: a ficha não tem faixa etária."),
            Limitation.convention(
                    "C3-LIM-23",
                    "Consulta de A, B e da 1ª de E é o atendimento do MIAI por médica(o) ou enfermeira(o)"
                            + " com código da lista de gestação do 24 f; a de I exige código da lista de puerpério;"
                            + " consulta só no MIP não conta; várias consultas ou visitas no mesmo dia contam como"
                            + " uma (Quadro 02; AMB-C3-11, -12, -14, -16). O SIAPS pode contar qualquer consulta com"
                            + " CID/CIAP, o que daria valores maiores em A e B."),
            Limitation.convention(
                    "C3-LIM-24",
                    "Exame de G e H vale pela data do registro (realização ou avaliação); anti-HTLV não"
                            + " cobre nenhum agente (Quadro 07; AMB-C3-18)."),
            Limitation.convention(
                    "C3-LIM-25",
                    "A avaliação antropométrica 01.01.04.002-4 sem valores vale um par de peso e altura"
                            + " no dia (Quadro 04; AMB-C3-15). Pode superestimar D."),
            Limitation.convention(
                    "C3-LIM-26",
                    "A dose de dTpa vale de DUM+140 até D+42, pela data de aplicação (item 16, F;" + " AMB-C3-17)."),
            Limitation.convention(
                    "C3-LIM-27",
                    "Código de aborto (24 g) em registro ativo, latente ou resolvido dentro de [DUM, D]"
                            + " exclui o episódio desde a competência do registro; código fora da janela não exclui;"
                            + " CID-10 casa por categoria (item 15, 24 f e 24 g; AMB-C3-07, -08)."),
            Limitation.outOfReach(
                    "C3-LIM-28",
                    "Se o desfecho da gestação foi registrado em outro município ou serviço, o PEC local"
                            + " não o vê e usa a resolução do W78 ou DUM+294; janelas e puerpério podem divergir do"
                            + " SIAPS (item 17, p.2; 4.1, p.5)."),
            Limitation.outOfReach(
                    "C3-LIM-29",
                    "A \"Mudança de equipe\" com desempate da Portaria SAPS/MS nº 161/2024 não é aplicada;"
                            + " só valem as saídas do cadastro (óbito 135, mudança de território 136) e o INE do"
                            + " vínculo local (item 15, p.2)."),
            Limitation.outOfReach(
                    "C3-LIM-30",
                    "A validade de CPF/CNS e a unificação de cadastros no CadSUS são nacionais; cadastros"
                            + " que não se unificam com segurança não somam evidências (24 a, p.2)."),
            Limitation.outOfReach(
                    "C3-LIM-31",
                    "A validação das equipes pelas condições da Portaria GM/MS nº 3.493/2024 e pela"
                            + " última competência válida do SCNES é do MS; o PEC traz INE e tipo (24 b, p.2; item"
                            + " 11, p.1)."),
            Limitation.outOfReach(
                    "C3-LIM-32",
                    "As habilitações de CBO da tabela SIGTAP (24 h, p.3) não são verificadas; vale o CBO"
                            + " do registro, comparado com as listas da ficha."),
            Limitation.convention(
                    "C3-LIM-33",
                    "O mapeamento modelo de informação → dado do PEC não está na ficha (4.2, p.5); vale o"
                            + " das capacidades validadas (pec-adapters.json)."),
            Limitation.outOfReach(
                    "C3-LIM-34",
                    "O resultado depende da qualidade do registro pelos profissionais e do envio tardio"
                            + " pela gestão local (item 33, p.4)."));

    private C3Limitations() {}
}
