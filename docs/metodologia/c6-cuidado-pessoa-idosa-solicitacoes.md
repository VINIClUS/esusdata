# C6 — Solicitações fora da posse do pacote

Pedidos da sessão do pacote C6 (`c6-cuidado-pessoa-idosa@0.1.0`) para a integração (ADR 0030). Cada
item diz o quê, por quê (com o trecho da ficha), o impacto e a alternativa local adotada enquanto
isso, que está declarada como limitação permanente em `C6Pack`.

## S-C6-01 — Tipo de equipe (eSF 70 / eAP 76) no extrato

- **O quê**: uma capacidade que entregue registros `team` (`CanonicalTeam.teamTypeCode`, com
  `observedAt`) para os INEs do município, de fonte documentada (inventário fora do DW ou CNES
  externo datado; lacuna L1 do dicionário). O descritor do C6 não lista capacidade de equipe porque
  ela não existe.
- **Por quê**: item 24 b (p. 2): «A boa prática (C) não será condicionante de pontuação para eAP,
  tipo 76, atendendo as condições previstas na PRC GM/MS nº 02/2017.» (AMB-C6-01, MET-23).
- **Impacto**: sem o tipo, C é exigida de todas as equipes; uma eAP 76 sai subestimada em até 25
  pontos por pessoa.
- **Alternativa local**: a regra já lê `CanonicalDataset.teams()`; quando uma equipe aparece como
  `76` (observação mais recente até o corte) o resultado dessa equipe e o municipal ficam
  `RULE_AMBIGUITY`, com C informativa na evidência (`C_INFORMATIVA_EAP76_AMB_C6_01`). Sem
  registros `team`, nada é presumido.

## S-C6-02 — Procedimento consolidado fora de `procedure_performed`

- **O quê**: confirmar (ou garantir na consulta da fase 1c) que `procedure_performed` não devolve
  registros de procedimento consolidado.
- **Por quê**: Quadro 03 (p. 4): «Serão considerados os registros com os códigos SIGTAP
  especificados, com exceção do registro de procedimento consolidado.»
- **Impacto**: um consolidado sem pessoa identificada não deveria chegar; se chegar com chave de
  pessoa, `0101040024` cumpriria B indevidamente.
- **Alternativa local**: a regra só aceita `stage = PERFORMED` e não conhece um valor de `origin`
  para consolidado; limitação AMB-C6-07 no descritor.

## S-C6-03 — CBO do profissional no MIAC e motivo da visita no MIVDT

- **O quê**: (a) `measurement_record` com `origin = MIAC` deve trazer o CBO do profissional que
  registrou a atividade coletiva; (b) `home_visit.reason_codes` deve vir preenchido a partir dos
  motivos (`st_acomp_*`) da visita.
- **Por quê**: Quadro 03 (p. 4–5) restringe B a uma lista de CBO para todos os modelos, inclusive o
  MIAC; item 24 e (p. 2), MIVDT: «considera o registro de visitas domiciliares, com preenchimento
  do ‘‘motivo da visita’’, desde que registrado por ACS/TACS».
- **Impacto**: MIAC sem CBO não cumpre B; visita sem motivo não conta para C.
- **Alternativa local**: aplicada a leitura literal (CBO obrigatório; motivo não vazio).

## S-C6-04 — Data de aplicação da transcrição e doses da RNDS

- **O quê**: `immunization_history.application_date` deve ser a data de aplicação também na
  transcrição de caderneta (nunca a de digitação); e uma fonte RNDS/RIA quando houver (lacuna L4).
- **Por quê**: Quadro 05 (p. 5), RIA: «Registro da vacina ou transcrição.»; item 26 (p. 3), fonte
  «RNDS»; AMB-C6-09.
- **Impacto**: dose de outro serviço só na RNDS não aparece; a regra não a trata como ausência
  de vacinação, mas o valor sai subestimado.
- **Alternativa local**: limitação "Dados fora do PEC local" no descritor.

## S-C6-05 — Registrar na transcrição as convenções que não estão nas AMB

As convenções abaixo foram escolhidas pelo pacote sem texto da ficha que as decida; a transcrição
não pode ser alterada pela sessão do pacote (só correções), então pedimos que a orquestradora as
registre como AMB-C6-12 em diante, para revisão nos Portões A/B:

1. **Aniversário** (ENG-27): `AnniversaryRule.CLAMP_TO_MONTH_END` (29/02 + 1 ano = 28/02), a
   mesma aritmética do bind de nascimento. Só muda o resultado para quem nasceu em 29/02 e faz
   60 anos num ano não bissexto (nascidos em 29/02/2040, competência 2100-02).
2. **Vínculo local** (AMB-C6-04, §1.7.3): versão completa do cadastro individual de maior data
   até o corte; cadastro simplificado não vincula; recusa e ficha inativa excluem; versões da
   mesma data que divergem em INE/CNES/saída/inativa/recusa excluem como conflito
   (`EXCLUIDO_VINCULO_CONFLITANTE`), sem escolher uma. Pessoa sem INE no cadastro vigente entra
   na equipe `ine = null`.
3. **Códigos de saída do cadastro**: 136 (mudança de território) e 135 (óbito) são do LEDI
   (`MotivoSaida`, dicionário do DW), não da ficha (item 15, p. 1).
4. **Problema/condição avaliada** (item 24 e, MIAI): a consulta (A) não exige CIAP/CID avaliado,
   pela leitura literal do Quadro 02 (AMB-C6-06).
5. **Data de nascimento divergente** entre linhas da mesma chave de pessoa: excluída
   (`EXCLUIDO_DATA_NASCIMENTO_DIVERGENTE`), sem escolher uma das datas.

## S-C6-06 — Helpers comuns a C4, C5 e C6

- **O quê**: mover para `indicator/model` (ou um pacote comum) a coorte vinculada por versões do
  cadastro (`C6Cohort`), o par de visitas com intervalo mínimo e o "peso e altura no mesmo dia"
  (`C6Practices`), quando C4 e C5 tiverem os seus.
- **Por quê**: seção "Comum a C4, C5 e C6" de `c4-cuidado-diabetes.md` (itens 5, 6, 7, 12, 13 e
  14): mesma política de eAP, mesma interrupção, mesmo módulo de vínculo, mesma antropometria e
  mesmas visitas.
- **Impacto**: hoje a lógica fica privada do pacote C6; três cópias divergiriam na reconciliação.
- **Alternativa local**: classes package-private em `indicator/pack/c6`.
