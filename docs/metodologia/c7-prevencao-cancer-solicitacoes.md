# C7 — pedidos à integração (fora da posse do pacote)

Pedidos da sessão do pacote `c7-prevencao-cancer` (ADR 0030) para o que está fora de
`indicator/pack/c7`. Em cada um, a alternativa local adotada até a integração está declarada como
limitação permanente em `C7Pack.STANDING_LIMITATIONS`.

## S-C7-01 — Códigos AB (ABEX/ABP) como parâmetro das capacidades de procedimento

- **O quê.** Um bind `text[]` para códigos AB (por exemplo `ab_codes`) em `exam_request_evaluation`
  e `procedure_performed`, casado com `tb_dim_procedimento` pela referência AB
  (`co_seq_dim_proced_ref_ab`, dicionário do DW, linha "Exames solicitados / avaliados": "Aplicar as
  listas em SIGTAP **e** AB"), e o código AB devolvido no registro (em `sigtap_code` ou num campo
  próprio).
- **Por quê.** A ficha lista `ABEX001` "Citopatológico" e `ABP022` "Rastreamento de câncer do colo do
  útero" (Quadro 02, p. 6) para A e `ABP023` "Rastreamento de câncer de mama" (Quadro 05, p. 6) para
  D. `procedure_codes` é SIGTAP só com dígitos (guia `como-adicionar.md`), então esses códigos não vão
  na consulta.
- **Impacto.** Sem o bind, um rastreamento registrado só pelo código AB não é lido e A/D podem sair
  subestimados. A regra já compara `ABEX001`, `ABP022` e `ABP023` se chegarem no código do evento.

  os SIGTAP (`co_proced` guarda os dois). **Atendido no pacote**: `C7Codes.procedureCodes` envia
  `ABEX001`, `ABP022` e `ABP023`.

## S-C7-02 — Problemas avaliados com códigos ABP em `care_encounter`

- **O quê.** Confirmar (inventário) se `ciap_codes` do `care_encounter` traz os códigos ABP do bloco
  Avaliação ou se é preciso uma coluna própria.
- **Por quê.** Item 24, alínea g (p. 4): "Código ABP: ABP003; ABP022; ABP023." vale para C. A regra
  procura esses códigos em `ciapCodes` e `cidCodes` (AMB-C7-16).
- **Impacto.** Se os ABP não chegarem, C só conta CIAP-2/CID-10.
- **Resposta da fundação.** `ciap_codes` traz `nu_ciap` como está (pode trazer `ABP…`); confirmação no
  inventário ao vivo.

## S-C7-03 — Binds de CIAP-2/CID-10 em `care_encounter` (opcional, volume)

- **O quê.** `ciap_codes`/`cid_codes` como binds opcionais de `care_encounter` (hoje a capacidade não
  tem bind de código).
- **Por quê.** C precisa só dos atendimentos com os 28 CIAP-2, 105 CID-10 e 3 ABP da alínea g
  (pp. 3–4). Sem bind, a parte lê todos os atendimentos de 12 meses de quem tem 14 a 69 anos e o
  filtro é feito em Java.
- **Impacto.** Só volume (§1.9.2); o resultado não muda.
- **Resposta da fundação.** Adiado (só volume).

## S-C7-04 — Domínio de sexo e identidade de gênero no registro canônico

- **O quê.** Fixar no contrato de `citizen` o domínio de `gender_identity` (proposta: código LEDI
  `identidadeGeneroCidadao`, 149 "Homem transgênero", 150 "Mulher transgênero") e de `sex`
  (`FEMININO`/`MASCULINO`/`INDETERMINADO`/null, como já diz `CanonicalPerson`).
- **Por quê.** Itens 4.1–4.2 (p. 5) usam os rótulos "Homem transgênero" e "Mulher transgênero"; a
  regra compara os códigos LEDI 149/150 (AMB-C7-12). Se a consulta devolver outro formato, a
  elegibilidade dos homens transgênero e a exclusão do item 4.2 deixam de funcionar.
- **Impacto.** Denominadores de A–D.
- **Resposta da fundação.** Adotado: `genderIdentity` em código LEDI (149/150), `sex` em palavras.

## S-C7-05 — Tipo de equipe (eSF 70 / eAP 76) e óbito do CadSUS

- **O quê.** Fonte de tipo de equipe (lacuna L1) e de óbito nacional, quando houver (ADR 0030).
- **Por quê.** Item 24, b (p. 3): "tipo 70 e 76"; item 15 (p. 2): "Óbito no CADSUS".
- **Impacto.** Hoje: equipe não validada e só óbito local (limitações permanentes).
- **Resposta da fundação.** Limitações mantidas.

## S-C7-06 — Status por componente com ambiguidade

- **O quê.** Confirmar que `ResultComponent` com `status = RULE_AMBIGUITY` (valor nulo, contagens
  exatas) é aceito pela persistência, pela API e pela tela (`ResultComponent.of` só produz
  `COMPUTED`/`NO_DENOMINATOR`; o C7 constrói o componente direto).
- **Por quê.** AMB-C7-05, AMB-C7-06 e AMB-C7-08 afetam o valor de um subgrupo só quando o caso
  ocorre; o componente fica sem valor e o resultado `RULE_AMBIGUITY` (Tech Spec §4.2).
- **Impacto.** Exibição dos componentes de C7.
- **Resposta da fundação.** Aceito; e `EvidenceDecision.PRACTICE_AMBIGUOUS` passa a ser a decisão da
  evidência para AMB-C7-05/06/08 (adotado no pacote).

## S-C7-07 — Janelas lidas no `CanonicalDataset` da execução

- **O quê.** Que o `RunExecutor` registre no `CanonicalDataset` a janela de cada capacidade lida
  (`Builder.window`), como o v1 já faz (`ofEncounters`).
- **Por quê.** "`null` ≠ zero: ausência de fonte, de denominador ou de capacidade nunca vira 0"
  (`como-adicionar.md`). O C7 confere essas janelas com `requirements` e devolve
  `UNSUPPORTED_SOURCE` quando falta uma capacidade ou a janela é menor. Um dataset que não declara
  nenhuma janela é aceito como validado pela execução (ADR 0030), e aí a conferência não acontece.
- **Impacto.** Sem as janelas, uma parte não lida faria B, C ou A/D saírem 0/d em vez de sem valor.

## S-C7-08 — Decisão metodológica para AMB-C7-06 e AMB-C7-08 (para a equipe)

- **O quê.** Esclarecimento oficial (ou decisão documentada da equipe) para a janela da dose de HPV
  (ficha sem janela × NT nº 8/2026, Figura 2, "Últimos 60 meses") e para a vigência do
  02.02.10.025-1 (registros anteriores a 2026-01).
- **Por quê.** O esquema usual dá a dose aos 9 anos. Quem tem 14 anos e tomou a dose aos 9 a tem há
  mais de 60 meses, então a AMB-C7-06 ocorre em quase todo município. Nesse caso B, e com ele o C7,
  fica `RULE_AMBIGUITY`. Com a AMB-C7-08 é igual até 2030, para quem só tem exame molecular anterior
  a 2026.
- **Impacto.** Disponibilidade do valor do C7 depois que os portões forem concluídos.
