# C3 — solicitações fora da posse do pacote

Pedidos da sessão do pacote C3 (`c3-gestacao-puerperio`) à orquestradora (ADR 0030, "como-adicionar").
Cada um diz o quê, por quê, o trecho da ficha e o impacto; até ser atendido, o pacote segue com a
alternativa local declarada como limitação no descritor.

## S-C3-01 — Decisão "ambígua" na evidência por prática

- **O quê:** um valor `PRACTICE_AMBIGUOUS` em `EvidenceDecision` (modelo compartilhado, migração do
  `CHECK` da V10 se houver).
- **Por quê:** a transcrição manda `RULE_AMBIGUITY` por prática em vários cenários (AMB-C3-01, 02, 04,
  11, 12, 14–18). Hoje o enum só tem `PRACTICE_MET`, `PRACTICE_NOT_MET` e `PRACTICE_EXEMPT`.
- **Alternativa local:** a linha sai como `PRACTICE_NOT_MET` com `points = null` (não 0) e
  `reasonCode = AMBIGUIDADE_AMB_C3_xx`. Quem lê a evidência precisa olhar o `reasonCode`.
- **Impacto:** telas e exportação podem contar uma prática ambígua como não cumprida se ignorarem o
  `reasonCode`.

## S-C3-02 — Data de desfecho da gestação (lacuna L2)

- **O quê:** uma capacidade que entregue `pregnancy_outcome` (o tipo de registro já existe no modelo).
- **Por quê:** item 17 (p.2) e 4.1 (p.5): "O encerramento de cada gestação no sistema irá considerar o
  registro da Data de desfecho da gestação ou na ausência do referido registro será considerado o total
  de 294 dias". O DW não documenta o campo (dicionário, L2); o manual do PEC (guia, T3) diz que a data
  vira a "data da resolução da condição" de gravidez na LPC.
- **Alternativa local:** sem a capacidade, todo episódio usa a data substitutiva DUM+294 e a evidência
  diz qual data foi usada (`ELEGIVEL_DATA_SUBSTITUTIVA_294D`). A regra já lê `pregnancyOutcomes()` se
  vierem no extrato.
- **Impacto:** gestações encerradas antes de 294 dias (parto a termo, aborto) ficam com janelas de
  gestação e puerpério deslocadas em relação ao Siaps (I, J, B, C, D, K, F).

## S-C3-03 — Tipo de equipe (lacuna L1)

- **O quê:** registros `team` com `team_type_code` (70/76), de inventário ou CNES externo.
- **Por quê:** 24 b (p.2): "As boas práticas (E) e (J) consideram a pontuação integral para eAP, tipo 76."
- **Alternativa local:** sem tipo comprovado, E e J são avaliadas pela evidência (sem presumir eAP). A
  regra já aplica a exceção se o extrato trouxer `CanonicalTeam` tipo 76 para o INE do vínculo.
- **Impacto:** equipes eAP podem sair abaixo do Siaps em até 18 pontos por episódio.

## S-C3-04 — Lista de problemas/condições (`condition_list`) no C3

- **O quê:** acrescentar `condition_list` às capacidades do C3, com `ciap_codes`/`cid_codes` = listas do
  24 f e 24 g.
- **Por quê:** 24 f/g (p.3) falam em "CID-10 e/ou CIAP-2 **ativos**" (situação na LPC); hoje os códigos
  vêm só do que cada atendimento avaliou (`care_encounter`). A data de resolução da gravidez na LPC é
  também a candidata (a) da L2.
- **Alternativa local:** o código avaliado num MIAI dentro da janela do episódio vale como ativo naquela
  data (AMB-C3-07 (ii) declarada).
- **Impacto:** exclusões por aborto registradas só na LPC, sem atendimento, não são vistas.

## S-C3-05 — PA da visita domiciliar e códigos da atividade coletiva

- **O quê:** `blood_pressure` em `CanonicalHomeVisit` (lacuna L6) e os códigos de Atividade/Práticas em
  Saúde em `CanonicalMeasurement` de origem MIAC.
