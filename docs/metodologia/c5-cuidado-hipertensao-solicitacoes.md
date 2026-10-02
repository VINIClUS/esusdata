# C5 — Solicitações fora da posse do pacote

Pedidos da sessão do pacote `c5-cuidado-hipertensao` (ADR 0030) para a integração. Cada um traz o
quê, o porquê, o trecho da ficha e o impacto; enquanto não for atendido, o pacote segue com a
alternativa local declarada como limitação permanente do descritor.

## SOL-C5-01 — CBO do profissional no registro de condição (`CanonicalCondition`)

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

- **O quê:** fixar no contrato das capacidades os valores de `CanonicalCondition.status`
  (sugestão: `ATIVO`, `LATENTE`, `RESOLVIDO`, a partir do LEDI 0/1/2) e de
  `CanonicalRegistration.exitReason` (sugestão: `OBITO`, `MUDANCA_TERRITORIO`, a partir do LEDI
  135/136).
- **Por quê:** a interrupção por «todas as condições ou problemas marcados como "resolvidos"» (item
  15, p. 2) e por «Mudança de território» (item 15, p. 1) depende desses valores; as consultas da
  fundação ainda são provisórias e não dizem o que devolvem.
- **Hoje:** o pacote aceita os dois vocabulários (código LEDI e texto) — `RESOLVIDO`/`RESOLVED`/`2`/
  `CONCLUIDO`; `136`/`MUDANCA_TERRITORIO`; `135`/`OBITO` — e trata qualquer outro estado como não
  resolvido. Valor fora disso não é convertido em silêncio.
- **Impacto:** denominador (interrupções). Comum a C4.

## SOL-C5-04 — Formato do CID-10 no bind `cid_codes`

- **O quê:** a consulta `condition_list` deve normalizar o CID-10 (com ou sem ponto) dos dois lados,
  ou o contrato deve dizer qual formato o PEC grava.
- **Por quê:** a ficha escreve os subcódigos com ponto («I11.0», p. 3); o DW pode gravar `I110`.
- **Hoje:** o pacote envia os 26 códigos com ponto **e** as variantes sem ponto, e compara em Java
  ignorando o ponto (formato, não analogia: `I11.8` continua fora — AMB-C5-04).
- **Impacto:** nenhum no valor, se o bind duplo bastar; confirmar no Portão C.

## SOL-C5-05 — Tipo de equipe (eSF 70 / eAP 76)

- **O quê:** uma fonte do tipo de equipe (lacuna L1; ADR 0030: inventário fora do DW ou CNES
  externo) que preencha `CanonicalTeam.teamTypeCode`, e uma capacidade que o pacote possa declarar.
- **Por quê:** «A boa prática (D) não será condicionante de pontuação para eAP, tipo 76» (item 24 b,
  p. 2).
- **Hoje:** o descritor não lê equipes (sem capacidade); se `teams()` vier preenchido com tipo `76`,
  o resultado da equipe (e o municipal que a contém) sai `RULE_AMBIGUITY` (AMB-C5-01, P07, MET-23).
  Sem tipo, a prática D é exigida de todos e a limitação explica.
- **Impacto:** até 25 pontos por pessoa em equipes eAP 76. Comum a C4 (D) e C6 (C).
