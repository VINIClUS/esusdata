# C5 — Solicitações fora da posse do pacote

Pedidos da sessão do pacote `c5-cuidado-hipertensao` (ADR 0030) para a integração. Cada um traz o
quê, o porquê, o trecho da ficha e o impacto; enquanto não for atendido, o pacote segue com a
alternativa local declarada como limitação permanente do descritor.

## SOL-C5-01 — CBO do profissional no registro de condição (`CanonicalCondition`)

**Atendida na emenda 9526ac6.** O pacote agora só identifica a pessoa por condição com `basis` `PROFESSIONAL` (ou nulo) **e** `cbo` dos grupos do Quadro 02 (médica(o)/enfermeira(o)); CBO nulo ou de outro profissional sai `EXCLUIDO_SEM_CONDICAO_AVALIADA`. A limitação correspondente saiu do descritor.

- **O quê:** acrescentar `cbo` (texto, opcional) à capacidade `condition_list` e ao registro
  `CanonicalCondition` — o CBO de quem avaliou o problema no atendimento (FAI: `co_dim_cbo` do
  atendimento dono de `tb_fat_atd_ind_problemas`).
- **Por quê:** a ficha identifica a pessoa com hipertensão por «atendimento individual com a
  condição avaliada de hipertensão, realizada por enfermeira(o) e/ou médica(o) da APS» (item 5,
  p. 1). Sem o CBO, o pacote não distingue a avaliação por médico/enfermeiro da feita por outro CBO.
- **Hoje:** aceita toda condição com `basis = PROFESSIONAL` (ou nula). Limitação declarada.
- **Impacto:** composição do denominador (pode incluir pessoas avaliadas só por outro profissional).
- Comum a C4 (AMB-C4-04, T-C4-26).

## SOL-C5-02 — Pressão arterial na visita domiciliar (`CanonicalHomeVisit`)

- **O quê:** campos de PA (`systolic_mmhg`, `diastolic_mmhg`, ou o texto bruto
  `nu_medicao_pressao_arterial` com o formato medido) na capacidade `home_visit`.
- **Por quê:** Quadro 03 (p. 5) aceita o MIVDT: «Serão considerados os registros de pressão arterial
  no campo específico.» O registro canônico só traz peso e altura.
- **Hoje:** PA registrada em visita (TACS) não comprova a prática B. Limitação declarada
  (lacuna L6 do dicionário do DW: formato da coluna não documentado).
- **Impacto:** prática B subestimada para quem só teve PA aferida em visita.

## SOL-C5-03 — Vocabulário normalizado de situação da condição e de motivo de saída

**Atendida na emenda 9526ac6.** O contrato fixa `status` `0` ativo, `1` latente, `2` resolvido e `exitReason` `135` óbito, `136` mudança de território; o pacote usa esses códigos (com testes) e continua aceitando os textuais (`RESOLVIDO`, `CONCLUIDO`, `MUDANCA_TERRITORIO`, «Óbito» …).

- **O quê:** fixar no contrato das capacidades os valores de `CanonicalCondition.status`
  (sugestão: `ATIVO`, `LATENTE`, `RESOLVIDO`, a partir do LEDI 0/1/2) e de
  `CanonicalRegistration.exitReason` (sugestão: `OBITO`, `MUDANCA_TERRITORIO`, a partir do LEDI
  135/136).
- **Por quê:** a interrupção por «todas as condições ou problemas marcados como "resolvidos"» (item
  15, p. 2) e por «Mudança de território» (item 15, p. 1) depende desses valores; as consultas da
  fundação ainda são provisórias e não dizem o que devolvem.
