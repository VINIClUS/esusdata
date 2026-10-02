# C2 — Solicitações fora da posse do pacote

Pedidos da sessão do pacote C2 (`c2-desenvolvimento-infantil`) para a integração (ADR 0030). Cada
pedido diz o quê, por quê, o trecho da ficha e o impacto; a alternativa local adotada até lá está
declarada como limitação permanente em `C2Pack` e mantém o pacote `BLOCKED`.

## S-C2-01 — Marcação de "Puericultura" no `CanonicalCareEvent` (capacidade `care_encounter`)

- **O quê:** um campo do registro canônico (por exemplo `childCare: Boolean`) que diga se o atendimento
  individual teve o problema/condição avaliado "Puericultura", e a coluna correspondente na consulta
  congelada de `care_encounter`.
- **Por quê:** a ficha só pontua consultas "Com indicação de problema/condição avaliado
  “Puericultura”" (Quadro 02, p.5; nota de rodapé 2, p.7). O dicionário do DW não tem coluna que
  distinga o atendimento de puericultura (lacuna L7); o guia diz que o campo de puericultura gera
  `A98`/`Z001` automaticamente, mas esses códigos não são exclusivos (AMB-GUIA-01) e não estão na
  ficha, então o pacote não os usa.
- **Impacto:** sem o campo, A e B contam todas as consultas de médico/enfermeiro (limitação
  permanente; o local pode ficar acima do Siaps).

## S-C2-02 — Tipo de equipe (eSF 70 / eAP 76) — capacidade para o registro `team`

- **O quê:** uma capacidade que entregue `CanonicalTeam.teamTypeCode` por INE (inventário fora do DW ou
  CNES externo, ADR 0030 / lacuna L1), e a sua inclusão em `requiredCapabilities` do C2.
- **Por quê:** "A boa prática (D) considera a pontuação integral para eAP, tipo 76." (24 b, p.2); o
  Quadro 02 exige profissional "alocado conforme os códigos das equipes descritos" (AMB-C2-11).
- **Impacto:** o pacote já lê `CanonicalDataset.teams()` (tipo "76" ⇒ D isenta com `PRACTICE_EXEMPT`);
  sem a capacidade, toda criança fica com D avaliada e o resultado traz a limitação com a contagem.

## S-C2-03 — Vocabulário congelado de `reason_codes` e `outcome_code` da visita (`home_visit`)

- **O quê:** fixar no descritor da capacidade `home_visit` os valores que a consulta devolve em
  `reason_codes` e `outcome_code`.
- **Por quê:** a ficha exige motivo "recém-nascido" ou "criança" (24 e, p.3) e a C2 não fala do
  desfecho (AMB-C2-08 iv). O pacote declarou, em `C2Codes`, os tokens `ACOMP_RECEM_NASCIDO` e
  `ACOMP_CRIANCA` (das colunas `st_acomp_recem_nascido` e `st_acomp_crianca` de
  `tb_fat_visita_domiciliar`) e o desfecho `1` (LEDI, visita realizada). A consulta de `home_visit`
  ainda é provisória.
- **Impacto:** se a consulta real usar outros valores, D nunca cumpre. Pede-se que a fase 1c adote
  esses valores ou avise para o pacote trocar as constantes.

## S-C2-04 — Domicílio e modalidade em `care_encounter`

- **O quê:** confirmar que `care_location_code` devolve o código LEDI (4 = domicílio) ou que `form`
  vira `HOME` para atendimento domiciliar, e que `remote` vem de `co_dim_tp_particip_cidadao`
  (lacuna L3).
- **Por quê:** a prática A exige consulta "presencial" (item 16, p.2); domiciliar é AMB-C2-04 e
  remoto não cumpre. O pacote trata `form = HOME` ou `care_location_code = 4` como domiciliar e
  `remote = null` como modalidade desconhecida (leitura `LACUNA-L3`, que torna A ambígua quando decide).
- **Impacto:** sem a confirmação, A pode ficar ambígua para muitas crianças.

## S-C2-05 — Data de registro da transcrição de vacina (`immunization_history`)

- **O quê:** um campo `registration_date` em `CanonicalImmunization` (o `co_dim_tempo` da dose, quando
  `transcription = true`; a data de aplicação já vem de `co_dim_tempo_vacina_aplicada`).
- **Por quê:** AMB-C2-09 v e AMB-C2-10 i (CT-C2-61: dose aplicada aos 13 meses e transcrita aos 26).
  Hoje o pacote conta a transcrição pela data de aplicação e declara a limitação.
- **Impacto:** sem o campo, a ambiguidade de transcrições tardias não é detectável.

## S-C2-06 — Unificação de cadastro e óbito do CadSUS

- **O quê:** nada a implementar agora; registro para o Portão C. A capacidade `citizen` promete chave
  unificada (`tb_dim_cidadao_pec_grupo`); o óbito do CadSUS (item 15) não existe no PEC local.
- **Impacto:** limitações permanentes (AMB-C2-14; óbito só pelo cadastro local).