- **Por quê:** Quadro 03 (p.6) aceita o MIVDT "registros de pressão arterial no campo específico";
  Quadros 04 e 08 (p.7–8) condicionam o MIAC a "Atividade código 05 e 06 e Práticas em Saúde
  código(s) …".
- **Alternativa local:** PA de visita não é lida; registro MIAC conta como "talvez" (C, D) e o
  resultado vira `RULE_AMBIGUITY` quando o limiar dependeria dele. MIAC de K não é avaliado.
- **Impacto:** C e D podem ficar abaixo do Siaps; K perde as atividades coletivas.

## S-C3-06 — Procedimentos de saúde bucal pelo MIP (K)

- **O quê:** um modo de `procedure_performed` por CBO (sem lista SIGTAP), ou a lista oficial dos
  procedimentos de saúde bucal, se a ficha vier a publicá-la.
- **Por quê:** Quadro 08 (p.8) aceita o MIP "realizados por profissionais de saúde dos CBO supracitados"
  sem lista SIGTAP; a capacidade só devolve os códigos pedidos.
- **Alternativa local:** K é comprovada só pelo atendimento odontológico (MIAOI); limitação declarada.
- **Impacto:** K pode ficar abaixo do Siaps quando a única atividade foi um procedimento avulso.

## Respostas da integração (emenda da fundação 9526ac6)

| Pedido | Resposta | Como o pacote ficou |
|---|---|---|
| S-C3-01 | Atendido: `EvidenceDecision.PRACTICE_AMBIGUOUS` | Prática ambígua sai `PRACTICE_AMBIGUOUS`, sem pontos, `reasonCode` com a AMB |
| S-C3-02 | Sem capacidade de desfecho; usar a resolução da condição de gravidez na LPC como candidata declarada | Prioridade: desfecho registrado (`pregnancy_outcome`, se vier) > resolução na LPC (`ELEGIVEL_DESFECHO_RESOLUCAO_LPC`) > DUM+294; limitação L2 mantida |
| S-C3-03 | Sem fonte (L1) | Limitação mantida; exceção eAP só com `team` tipo 76 no extrato |
| S-C3-04 | Aprovado | `condition_list` entra no descritor e nas `requirements` com as listas do 24 f/g |
| S-C3-05 | (a) PA da visita: não (L6); (b) MIAC com tipo de atividade e práticas em saúde | PA de visita segue limitação; MIAC avaliado em C, D e K (AMB-C3-19 quando só uma condição casa) |
| S-C3-06 | Sem modo "por CBO" | Limitação mantida (K só pelo MIAOI e MIAC) |

## S-C3-07 — Códigos de "Práticas em Saúde" da ficha × LEDI

- **O quê:** a correspondência documentada entre os códigos que a ficha usa para "Práticas em Saúde"
  e os códigos LEDI `PraticasEmSaude` que `measurement_record` devolve em `health_practice_codes`.
- **Por quê:** 24 e (p.3): "Práticas em Saúde códigos 01, 02, 04"; Quadro 04 (p.7): "código 01";
  Quadro 08 (p.8): "códigos 02 e 04". O LEDI (`docs/discovery/capacidades-dw-v2.md` §3.7) não tem os
  códigos 1 nem 4, e o 2 é "aplicação tópica de flúor": a numeração da ficha não é a do LEDI.
- **Resposta da integração:** a correspondência é por nome da opção da Ficha de Atividade Coletiva do
  CDS (01 antropometria, 02 aplicação tópica de flúor, 04 escovação dental supervisionada), coerente com
  os quadros (D cita só 01; K cita 02 e 04): LEDI 20, 2 e 9. Leitura declarada (AMB-C3-19), a confirmar
  no Portão C.
- **Como o pacote ficou:** C usa {20, 2, 9}, D usa {20}, K usa {2, 9}; atividade 05/06 com a prática
  conta; só uma das duas condições fica AMB-C3-19.
