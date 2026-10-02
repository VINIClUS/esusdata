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

## S-C7-02 — Problemas avaliados com códigos ABP em `care_encounter`

- **O quê.** Confirmar (inventário) se `ciap_codes` do `care_encounter` traz os códigos ABP do bloco
  Avaliação ou se é preciso uma coluna própria.
- **Por quê.** Item 24, alínea g (p. 4): "Código ABP: ABP003; ABP022; ABP023." vale para C. A regra
  procura esses códigos em `ciapCodes` e `cidCodes` (AMB-C7-16).
- **Impacto.** Se os ABP não chegarem, C só conta CIAP-2/CID-10.

## S-C7-03 — Binds de CIAP-2/CID-10 em `care_encounter` (opcional, volume)

- **O quê.** `ciap_codes`/`cid_codes` como binds opcionais de `care_encounter` (hoje a capacidade não
  tem bind de código).
- **Por quê.** C precisa só dos atendimentos com os 28 CIAP-2, 105 CID-10 e 3 ABP da alínea g
  (pp. 3–4). Sem bind, a parte lê todos os atendimentos de 12 meses de quem tem 14 a 69 anos e o
  filtro é feito em Java.
- **Impacto.** Só volume (§1.9.2); o resultado não muda.

## S-C7-04 — Domínio de sexo e identidade de gênero no registro canônico

- **O quê.** Fixar no contrato de `citizen` o domínio de `gender_identity` (proposta: código LEDI
  `identidadeGeneroCidadao`, 149 "Homem transgênero", 150 "Mulher transgênero") e de `sex`
  (`FEMININO`/`MASCULINO`/`INDETERMINADO`/null, como já diz `CanonicalPerson`).
- **Por quê.** Itens 4.1–4.2 (p. 5) usam os rótulos "Homem transgênero" e "Mulher transgênero"; a
  regra compara os códigos LEDI 149/150 (AMB-C7-12). Se a consulta devolver outro formato, a
  elegibilidade dos homens transgênero e a exclusão do item 4.2 deixam de funcionar.
- **Impacto.** Denominadores de A–D.

## S-C7-05 — Tipo de equipe (eSF 70 / eAP 76) e óbito do CadSUS

- **O quê.** Fonte de tipo de equipe (lacuna L1) e de óbito nacional, quando houver (ADR 0030).
- **Por quê.** Item 24, b (p. 3): "tipo 70 e 76"; item 15 (p. 2): "Óbito no CADSUS".
- **Impacto.** Hoje: equipe não validada e só óbito local (limitações permanentes).

## S-C7-06 — Status por componente com ambiguidade

- **O quê.** Confirmar que `ResultComponent` com `status = RULE_AMBIGUITY` (valor nulo, contagens
  exatas) é aceito pela persistência, pela API e pela tela (`ResultComponent.of` só produz
  `COMPUTED`/`NO_DENOMINATOR`; o C7 constrói o componente direto).
- **Por quê.** AMB-C7-05, AMB-C7-06 e AMB-C7-08 afetam o valor de um subgrupo só quando o caso
  ocorre; o componente fica sem valor e o resultado `RULE_AMBIGUITY` (Tech Spec §4.2).
- **Impacto.** Exibição dos componentes de C7.
