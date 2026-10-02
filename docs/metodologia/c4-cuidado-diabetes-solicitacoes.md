# C4 — Solicitações à integração (fora da posse do pacote)

Pedidos da sessão do pacote `c4-cuidado-diabetes` para a orquestradora (ADR 0030, `docs/indicadores/como-adicionar.md`).
Cada um diz o quê, por quê (com o trecho da ficha), o impacto e a alternativa local adotada até ser atendido.
Páginas: PDF da ficha C4 (SEI 0055986848), transcrição em [`c4-cuidado-diabetes.md`](c4-cuidado-diabetes.md).

## S-C4-01 — CBO de quem avaliou a condição em `condition_list` / `CanonicalCondition`

- **O quê**: coluna `cbo` (e, se possível, `form`/modelo de informação de origem) em `condition_list@…` e campo
  correspondente em `CanonicalCondition`.
- **Por quê**: item 5 (p. 1): «Pessoa com diabetes: pessoa identificada a partir de atendimento individual com a
  condição avaliada de diabetes, realizada por enfermeira(o) e/ou médica(o) da APS, no Modelo de Informação de
  Atendimento Individual (MIAI), em pelo menos uma ocasião desde 2013.» O DW tem o profissional no cabeçalho do
  atendimento (`tb_fat_atd_ind_problemas.co_fat_atd_ind` → `tb_fat_atendimento_individual`).
- **Impacto**: hoje uma condição profissional da lista de problemas entra sem conferir o CBO (caso T-C4-26 só é
  verificável quando a evidência vem do atendimento, `care_encounter`, que só cobre 12 meses).
- **Alternativa local**: condição com `basis = PROFESSIONAL` desde 2013 qualifica sem CBO; atendimento individual
  com T89/T90/E10*/E11*/E14* exige CBO de médico/enfermeiro. Declarado nas limitações do descritor.

## S-C4-02 — Vocabulário canônico de `status` (condição) e `exit_reason` (cadastro)

- **O quê**: congelar no descritor das capacidades os valores de `condition.status`, `condition.code_system`
  (o pacote espera `CIAP2` e `CID10`, como em `CanonicalCondition`), `condition.basis` (`PROFESSIONAL`) e
  `registration.exit_reason`.
  O pacote usa os códigos LEDI que o DW grava em `nu_identificador`: situação `0` Ativo, `1` Latente, `2`
  Resolvido (`tb_dim_situacao_problema`); saída `135` Óbito, `136` Mudança de território
  (`tb_dim_tipo_saida_cadastro`).
- **Por quê**: item 15 (p. 2) «todas as condições ou problemas marcados como "resolvidos" no PEC» e «Saída do
  cidadão do cadastro» com «Mudança de território»; item 4.1 (p. 4) «“resolvidos” ou “concluídos”».
- **Impacto**: se o adaptador devolver outro vocabulário (ex.: `RESOLVED`, `CID-10`), ninguém é interrompido
  ou a coorte esvazia, em silêncio.
- **Alternativa local**: constantes em `C4Codes` (`RESOLVED_STATUS`, `EXIT_DEATH`, `EXIT_TERRITORY_CHANGE`).
  «Concluído» não tem código LEDI; fica como «não resolvido» até o Portão C (AMB-C4-04).

## S-C4-03 — `condition_list` deve casar CID-10 pela categoria

- **O quê**: a consulta real de `condition_list` deve aplicar `cid_codes` por prefixo de três caracteres
  (categoria), não por igualdade, e devolver o código com o subcódigo como o PEC grava.
- **Por quê**: nota de rodapé 3 (p. 7): «a lista de CID-10 foi ajustada para apresentar as categorias E10, E11 e
  E14, contemplando seus respectivos subcódigos.» O pacote passa `cid_codes = [E10, E11, E14]`.
- **Impacto**: com igualdade, quem tem só `E11.9`/`E119` fica fora da coorte.
- **Alternativa local**: o pacote também confere a categoria em Java (`C4Codes.isEligibleCid`); falta só a
  consulta não filtrar demais.

## S-C4-04 — Tipo de equipe (lacuna L1) para a exceção eAP 76 da prática D

- **O quê**: uma capacidade de equipe (`team`, `CanonicalTeam.teamTypeCode`) a partir de fonte documentada
  (inventário do PEC transacional ou CNES externo datado), como a ADR 0030 já prevê.
- **Por quê**: item 24 b (p. 2): «A boa prática (D) não será condicionante de pontuação para eAP, tipo 76,
  atendendo as condições previstas na PRC GM/MS nº 02/2017.»
- **Impacto**: sem o tipo, equipes eAP 76 são calculadas como se a exceção não existisse.
- **Alternativa local**: o pacote já lê `data.teams()` quando houver registros e, para eAP 76, devolve
  `RULE_AMBIGUITY` (AMB-C4-01, MET-23) com as práticas exibidas e sem redistribuir pesos. Sem a capacidade, a
  limitação fica no descritor. Quando houver a capacidade, acrescentá-la a `requiredCapabilities` do C4 (e de C5/C6,
  política comum).

## S-C4-05 — Pressão arterial da visita domiciliar (lacuna L6) e marcação MIAC

- **O quê**: (a) `systolic_mmhg`/`diastolic_mmhg` em `home_visit` depois que o inventário medir o formato de
  `nu_medicao_pressao_arterial`; (b) em `measurement_record` com `origin = MIAC`, o código da atividade (04–07)
  para o filtro do item 24 e.
- **Por quê**: Quadro 03 (p. 5): MIVDT «Serão considerados os registros de pressão arterial no campo específico.»;
  item 24 e (p. 3) MIAC «código 04, 05, 06 e 07, de forma específica ou compartilhada».
- **Impacto**: PA registrada só na visita (por TACS) não comprova B; antropometria de atividade coletiva entra
  sem o filtro de tipo de atividade.
- **Alternativa local**: B não lê visita; C aceita participante identificado com peso e altura (AMB-C4-10).
  Ambos nas limitações do descritor.

## S-C4-06 — Campo «avaliação dos pés» do MIAI (prática F)

- **O quê**: identificar no Portão C o campo do atendimento individual que registra a avaliação dos pés e
  expô-lo em `care_encounter`.
- **Por quê**: Quadro 07 (p. 6), MIAI: «Serão considerados os registros de avaliação dos pés.» A ficha não nomeia
  o campo (AMB-C4-09); o guia manda registrar `03.01.04.009-5`.
- **Alternativa local**: F só por `03.01.04.009-5` (MIP ou procedimento do atendimento) com CBO do Quadro 07.

## S-C4-07 — Divergência entre a instrução da sessão e a ficha sobre o autorreferido

- A instrução da sessão pedia coorte por `condition_list` «mais o autorreferido do cadastro». A ficha não admite:
  item 5 (p. 1) exige condição avaliada em atendimento por médico/enfermeiro, e a transcrição registra o caso
  T-C4-27 («Diabetes só autorreferido no cadastro individual» → não entra). O pacote segue a ficha: o autorreferido
  só torna a pessoa candidata e ela aparece na evidência como `EXCLUDED` / `SEM_CONDICAO_AVALIADA` (ENG-36).
  Pede-se confirmação na revisão dos Portões A/B.