- **Hoje:** o pacote aceita os dois vocabulários (código LEDI e texto) — situação `0`/`1`/`2` e
  `ATIVO`/`LATENTE`/`RESOLVIDO`/`RESOLVED`/`CONCLUIDO`; base `PROFESSIONAL`/`SELF_REPORTED`; saída
  `136`/`MUDANCA_TERRITORIO`, `135`/`OBITO`. Situação fora do vocabulário (ou nula) conta como não
  resolvida e base fora do vocabulário (ou nula) não identifica; as duas são contadas e, se houver,
  aparecem no diagnóstico «AMB-C5-04: N linha(s) de condição com situação ou base fora do
  vocabulário (diagnóstico).»
- **Impacto:** denominador (interrupções). Comum a C4.

## SOL-C5-04 — Formato do CID-10 no bind `cid_codes`

**Atendida na emenda 9526ac6.** A consulta casa `cid_codes` pela categoria, sem ponto; o pacote envia exatamente os 26 códigos literais da ficha (com ponto, na ordem do item 24 f) e em Java mantém a correspondência exata (AMB-C5-04), com o diagnóstico dos vizinhos não listados.

- **O quê:** a consulta `condition_list` deve normalizar o CID-10 (com ou sem ponto) dos dois lados,
  ou o contrato deve dizer qual formato o PEC grava.
- **Por quê:** a ficha escreve os subcódigos com ponto («I11.0», p. 3); o DW pode gravar `I110`.
- **Hoje:** o bind `cid_codes` é exatamente a lista literal de 26 códigos (com ponto); a consulta casa
  pela categoria e o Java compara ignorando só o ponto (formato, não analogia: `I11.8` continua fora —
  AMB-C5-04).
- **Impacto:** nenhum no valor; confirmar no Portão C.

## SOL-C5-05 — Tipo de equipe (eSF 70 / eAP 76)

- **O quê:** uma fonte do tipo de equipe (lacuna L1; ADR 0030: inventário fora do DW ou CNES
  externo) que preencha `CanonicalTeam.teamTypeCode`, e uma capacidade que o pacote possa declarar.
- **Por quê:** «A boa prática (D) não será condicionante de pontuação para eAP, tipo 76» (item 24 b,
  p. 2).
- **Hoje:** o descritor não lê equipes (sem capacidade); se `teams()` vier preenchido com tipo `76`,
  o resultado da equipe (e o municipal que a contém) sai `RULE_AMBIGUITY` (AMB-C5-01, P07, MET-23).
  Sem tipo, a prática D é exigida de todos e a limitação explica.
- **Impacto:** até 25 pontos por pessoa em equipes eAP 76. Comum a C4 (D) e C6 (C).

## SOL-C5-06 — Versão do cadastro individual vigente no início da janela

- **O quê:** que a capacidade `individual_registration` devolva, além das versões do período, a
  última versão de cada pessoa anterior ao início da janela (a vigente no começo do período).
- **Por quê:** o vínculo é resolvido pela versão do cadastro vigente no corte (§1.7.3; item 14,
  p. 1, remete à «Nota Técnicaº 30/2025-CGESCO/DESCO/SAPS/MS», não transcrita). Com
  `scope_date_column = registration_date`, quem não atualizou o cadastro nos 24 meses lidos não
  traz versão nenhuma.
- **Hoje:** janela de 24 meses (a do esqueleto da fundação); pessoa sem versão nesse período sai
  `EXCLUIDO_SEM_VINCULO`. Limitação declarada no descritor.
- **Impacto:** denominador subestimado para cadastros antigos sem atualização. Comum a C2–C7.

## SOL-C5-07 — Capacidade de equipes (`team`) com tipo e validação

- **O quê:** além do tipo (SOL-C5-05), a validação de equipes do item 24 b («atendendo as condições
  previstas na Portaria GM/MS nº 3.493/2024») e a «última competência válida» do SCNES (item 11).
- **Hoje:** se `teams()` vier com tipo conhecido fora de {70, 76}, a pessoa sai
  `EXCLUIDO_EQUIPE_NAO_ELEGIVEL`; sem tipo, ninguém é excluído por equipe (limitação).
