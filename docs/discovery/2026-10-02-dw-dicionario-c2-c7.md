# Descoberta — dicionário de dados do DW e-SUS APS para C2–C7 (2026-10-02)

Fase 1a do plano C2–C7. Trabalho **somente documental**: este arquivo monta, a partir da documentação
oficial do Laboratório Bridge/UFSC, o dicionário das tabelas do Data Warehouse (DW) do PEC de que as
fichas C2–C7 precisam, e aponta onde cada dado exigido mora — ou que não mora em lugar nenhum
documentado. Nenhuma consulta foi executada contra um PEC, nenhuma SQL foi escrita ou validada e nenhum
outro arquivo do repositório foi alterado.

O que já está comprovado ao vivo continua sendo só o que o C1 usa: 7 objetos
(`tb_fat_atendimento_individual`, `tb_dim_tempo`, `tb_dim_municipio`, `tb_dim_tipo_atendimento`,
`tb_dim_unidade_saude`, `tb_dim_equipe`, `tb_dim_cbo`) nas colunas listadas em
`contracts/compatibility/pec-adapters.json`, em PEC 5.4.37 (CT 133) e 5.5.28 (produção municipal) —
ver [2026-09-19](2026-09-19-pec-ct133.md) e [2026-09-24](2026-09-24-pec-5528.md). O fingerprint ENG-43
cobre só essas colunas (nome, tipo, posição, nulidade); ele **não** prova que o resto das tabelas é igual
nas duas versões.

## Principais achados

1. **Recorte municipal.** Todos os fatos de evento usados por C2–C7 têm `co_dim_municipio` →
   `tb_dim_municipio.co_seq_dim_municipio`, o mesmo caminho do C1 (`WHERE m.co_ibge = ?`), inclusive as
   tabelas-filhas (problemas, exames, procedimentos, doses, participantes). Não têm município: as tabelas
   consolidadas por cidadão, `tb_fat_cuidado_compartilhado` (tem três municípios) e a visualização
   `tb_acomp_cidadaos_vinculados` (sem código IBGE). `tb_dim_unidade_saude` e `tb_dim_equipe` **não têm
   município**. Armadilha: `tb_fat_cad_individual.co_dim_municipio_cidadao` é o município de **nascimento**.
2. **Pessoa.** `co_fat_cidadao_pec` é a chave comum de quase todos os fatos, mas aponta para
   `tb_fat_cidadao_pec`, que não tem página e aparece entre as tabelas de relatórios operacionais que
   "em breve" serão descontinuadas. A unificação de cadastros do mesmo cidadão está em
   `tb_dim_cidadao_pec_grupo` (`co_fat_cidadao_pec` → `co_cidadao`, `co_cidadao_master`). Doses
   (`tb_fat_vacinacao_vacina`) não têm a coluna: o cidadão vem do cabeçalho por `co_fat_vacinacao`.
3. **Exames solicitados/avaliados** ficam em `tb_fat_atd_ind_procedimentos`
   (`co_dim_procedimento_solicitado` / `co_dim_procedimento_avaliado` → `tb_dim_procedimento.co_proced`),
   apesar do nome. Os resultados estruturados (com `dt_solicitacao`, `dt_realizacao`, `dt_resultado`)
   ficam em `tb_fat_atd_ind_exames`. Procedimentos **realizados** vão para os fatos de procedimentos:
   cabeçalho em `tb_fat_procedimento`; os individualizados em `tb_fat_proced_atend` e
   `tb_fat_proced_atend_proced.co_dim_procedimento`. Pelas Regras, isso inclui o procedimento lançado
   no plano ou na finalização de um atendimento do PEC e a escuta inicial de nível médio.
4. **Vacinação.** Imunobiológico, dose, estratégia, transcrição (`st_registro_anterior`) e data de
   aplicação (`co_dim_tempo_vacina_aplicada`) estão em `tb_fat_vacinacao_vacina`. O código da ficha
   ("42 – Vacina penta") coincide com o código LEDI (42 = "vacina penta (DTP/HB/Hib)", sigla `PENTA`).
   A coluna candidata é `tb_dim_imunobiologico.nu_identificador`, o que ainda é inferência a validar.
   Nunca usar `co_seq_dim_imunobiologico`.
5. **Condições.** CIAP-2/CID-10 avaliados, situação (0 ativo / 1 latente / 2 resolvido no LEDI) e datas de
   início e fim do problema estão em `tb_fat_atd_ind_problemas` (`co_dim_ciap`, `co_dim_cid`,
   `co_dim_situacao`, `st_avaliado`, `co_dim_data_inicio_problema`, `co_dim_data_fim_problema`). As
   condições autorreferidas estão em `tb_fat_cad_individual` (`st_diabete`, `st_hipertensao_arterial`,
   `st_gestante`).
6. **Modalidade.** O domicílio aparece em `co_dim_local_atendimento` (LEDI 4). O remoto aparece em
   `co_dim_tp_particip_cidadao` (LEDI 2 = presencial; 3 a 7 = vídeo, voz, e-mail, mensagem, outros). A
   dimensão dessa última coluna **não tem página** e o nome dela diverge entre as páginas de FAI e FAO.
7. **Medidas.** Peso, altura e PA sistólica/diastólica estão no atendimento individual e no atendimento
   de procedimentos (`tb_fat_proced_atend`). A visita tem peso e altura, mas a PA vem numa coluna única,
   `nu_medicao_pressao_arterial`, de formato não documentado. Os participantes de atividade coletiva só
   têm peso e altura, **sem PA**.
8. **Visita ACS/TACS.** Os dados estão em `tb_fat_visita_domiciliar`: `co_dim_cbo` (515105 ou 322255),
   `co_dim_desfecho_visita` (LEDI 1 = realizada), `co_fat_cidadao_pec` e os motivos
   (`st_acomp_gestante`, `st_acomp_puerpera`, `st_acomp_recem_nascido` etc.). A data só vem por
   `co_dim_tempo`.
9. **Coorte.** `tb_fat_cad_individual` é **versionada**: cada atualização gera uma linha. Ela traz
   `dt_nascimento`, `co_dim_sexo`, `co_dim_identidade_genero`, `dt_obito`, `co_dim_tipo_saida_cadastro`
   (LEDI 135 = óbito, 136 = mudança de território) e a equipe e a unidade do registro. A visualização
   `tb_acomp_cidadaos_vinculados` dá o vínculo atual (`nu_ine_vinc_equipe`, `nu_cnes_vinc_equipe`), mas
   **sem histórico**.
10. **Códigos.** As chaves `co_seq_dim_*` não são os códigos LEDI das fichas. Há prova no C1: o id 2 é
    "Consulta agendada programada / Cuidado continuado", que no LEDI tem código 1. Filtrar sempre por
    `nu_identificador`, `nu_cbo`, `co_proced`, `nu_ciap` ou `nu_cid`, e só depois de validar o conteúdo.

As lacunas estão na seção 4: tipo de equipe (eSF 70 / eAP 76), data de desfecho da gestação, dimensão
de tipo de participação, doses de origem RNDS/RIA, PA de participantes de atividade coletiva, formato
da PA da visita, marcação de puericultura/pré-natal, histórico do vínculo e tipos de dado.

## 1. Proveniência

| Item | Valor |
|---|---|
| Fonte | Documentação oficial do DW e-SUS APS PEC, Laboratório Bridge/UFSC: <https://integracao.esusaps.bridge.ufsc.tech/dw/> |
| Link antigo | <https://integracao.esusab.ufsc.br/dw> responde `301` → `https://integracao.esusaps.bridge.ufsc.tech/dw` → `301` → `/dw/` (conferido em 2026-10-02) |
| Data de acesso | **2026-10-02** — todas as páginas baixadas nessa data (HTTP 200) com `curl` pelo proxy da sessão; texto e tabelas extraídos com `python3` (`html.parser`) |
| Rótulo do site | "Integração e-SUS APS PEC — versão 8.7.0". É a versão do **LEDI**: a seção "Versão 8.7.0" das principais alterações do LEDI (PNCT manutenção; CPF na avaliação de elegibilidade) corresponde ao item "v.5.5.25 → v.5.5.26" de `/dw/principais_alteracoes.html` |
| Cobertura | Índice do DW, principais alterações, índice de fatos e os 15 índices de grupo, as **39** páginas de tabela fato, o índice de dimensões e as **89** páginas de dimensão (o índice lista 89 itens, não 90), o índice de visualizações e a página `tb_acomp_cidadaos_vinculados`. O Apêndice A traz o "Alterado em" de cada uma |
| Apoio (mesmo site, LEDI — não é doc do DW) | Dicionário de dados LEDI (domínios: imunobiológico, dose, estratégia, local de atendimento, tipo de atendimento, desfecho, situação do problema, motivo de saída, sexo, identidade de gênero, tipo de participação, tipo de atividade coletiva), lista de CBOs, MI Atendimento Individual, MI Visita Domiciliar e Territorial, MI Atividade Coletiva, MI Vacinação e Regras de vacinação. As datas estão no Apêndice A |
| Tipos de dado | As páginas de fatos e de dimensões **não publicam tipos**: trazem só "Colunas DW", "Referência LEDI" e "Referência sistema". Só a visualização tem a coluna "Tipo". Os tipos devem vir de `information_schema` no inventário |

Datas de alteração das páginas-índice: `/dw/` **11/09/2026**; `/dw/principais_alteracoes.html`
**11/09/2026**; `/dw/fatos/index.html` **09/02/2026**; `/dw/dimensoes/index.html` **13/07/2026**;
`/dw/visualizacoes/index.html` **15/08/2024**;
`/dw/visualizacoes/acompanhamento_cidadaos_vinculados.html` **19/06/2026**. Cada seção da parte 2 repete a
URL e o "Alterado em" da sua página.

> **Aviso.** Documentação pública; não comprova a versão instalada — validar com o inventário e o
> `ExecPlaneLivePecTest`. A doc descreve a versão corrente (LEDI 8.7.0; o histórico do DW vai até
> v5.5.26). As instalações conhecidas rodam **5.4.37** (CT 133) e **5.5.28** (produção). O intervalo
> v5.4.23 → v5.5.21 não tem alterações itemizadas: a doc diz só que foi "amplamente revisada". O CT 133
> tinha 52 `tb_fat_*` e 88 `tb_dim_*`. A doc tem 39 páginas de fato, mais 11 nomes citados só na
> Tabela 1 do índice de fatos, e 89 páginas de dimensão, mais 12 dimensões citadas sem página. Os dois
> conjuntos precisam ser comparados com o catálogo antes de qualquer consulta.

## 2. Dicionário por tabela

### 2.0 Convenções

- **documentado**: afirmado na página oficial. **inferido**: dedução a partir de nome, descrição ou
  LEDI. Precisa de validação ao vivo antes de virar coluna de contrato.
- Marcações na última coluna:
  - `PK`;
  - `FK → tabela.coluna`, copiado como a doc escreve, inclusive quando a doc erra (os erros estão
    listados em 2.13);
  - **recorte municipal**;
  - **data do registro**;
  - **[PII — nunca projetar]** para nome, nome social, nomes dos pais, CPF, CNS, NIS, DNV, DO,
    prontuário, telefones, e-mail, endereço, CEP, geolocalização, texto livre e `co_identificacao`;
  - *[sensível …]* para dados necessários a alguma regra, mas que só devem sair do PEC na capacidade
    que os exige;
  - ⚠ para armadilha ou divergência.
- A coluna "LEDI" é a "Referência LEDI" da página (o nome do campo no modelo de informação). Quando a
  doc não tem referência, aparece "—".
- As **chaves substitutas** (`co_seq_dim_*`) são locais da instalação e não correspondem aos códigos
  LEDI usados pelas fichas, como avisa o próprio índice de dimensões. Os códigos LEDI citados nas notas
  vêm do dicionário LEDI do mesmo site; a coluna do DW que os guardaria (`nu_identificador` na maioria
  das dimensões) ainda é **inferida**.
- As seções 2.1 a 2.11 são geradas a partir das páginas oficiais, sem edição das descrições; o texto
  de cada página foi mantido. Ficam documentadas **52 tabelas** (20 fatos, 1 visualização e 31
  dimensões). As 2.12 e 2.13 foram redigidas à mão.

### 2.1 Atendimento individual (MIAI)

#### `tb_fat_atendimento_individual` — Tabela fato do atendimento individual

Fonte: <https://integracao.esusaps.bridge.ufsc.tech/dw/fatos/atendimento_individual/tb_fat_atendimento_individual.html> — **Alterado em 11/09/2026**.

Texto da doc (objetivo/regras/notas):

- A tabela `tb_fat_atendimento_individual` é populada sempre que os dados de um atendimento individual são processados.
- 1. A `tb_fat_atendimento_individual` é preenchida quando são processado(a)s:
  - Fichas de atendimento individual recebidas através da importação de sistemas terceiros ou outras instalações do PEC;
  - Registro de um atendimento individual;
  - Registro de um atendimento de puericultura;
  - Registro de um atendimento de pré-natal;
  - Registro de uma escuta inicial com profissional de nível superior que não seja de odontologia;
  - Registro de um atendimento individual no CDS.

> **Já validado ao vivo (C1, PEC 5.4.37 e 5.5.28):** `co_seq_fat_atd_ind`, `co_dim_municipio`, `co_dim_tempo`, `co_dim_tipo_atendimento`, `co_dim_unidade_saude_1`, `co_dim_equipe_1`, `co_dim_cbo_1`, `nu_uuid_ficha`, `nu_atendimento`; 1 linha = 1 atendimento; `_1` = participante principal, `_2` = segundo participante; dimensões usam linha-sentinela (id 1) em vez de NULL; `dt_registro` = data local (America/Sao_Paulo) de `dt_inicial_atendimento`. Todo o resto desta tabela é **somente documental**.
>
> Colunas citadas na doc mas **ausentes da tabela de colunas**: `st_emulti_aval_diagnostico`, `st_emulti_proce_clin_terap`, `st_emulti_presc_terapeutica` (substitutos dos `st_nasf_*` "a partir da versão 5.5.0 do LEDI"). Colunas M-CHAT (`co_mchat_*`, `nu_mchat_*`, `st_mchat_preenchido`) e `st_conduta_agendamento_emulti` não aparecem em `principais_alteracoes` — presença em 5.4.37/5.5.28 desconhecida.

| Seção da doc | Coluna | LEDI | Descrição (doc oficial) | Marcação |
|---|---|---|---|---|
| Metadados | `co_seq_fat_atd_ind` | — | Código de identificação sequencial criado automaticamente pelo sistema | PK |
| Metadados | `co_fat_cidadao_pec` | — | Campo `co_seq_fat_cidadao_pec` da `tb_fat_cidadao_pec` | FK → `tb_fat_cidadao_pec.co_seq_fat_cidadao_pec` **chave de pessoa** (alvo sem página na doc) |
| Metadados | `nu_prontuario` | numeroProntuario | Número do prontuário criptografado | **[PII — nunca projetar]** |
| Metadados | `nu_uuid_ficha` | uuidFicha | Identificador universalmente único do registro |  |
| Metadados | `nu_uuid_dado_transp` | uuidDadoSerializado | Identificador universalmente único da camada de transporte de dados. Caso o registro seja gerado dentro do PEC terá o mesmo valor do campo nu_uuid_ficha |  |
| Metadados | `nu_atendimento` | — | Uma ficha pode conter mais de um atendimento esse campo é utilizado para ordenar os atendimentos dentro um mesmo envio |  |
| Metadados | `nu_cpf_cidadao` | cpfCidadao | CPF do cidadão | **[PII — nunca projetar]** |
| Metadados | `nu_cns` | cnsCidadao | CNS do cidadão | **[PII — nunca projetar]** |
| Metadados | `st_nao_possui_cpf` | stCidadaoNaoPossuiCpf | Indica se o cidadão não possui CPF |  |
| Métricas | `dt_nascimento` | dataNascimento | Data de nascimento do cidadão | *[sensível — projetar só na capacidade "cidadão"]* |
| Métricas | `nu_medicao_circ_abdominal` | circunferenciaAbdominal | Circunferência abdominal do cidadão em centímetros |  |
| Métricas | `nu_medicao_perim_pantrlha` | perimetroPanturrilha | Perímetro da panturrilha do cidadão em centímetros |  |
| Métricas | `nu_medicao_pressao_sistolica` | pressaoArterialSistolica | Pressão arterial sistólica do cidadão em mmHg |  |
| Métricas | `nu_medicao_pressao_diastolica` | pressaoArterialDiastolica | Pressão arterial diastólica do cidadão em mmHg |  |
| Métricas | `nu_medicao_freq_respiratoria` | frequenciaRespiratoria | Frequência respiratória do cidadão em MPM |  |
| Métricas | `nu_medicao_freq_cardiaca` | frequenciaCardiaca | Frequência cardíaca do cidadão em BPM |  |
| Métricas | `nu_medicao_temperatura` | temperatura | Temperatura do cidadão em ºC |  |
| Métricas | `nu_medicao_saturacao_o2` | saturacaoO2 | Saturação de oxigênio do cidadão em percentual |  |
| Métricas | `nu_medicao_glicemia` | glicemiaCapilar | Glicemia capilar do cidadão em mg/dL |  |
| Métricas | `nu_peso` | peso | Peso do cidadão em quilogramas |  |
| Métricas | `nu_altura` | altura | Altura do cidadão em centímetros |  |
| Métricas | `nu_perimetro_cefalico` | perimetroCefalico | Perímetro cefálico do cidadão em centímetros |  |
| Métricas | `st_vacinacao_em_dia` | vacinaEmDia | Status que indica se a vacinação do cidadão está em dia |  |
| Métricas | `st_gravidez_planejada` | stGravidezPlanejada | Status que indica se a gravidez é planejada |  |
| Métricas | `nu_idade_gestacional_semanas` | idadeGestacional | Idade gestacional em semanas |  |
| Métricas | `nu_gestas_previas` | nuGestasPrevias | Número de gestações prévias |  |
| Métricas | `nu_partos` | nuPartos | Número de partos que a mulher já teve |  |
| Métricas | `st_ficou_em_observacao` | ficouEmObservacao | Status que indica se o cidadão ficou em observação no atendimento |  |
| Métricas | `st_conduta_manter_observacao` | condutas | Status que indica a conduta automática de manter o cidadão em observação |  |
| Métricas | `st_conduta_consulta_agendada` | condutas | Status que indica a conduta "Retorno para consulta agendada" |  |
| Métricas | `st_conduta_cuidd_conti_program` | condutas | Status que indica a conduta "Retorno para consulta programada / cuidado continuado" |  |
| Métricas | `st_conduta_agendamento_grupos` | condutas | Status que indica a conduta "Agendamento para grupos" |  |
| Métricas | `st_conduta_agendamento_nasf` | condutas | Status que indica a conduta "Agendamento para eMulti" |  |
| Métricas | `st_conduta_alta_episodio` | condutas | Status que indica a conduta "Alta do episódio" |  |
| Métricas | `st_encaminhamento_serv_special` | Encaminhamentos | Status que indica o encaminhamento para "Serviço especializado" |  |
| Métricas | `st_encaminhamento_caps` | Encaminhamentos | Status que indica o encaminhamento para "CAPS" |  |
| Métricas | `st_encaminhamento_intern_hospi` | Encaminhamentos | Status que indica o encaminhamento para "Internação hospitalar" |  |
| Métricas | `st_encaminhamento_urgencia` | Encaminhamentos | Status que indica o encaminhamento para "Urgência" |  |
| Métricas | `st_encaminhamento_servico_ad` | Encaminhamentos | Status que indica o encaminhamento para "Serviço de Atenção Domiciliar" |  |
| Métricas | `st_encaminhamento_intersetoria` | Encaminhamentos | Status que indica o encaminhamento para "Intersetorial" |  |
| Métricas | `st_encaminhamento_interno_dia` | Encaminhamentos | Status que indica o encaminhamento onde cidadão foi reinserido na lista de atendimento |  |
| Métricas | `ds_filtro_cids` | cid10 | Agrupa todas as CID 10 registradas no atendimento |  |
| Métricas | `ds_filtro_ciaps` | ciap | Agrupa todas as CIAP registradas no atendimento |  |
| Métricas | `ds_filtro_proced_avaliados` | exame | Agrupa todos os resultados de exames registrados no atendimento |  |
| Métricas | `ds_filtro_proced_solicitados` | exame | Agrupa todas as solicitações de exames registradas no atendimento |  |
| Métricas | `dt_inicial_atendimento` | dataHoraInicialAtendimento | Data e hora do início do atendimento no formato "YYYY-MM-DD HH:MM:SS.MMM" |  |
| Métricas | `dt_final_atendimento` | dataHoraFinalAtendimento | Data e hora do fim do atendimento no formato "YYYY-MM-DD HH:MM:SS.MMM" |  |
| Métricas | `st_conduta_agendamento_emulti` | condutas | Status que indica a conduta "Agendamento para eMulti" |  |
| Métricas | `st_nasf` | — | Indica se o tipo de atendimento foi preenchido em uma versão que continha a antiga estrutura Núcleo de Apoio a Saúde da Família (NASF) |  |
| Métricas | `st_nasf_avaliacao_diagnostico` | — | Campo legado que indica se a Avaliação/Diagnóstico foi realizada por uma equipe Nasf durante o atendimento, substituído pelo st_emulti_aval_diagnostico a partir da versão 5.5.0 do LEDI |  |
| Métricas | `st_nasf_proce_clin_terapeutico` | — | Campo legado que indica se Procedimentos Clínicos/Terapêuticos foram realizados por uma equipe Nasf durante o atendimento, substituído pelo st_emulti_proce_clin_terap a partir da versão 5.5.0 do LEDI |  |
| Métricas | `st_nasf_prescricao_terapeutica` | — | Campo legado que indica se a Prescrição Terapêutica foi realizada por uma equipe Nasf durante o atendimento, substituído pelo st_emulti_presc_terapeutica a partir da versão 5.5.0 do LEDI |  |
| Métricas | `ds_filtro_ciap_motivo_consulta` | — | Agrupa as CIAPs registradas como motivo de consulta no atendimento |  |
| Métricas | `ds_filtro_ciap_plano` | — | Agrupa as CIAPs registradas no plano do atendimento |  |
| Métricas | `co_mchat_risco` | — | Classificação de risco do M-CHAT (Checklist para Autismo em Crianças) |  |
| Métricas | `co_mchat_rf_risco` | — | Classificação de risco do M-CHAT-RF (Checklist para Autismo em Crianças — Revisado com Seguimento) |  |
| Métricas | `nu_mchat_pontuacao` | — | Pontuação obtida no M-CHAT |  |
| Métricas | `nu_mchat_rf_pontuacao` | — | Pontuação obtida no M-CHAT-RF |  |
| Métricas | `st_mchat_preenchido` | — | Indica se o M-CHAT foi preenchido no atendimento |  |
| Dim. específica | `co_dim_racionalidade_saude` | — | Código da racionalidade em saúde adotada. Campo `co_seq_dim_racionalidade_saude` da `tb_dim_racionalidade_saude` | FK → `tb_dim_racionalidade_saude.co_seq_dim_racionalidade_saude` |
| Dim. específica | `co_dim_faixa_etaria` | — | Código da faixa etária. Campo `co_seq_dim_faixa_etaria` da `tb_dim_faixa_etaria` | FK → `tb_dim_faixa_etaria.co_seq_dim_faixa_etaria` |
| Dim. específica | `co_dim_sexo` | — | Código do sexo. Campo `co_seq_dim_faixa_sexo` da `tb_dim_sexo` | FK → `tb_dim_sexo.co_seq_dim_faixa_sexo` ⚠ PK real da dimensão é `co_seq_dim_sexo` |
| Dim. específica | `co_dim_turno` | — | Código do turno do atendimento. Campo `co_seq_dim_turno` da `tb_dim_turno` | FK → `tb_dim_turno.co_seq_dim_turno` |
| Dim. específica | `co_dim_local_atendimento` | — | Código do local de atendimento. Campo `co_seq_dim_local_atendimento` da `tb_dim_local_atendimento` | FK → `tb_dim_local_atendimento.co_seq_dim_local_atendimento` |
| Dim. específica | `co_dim_tipo_atendimento` | — | Código do tipo de atendimento. Campo `co_seq_dim_tipo_atendimento` da `tb_dim_tipo_atendimento` | FK → `tb_dim_tipo_atendimento.co_seq_dim_tipo_atendimento` |
| Dim. específica | `co_dim_aleitamento` | — | Código do tipo de aleitamento. Campo `co_seq_dim_faixa_aleitamento` da `tb_dim_aleitamento` | FK → `tb_dim_aleitamento.co_seq_dim_faixa_aleitamento` |
| Dim. específica | `co_dim_tempo_dum` | — | Código da DUM. Campo `co_seq_dim_tempo_dum` da `tb_dim_tempo_dum` | FK → `tb_dim_tempo_dum.co_seq_dim_tempo_dum` ⚠ dimensão-alvo sem página na doc |
| Dim. específica | `co_dim_modalidade_ad` | — | Código da modalidade AD. Campo `co_seq_dim_modalidade_ad` da `tb_dim_modalidade_ad` | FK → `tb_dim_modalidade_ad.co_seq_dim_modalidade_ad` |
| Dim. específica | `co_dim_prof_finalizador_obs` | — | Código do profissional finalizador do atendimento de observação. Campo `co_seq_dim_prof_finalizador_obs` da `tb_dim_prof_finalizador_obs` | FK → `tb_dim_prof_finalizador_obs.co_seq_dim_prof_finalizador_obs` |
| Dim. específica | `co_dim_cbo_finalizador_obs` | — | Código do CBO do profissional finalizador do atendimento de observação. Campo `co_seq_dim_cbo_finalizador_obs` da `tb_dim_cbo_finalizador_obs` | FK → `tb_dim_cbo_finalizador_obs.co_seq_dim_cbo_finalizador_obs` |
| Dim. específica | `co_dim_ubs_finalizador_obs` | — | Código do unidade de saúde do profissional finalizador do atendimento de observação. Campo `co_seq_dim_ubs_finalizador_obs` da `tb_dim_ubs_finalizador_obs` | FK → `tb_dim_ubs_finalizador_obs.co_seq_dim_ubs_finalizador_obs` |
| Dim. específica | `co_dim_equipe_finalizador_obs` | — | Código da equipe do profissional finalizador do atendimento de observação. Campo `co_seq_dim_equipe_finalizador_obs` da `tb_dim_equipe_finalizador_obs` | FK → `tb_dim_equipe_finalizador_obs.co_seq_dim_equipe_finalizador_obs` |
| Dim. específica | `co_dim_tp_particip_cidadao` | — | Código do tipo de participação do cidadão. Campo `co_seq_dim_tp_particip_cidadao` da `tb_dim_tp_particip_cidadao` | FK → `tb_dim_tp_particip_cidadao.co_seq_dim_tp_particip_cidadao` ⚠ dimensão-alvo sem página na doc |
| Dim. específica | `co_dim_tp_particip_prof_conv` | — | Código do tipo de participação do profissional convidado. Campo `co_seq_dim_tp_particip_prof_conv` da `tb_dim_tp_particip_prof_conv` | FK → `tb_dim_tp_particip_prof_conv.co_seq_dim_tp_particip_prof_conv` ⚠ dimensão-alvo sem página na doc |
| Dim. específica | `co_dim_tipo_glicemia` | — | Código do tipo de glicemia. Campo `co_seq_dim_tipo_glicemia` da `tb_dim_tipo_glicemia` | FK → `tb_dim_tipo_glicemia.co_seq_dim_tipo_glicemia` |
| Dim. específica | `co_dim_just_nao_possui_cpf` | — | Código da justificativa de não possuir CPF. Campo `co_seq_dim_just_nao_possui_cpf` da `tb_dim_just_nao_possui_cpf` | FK → `tb_dim_just_nao_possui_cpf.co_seq_dim_just_nao_possui_cpf` |
| Dim. comum ao grupo | `co_dim_municipio` | — | Código de identificação do município. Campo `co_seq_dim_municipio` da `tb_dim_municipio` | FK → `tb_dim_municipio.co_seq_dim_municipio` **recorte municipal** |
| Dim. comum ao grupo | `co_dim_tipo_ficha` | — | Código de identificação do tipo de ficha. Campo `co_seq_dim_tipo_ficha` da `tb_dim_tipo_ficha` | FK → `tb_dim_tipo_ficha.co_seq_dim_tipo_ficha` |
| Dim. comum ao grupo | `co_dim_profissional_1` | — | Código de identificação do profissional responsável. Campo `co_seq_dim_profissional` da `tb_dim_profissional` | FK → `tb_dim_profissional.co_seq_dim_profissional` |
| Dim. comum ao grupo | `co_dim_profissional_2` | — | Código de identificação do profissional auxiliar. Campo `co_seq_dim_profissional` da `tb_dim_profissional` | FK → `tb_dim_profissional.co_seq_dim_profissional` |
| Dim. comum ao grupo | `co_dim_cbo_1` | — | Código de identificação do CBO profissional responsável. Campo `co_seq_dim_cbo` da `tb_dim_cbo` | FK → `tb_dim_cbo.co_seq_dim_cbo` |
| Dim. comum ao grupo | `co_dim_cbo_2` | — | Código de identificação do CBO profissional auxiliar. Campo `co_seq_dim_cbo` da `tb_dim_cbo` | FK → `tb_dim_cbo.co_seq_dim_cbo` |
| Dim. comum ao grupo | `co_dim_unidade_saude_1` | — | Código de identificação da unidade de saúde do profissional responsável. Campo `co_seq_dim_unidade_saude` da `tb_dim_unidade_saude` | FK → `tb_dim_unidade_saude.co_seq_dim_unidade_saude` |
| Dim. comum ao grupo | `co_dim_unidade_saude_2` | — | Código de identificação da unidade de saúde do profissional auxiliar. Campo `co_seq_dim_unidade_saude` da `tb_dim_unidade_saude` | FK → `tb_dim_unidade_saude.co_seq_dim_unidade_saude` |
| Dim. comum ao grupo | `co_dim_equipe_1` | — | Código de identificação da equipe do profissional responsável. Campo `co_seq_dim_equipe` da `tb_dim_equipe` | FK → `tb_dim_equipe.co_seq_dim_equipe` |
| Dim. comum ao grupo | `co_dim_equipe_2` | — | Código de identificação da equipe do profissional auxiliar. Campo `co_seq_dim_equipe` da `tb_dim_equipe` | FK → `tb_dim_equipe.co_seq_dim_equipe` |
| Dim. comum ao grupo | `co_dim_tipo_origem_dado_transp` | — | Código da origem do dado no transporte. Campo `co_seq_dim_tp_orgm_dado_transp` da `tb_dim_tipo_origem_dado_transp` | FK → `tb_dim_tipo_origem_dado_transp.co_seq_dim_tp_orgm_dado_transp` |
| Dim. comum ao grupo | `co_dim_cds_tipo_origem` | — | Código da origem do dado no transporte. Campo `co_seq_dim_tipo_origem` da `tb_dim_tipo_origem` | FK → `tb_dim_tipo_origem.co_seq_dim_tipo_origem` |
| Dim. comum ao grupo | `co_dim_tempo` | — | Responsável por armazenar as datas de maneira estruturada. Campo `co_seq_dim_tempo` da `tb_dim_tempo` | FK → `tb_dim_tempo.co_seq_dim_tempo` **data do registro** |

#### `tb_fat_atd_ind_problemas` — Tabela fato dos problemas e condições do atendimento individual

Fonte: <https://integracao.esusaps.bridge.ufsc.tech/dw/fatos/atendimento_individual/tb_fat_atd_ind_problemas.html> — **Alterado em 13/07/2026**.

Texto da doc (objetivo/regras/notas):

- A tabela `tb_fat_atd_ind_problemas` só é preenchida quando são processados registros de problemas e/ou condições em um atendimento individual e o atendimento em questão é processado.

> Colunas de evolução (`nu_uuid_problema`, `co_unico_evolucao`, `co_sequencial_evolucao`, `co_dim_situacao`, `co_dim_data_inicio_problema`, `co_dim_data_fim_problema`, `st_avaliado`) entraram na v5.3.14→5.3.15. LEDI FAI: `dataFimProblema` é obrigatória quando `situacao` = 2 (Resolvido). A tabela lista `co_dim_tempo` duas vezes (específica e comum) — é a mesma coluna.

| Seção da doc | Coluna | LEDI | Descrição (doc oficial) | Marcação |
|---|---|---|---|---|
| Metadados | `co_seq_fat_atend_ind_problemas` | — | Código de identificação sequencial dos problemas ou condições registrados | PK |
| Metadados | `co_fat_atd_ind` | — | Código de identificação sequencial do atendimento individual. Campo `co_seq_fat_atd_ind` da `tb_fat_atendimento_individual` | FK → `tb_fat_atendimento_individual.co_seq_fat_atd_ind` |
| Metadados | `co_fat_cidadao_pec` | — | Campo `co_seq_fat_cidadao_pec` da `tb_fat_cidadao_pec` | FK → `tb_fat_cidadao_pec.co_seq_fat_cidadao_pec` **chave de pessoa** (alvo sem página na doc) |
| Metadados | `nu_cpf_cidadao` | cpfCidadao | CPF do cidadão | **[PII — nunca projetar]** |
| Metadados | `nu_cns` | cnsCidadao | CNS do cidadão | **[PII — nunca projetar]** |
| Metadados | `nu_uuid_ficha` | uuidFicha | Identificador universalmente único da camada de transporte de dados |  |
| Metadados | `nu_uuid_dado_transp` | — | Identificador universalmente único da camada de transporte de dados. Caso o registro seja gerado dentro do PEC terá o mesmo valor do campo nu_uuid_ficha |  |
| Metadados | `nu_atendimento` | — | Uma ficha pode conter mais de um atendimento esse campo é utilizado para ordenar os atendimentos dentro um mesmo envio |  |
| Metadados | `nu_uuid_problema` | uuidProblema | Código identificador único do problema ou condição |  |
| Metadados | `co_unico_evolucao` | uuidEvolucaoProblema | Código identificador único da evolução do problema ou condição |  |
| Metadados | `co_sequencial_evolucao` | coSequencialEvolucao | Código sequencial da evolução dentro do próprio problema e condição atual |  |
| Métricas | `st_avaliado` | isAvaliado | Indica se o problema ou condição foi avaliado durante o atendimento |  |
| Dim. específica | `co_dim_tempo` | — | Responsável por armazenar as datas de maneira estruturada. Campo `co_seq_dim_tempo` da `tb_dim_tempo` | FK → `tb_dim_tempo.co_seq_dim_tempo` **data do registro** |
| Dim. específica | `co_dim_cid` | — | Código da CID do problema ou condição. Campo `co_seq_dim_cid` da `tb_dim_cid` | FK → `tb_dim_cid.co_seq_dim_cid` ⚠ ver divergência `tb_dim_cid` × `tb_dim_cid10` |
| Dim. específica | `co_dim_ciap` | — | Código da CIAP do problema ou condição. Campo `co_seq_dim_ciap` da `tb_dim_ciap` | FK → `tb_dim_ciap.co_seq_dim_ciap` ⚠ ver divergência `tb_dim_ciap` × `tb_dim_ciap2` |
| Dim. específica | `co_dim_situacao` | — | Código da situação do problema ou condição. Campo `co_seq_dim_situacao` da `tb_dim_situacao_problema` | FK → `tb_dim_situacao_problema.co_seq_dim_situacao` |
| Dim. específica | `co_dim_data_inicio_problema` | — | Responsável por armazenar as datas de maneira estruturada. Campo `co_seq_dim_tempo` da `tb_dim_tempo` | FK → `tb_dim_tempo.co_seq_dim_tempo` |
| Dim. específica | `co_dim_data_fim_problema` | — | Responsável por armazenar as datas de maneira estruturada. Campo `co_seq_dim_tempo` da `tb_dim_tempo` | FK → `tb_dim_tempo.co_seq_dim_tempo` |
| Dim. comum ao grupo | `co_dim_municipio` | — | Código de identificação do município. Campo `co_seq_dim_municipio` da `tb_dim_municipio` | FK → `tb_dim_municipio.co_seq_dim_municipio` **recorte municipal** |
| Dim. comum ao grupo | `co_dim_tipo_ficha` | — | Código de identificação do tipo de ficha. Campo `co_seq_dim_tipo_ficha` da `tb_dim_tipo_ficha` | FK → `tb_dim_tipo_ficha.co_seq_dim_tipo_ficha` |
| Dim. comum ao grupo | `co_dim_profissional_1` | — | Código de identificação do profissional responsável. Campo `co_seq_dim_profissional` da `tb_dim_profissional` | FK → `tb_dim_profissional.co_seq_dim_profissional` |
| Dim. comum ao grupo | `co_dim_profissional_2` | — | Código de identificação do profissional auxiliar. Campo `co_seq_dim_profissional` da `tb_dim_profissional` | FK → `tb_dim_profissional.co_seq_dim_profissional` |
| Dim. comum ao grupo | `co_dim_cbo_1` | — | Código de identificação do CBO profissional responsável. Campo `co_seq_dim_cbo` da `tb_dim_cbo` | FK → `tb_dim_cbo.co_seq_dim_cbo` |
| Dim. comum ao grupo | `co_dim_cbo_2` | — | Código de identificação do CBO profissional auxiliar. Campo `co_seq_dim_cbo` da `tb_dim_cbo` | FK → `tb_dim_cbo.co_seq_dim_cbo` |
| Dim. comum ao grupo | `co_dim_unidade_saude_1` | — | Código de identificação da unidade de saúde do profissional responsável. Campo `co_seq_dim_unidade_saude` da `tb_dim_unidade_saude` | FK → `tb_dim_unidade_saude.co_seq_dim_unidade_saude` |
| Dim. comum ao grupo | `co_dim_unidade_saude_2` | — | Código de identificação da unidade de saúde do profissional auxiliar. Campo `co_seq_dim_unidade_saude` da `tb_dim_unidade_saude` | FK → `tb_dim_unidade_saude.co_seq_dim_unidade_saude` |
| Dim. comum ao grupo | `co_dim_equipe_1` | — | Código de identificação da equipe do profissional responsável. Campo `co_seq_dim_equipe` da `tb_dim_equipe` | FK → `tb_dim_equipe.co_seq_dim_equipe` |
| Dim. comum ao grupo | `co_dim_equipe_2` | — | Código de identificação da equipe do profissional auxiliar. Campo `co_seq_dim_equipe` da `tb_dim_equipe` | FK → `tb_dim_equipe.co_seq_dim_equipe` |
| Dim. comum ao grupo | `co_dim_tipo_origem_dado_transp` | — | Código da origem do dado no transporte. Campo `co_seq_dim_tp_orgm_dado_transp` da `tb_dim_tipo_origem_dado_transp` | FK → `tb_dim_tipo_origem_dado_transp.co_seq_dim_tp_orgm_dado_transp` |
| Dim. comum ao grupo | `co_dim_cds_tipo_origem` | — | Código da origem do dado no transporte. Campo `co_seq_dim_tipo_origem` da `tb_dim_tipo_origem` | FK → `tb_dim_tipo_origem.co_seq_dim_tipo_origem` |
| Dim. comum ao grupo | `co_dim_tempo` | — | Responsável por armazenar as datas de maneira estruturada. Campo `co_seq_dim_tempo` da `tb_dim_tempo` | FK → `tb_dim_tempo.co_seq_dim_tempo` **data do registro** |

#### `tb_fat_atd_ind_procedimentos` — Tabela fato dos procedimentos do atendimento individual

Fonte: <https://integracao.esusaps.bridge.ufsc.tech/dw/fatos/atendimento_individual/tb_fat_atd_ind_procedimentos.html> — **Alterado em 13/07/2026**.

Texto da doc (objetivo/regras/notas):

- A tabela `tb_fat_atd_ind_procedimentos` só é preenchida quando são processados registros de resultado de exame ou de solicitação de exames em um atendimento individual e o atendimento em questão é processado.

> **Atenção ao nome:** apesar de "procedimentos", a doc diz que esta tabela só é preenchida com **resultado de exame ou solicitação de exames** do atendimento individual (colunas `_solicitado`/`_avaliado`). Procedimentos **realizados** num atendimento do PEC vão para os fatos de Procedimentos (regra 1 de `tb_fat_procedimento`). Colunas `co_dim_faixa_etaria`, `dt_inicial_atendimento`, `co_dim_tipo_atendimento`, `co_dim_turno`, `co_dim_sexo` entraram na v5.4.0→5.4.1 (presentes nas duas instalações).

| Seção da doc | Coluna | LEDI | Descrição (doc oficial) | Marcação |
|---|---|---|---|---|
| Metadados | `co_seq_fat_atend_ind_proced` | — | Código de identificação sequencial dos resultado de exame registrados | PK |
| Metadados | `co_fat_atd_ind` | — | Código de identificação sequencial do atendimento individual. Campo `co_seq_fat_atd_ind` da `tb_fat_atendimento_individual` | FK → `tb_fat_atendimento_individual.co_seq_fat_atd_ind` |
| Metadados | `co_fat_cidadao_pec` | — | Campo `co_seq_fat_cidadao_pec` da `tb_fat_cidadao_pec` | FK → `tb_fat_cidadao_pec.co_seq_fat_cidadao_pec` **chave de pessoa** (alvo sem página na doc) |
| Metadados | `nu_uuid_ficha` | uuidFicha | Identificador universalmente único da camada de transporte de dados |  |
| Metadados | `nu_uuid_dado_transp` | — | Identificador universalmente único da camada de transporte de dados. Caso o registro seja gerado dentro do PEC terá o mesmo valor do campo nu_uuid_ficha |  |
| Metadados | `nu_atendimento` | — | Uma ficha pode conter mais de um atendimento esse campo é utilizado para ordenar os atendimentos dentro um mesmo envio |  |
| Metadados | `nu_cpf_cidadao` | cpfCidadao | CPF do cidadão | **[PII — nunca projetar]** |
| Metadados | `nu_cns` | cnsCidadao | CNS do cidadão | **[PII — nunca projetar]** |
| Métricas | `dt_inicial_atendimento` | dataHoraInicialAtendimento | Data e hora do início do atendimento no formato "YYYY-MM-DD HH:MM:SS.MMM" |  |
| Dim. específica | `co_dim_procedimento_avaliado` | — | Código SIGTAP do procedimento avaliado. Campo `co_seq_dim_procedimento` da `tb_dim_procedimento` | FK → `tb_dim_procedimento.co_seq_dim_procedimento` |
| Dim. específica | `co_dim_procedimento_solicitado` | — | Código SIGTAP do procedimento solicitado. Campo `co_seq_dim_procedimento` da `tb_dim_procedimento` | FK → `tb_dim_procedimento.co_seq_dim_procedimento` |
| Dim. específica | `co_dim_sexo` | — | Código do sexo. Campo `co_seq_dim_faixa_sexo` da `tb_dim_sexo` | FK → `tb_dim_sexo.co_seq_dim_faixa_sexo` ⚠ PK real da dimensão é `co_seq_dim_sexo` |
| Dim. específica | `co_dim_turno` | — | Código do turno do atendimento. Campo `co_seq_dim_turno` da `tb_dim_turno` | FK → `tb_dim_turno.co_seq_dim_turno` |
| Dim. específica | `co_dim_tipo_atendimento` | — | Código do tipo de atendimento. Campo `co_seq_dim_tipo_atendimento` da `tb_dim_tipo_atendimento` | FK → `tb_dim_tipo_atendimento.co_seq_dim_tipo_atendimento` |
| Dim. específica | `co_dim_faixa_etaria` | — | Código da faixa etária. Campo `co_seq_dim_faixa_etaria` da `tb_dim_faixa_etaria` | FK → `tb_dim_faixa_etaria.co_seq_dim_faixa_etaria` |
| Dim. comum ao grupo | `co_dim_municipio` | — | Código de identificação do município. Campo `co_seq_dim_municipio` da `tb_dim_municipio` | FK → `tb_dim_municipio.co_seq_dim_municipio` **recorte municipal** |
| Dim. comum ao grupo | `co_dim_tipo_ficha` | — | Código de identificação do tipo de ficha. Campo `co_seq_dim_tipo_ficha` da `tb_dim_tipo_ficha` | FK → `tb_dim_tipo_ficha.co_seq_dim_tipo_ficha` |
| Dim. comum ao grupo | `co_dim_profissional_1` | — | Código de identificação do profissional responsável. Campo `co_seq_dim_profissional` da `tb_dim_profissional` | FK → `tb_dim_profissional.co_seq_dim_profissional` |
| Dim. comum ao grupo | `co_dim_profissional_2` | — | Código de identificação do profissional auxiliar. Campo `co_seq_dim_profissional` da `tb_dim_profissional` | FK → `tb_dim_profissional.co_seq_dim_profissional` |
| Dim. comum ao grupo | `co_dim_cbo_1` | — | Código de identificação do CBO profissional responsável. Campo `co_seq_dim_cbo` da `tb_dim_cbo` | FK → `tb_dim_cbo.co_seq_dim_cbo` |
| Dim. comum ao grupo | `co_dim_cbo_2` | — | Código de identificação do CBO profissional auxiliar. Campo `co_seq_dim_cbo` da `tb_dim_cbo` | FK → `tb_dim_cbo.co_seq_dim_cbo` |
| Dim. comum ao grupo | `co_dim_unidade_saude_1` | — | Código de identificação da unidade de saúde do profissional responsável. Campo `co_seq_dim_unidade_saude` da `tb_dim_unidade_saude` | FK → `tb_dim_unidade_saude.co_seq_dim_unidade_saude` |
| Dim. comum ao grupo | `co_dim_unidade_saude_2` | — | Código de identificação da unidade de saúde do profissional auxiliar. Campo `co_seq_dim_unidade_saude` da `tb_dim_unidade_saude` | FK → `tb_dim_unidade_saude.co_seq_dim_unidade_saude` |
| Dim. comum ao grupo | `co_dim_equipe_1` | — | Código de identificação da equipe do profissional responsável. Campo `co_seq_dim_equipe` da `tb_dim_equipe` | FK → `tb_dim_equipe.co_seq_dim_equipe` |
| Dim. comum ao grupo | `co_dim_equipe_2` | — | Código de identificação da equipe do profissional auxiliar. Campo `co_seq_dim_equipe` da `tb_dim_equipe` | FK → `tb_dim_equipe.co_seq_dim_equipe` |
| Dim. comum ao grupo | `co_dim_tipo_origem_dado_transp` | — | Código da origem do dado no transporte. Campo `co_seq_dim_tp_orgm_dado_transp` da `tb_dim_tipo_origem_dado_transp` | FK → `tb_dim_tipo_origem_dado_transp.co_seq_dim_tp_orgm_dado_transp` |
| Dim. comum ao grupo | `co_dim_cds_tipo_origem` | — | Código da origem do dado no transporte. Campo `co_seq_dim_tipo_origem` da `tb_dim_tipo_origem` | FK → `tb_dim_tipo_origem.co_seq_dim_tipo_origem` |
| Dim. comum ao grupo | `co_dim_tempo` | — | Responsável por armazenar as datas de maneira estruturada. Campo `co_seq_dim_tempo` da `tb_dim_tempo` | FK → `tb_dim_tempo.co_seq_dim_tempo` **data do registro** |

#### `tb_fat_atd_ind_exames` — Tabela fato dos exames do atendimento individual

Fonte: <https://integracao.esusaps.bridge.ufsc.tech/dw/fatos/atendimento_individual/tb_fat_atd_ind_exames.html> — **Alterado em 13/07/2026**.

Texto da doc (objetivo/regras/notas):

- A tabela `tb_fat_atd_ind_exames` só é preenchida quando são feitos registros de resultado de exame estruturados em um atendimento individual e o atendimento em questão é processado.
- 1. A `tb_fat_atd_ind_exames` é preenchida de acordo com as regras.

| Seção da doc | Coluna | LEDI | Descrição (doc oficial) | Marcação |
|---|---|---|---|---|
| Metadados | `co_seq_fat_atd_ind_exames` | — | Código de identificação sequencial dos resultado de exame estruturados registrados | PK |
| Metadados | `co_fat_atd_ind` | — | Código de identificação sequencial do atendimento individual. Campo `co_seq_fat_atd_ind` da `tb_fat_atendimento_individual` | FK → `tb_fat_atendimento_individual.co_seq_fat_atd_ind` |
| Metadados | `co_fat_cidadao_pec` | — | Campo `co_seq_fat_cidadao_pec` da `tb_fat_cidadao_pec` | FK → `tb_fat_cidadao_pec.co_seq_fat_cidadao_pec` **chave de pessoa** (alvo sem página na doc) |
| Metadados | `nu_uuid_ficha` | uuidFicha | Identificador universalmente único da camada de transporte de dados |  |
| Metadados | `nu_uuid_dado_transp` | — | Identificador universalmente único da camada de transporte de dados. Caso o registro seja gerado dentro do PEC terá o mesmo valor do campo nu_uuid_ficha |  |
| Metadados | `nu_atendimento` | — | Uma ficha pode conter mais de um atendimento esse campo é utilizado para ordenar os atendimentos dentro um mesmo envio |  |
| Metadados | `nu_cpf_cidadao` | cpfCidadao | CPF do cidadão | **[PII — nunca projetar]** |
| Metadados | `nu_cns_cidadao` | cnsCidadao | CNS do cidadão | **[PII — nunca projetar]** |
| Métricas | `dt_solicitacao` | dataSolicitacao | Data de solicitação do exame avaliado |  |
| Métricas | `dt_realizacao` | dataRealizacao | Data de realização do exame avaliado |  |
| Métricas | `dt_resultado` | dataResultado | Data de resultado do exame avaliado |  |
| Métricas | `nu_resultado_valor` | resultadoExame | Resultado do exame avaliado |  |
| Métricas | `nu_resultado_dia` | resultadoExame | Campo preenchido apenas para resultados de exames que possuem os seguintes códigos SIGTAP: 0205020143, 0205020151 e 0205010059 |  |
| Métricas | `nu_resultado_semana` | resultadoExame | Campo preenchido apenas para resultados de exames que possuem os seguintes códigos SIGTAP: 0205020143, 0205020151 e 0205010059 |  |
| Métricas | `dt_resultado_data` | resultadoExame | Campo preenchido apenas para resultados de exames que possuem os seguintes códigos SIGTAP: 0205020143, 0205020151 e 0205010059 |  |
| Dim. específica | `co_dim_procedimento` | — | Código SIGTAP dos exames avaliados. Campo `co_seq_dim_procedimento` da `tb_dim_procedimento` | FK → `tb_dim_procedimento.co_seq_dim_procedimento` |
| Dim. comum ao grupo | `co_dim_municipio` | — | Código de identificação do município. Campo `co_seq_dim_municipio` da `tb_dim_municipio` | FK → `tb_dim_municipio.co_seq_dim_municipio` **recorte municipal** |
| Dim. comum ao grupo | `co_dim_tipo_ficha` | — | Código de identificação do tipo de ficha. Campo `co_seq_dim_tipo_ficha` da `tb_dim_tipo_ficha` | FK → `tb_dim_tipo_ficha.co_seq_dim_tipo_ficha` |
| Dim. comum ao grupo | `co_dim_profissional_1` | — | Código de identificação do profissional responsável. Campo `co_seq_dim_profissional` da `tb_dim_profissional` | FK → `tb_dim_profissional.co_seq_dim_profissional` |
| Dim. comum ao grupo | `co_dim_profissional_2` | — | Código de identificação do profissional auxiliar. Campo `co_seq_dim_profissional` da `tb_dim_profissional` | FK → `tb_dim_profissional.co_seq_dim_profissional` |
| Dim. comum ao grupo | `co_dim_cbo_1` | — | Código de identificação do CBO profissional responsável. Campo `co_seq_dim_cbo` da `tb_dim_cbo` | FK → `tb_dim_cbo.co_seq_dim_cbo` |
| Dim. comum ao grupo | `co_dim_cbo_2` | — | Código de identificação do CBO profissional auxiliar. Campo `co_seq_dim_cbo` da `tb_dim_cbo` | FK → `tb_dim_cbo.co_seq_dim_cbo` |
| Dim. comum ao grupo | `co_dim_unidade_saude_1` | — | Código de identificação da unidade de saúde do profissional responsável. Campo `co_seq_dim_unidade_saude` da `tb_dim_unidade_saude` | FK → `tb_dim_unidade_saude.co_seq_dim_unidade_saude` |
| Dim. comum ao grupo | `co_dim_unidade_saude_2` | — | Código de identificação da unidade de saúde do profissional auxiliar. Campo `co_seq_dim_unidade_saude` da `tb_dim_unidade_saude` | FK → `tb_dim_unidade_saude.co_seq_dim_unidade_saude` |
| Dim. comum ao grupo | `co_dim_equipe_1` | — | Código de identificação da equipe do profissional responsável. Campo `co_seq_dim_equipe` da `tb_dim_equipe` | FK → `tb_dim_equipe.co_seq_dim_equipe` |
| Dim. comum ao grupo | `co_dim_equipe_2` | — | Código de identificação da equipe do profissional auxiliar. Campo `co_seq_dim_equipe` da `tb_dim_equipe` | FK → `tb_dim_equipe.co_seq_dim_equipe` |
| Dim. comum ao grupo | `co_dim_tipo_origem_dado_transp` | — | Código da origem do dado no transporte. Campo `co_seq_dim_tp_orgm_dado_transp` da `tb_dim_tipo_origem_dado_transp` | FK → `tb_dim_tipo_origem_dado_transp.co_seq_dim_tp_orgm_dado_transp` |
| Dim. comum ao grupo | `co_dim_cds_tipo_origem` | — | Código da origem do dado no transporte. Campo `co_seq_dim_tipo_origem` da `tb_dim_tipo_origem` | FK → `tb_dim_tipo_origem.co_seq_dim_tipo_origem` |
| Dim. comum ao grupo | `co_dim_tempo` | — | Responsável por armazenar as datas de maneira estruturada. Campo `co_seq_dim_tempo` da `tb_dim_tempo` | FK → `tb_dim_tempo.co_seq_dim_tempo` **data do registro** |

#### `tb_fat_consolidado_cidadao_fai` — Tabela fato com informações consolidadas sobre um cidadão geradas a partir de um atendimento individual

Fonte: <https://integracao.esusaps.bridge.ufsc.tech/dw/fatos/atendimento_individual/tb_fat_consolidado_cidadao_fai.html> — **Alterado em 08/11/2024**.

Texto da doc (objetivo/regras/notas):

- A tabela `tb_fat_consolidado_cidadao_fai` é populada sempre que os dados de um atendimento individual são processados.
- 1. Após o processamento do primeiro atendimento individual de um cidadão é criada uma nova linha na tabela `tb_fat_consolidado_cidadao_fai`. Os próximos atendimentos não criam novas linhas, substituem o registro antigo com as informações mais recentes do cidadão, as colunas só são atualizadas caso existam novos registros.
- 2. Todos as colunas que começam com `co_dim_tempo` estão relacionado a tabela `tb_dim_tempo` através do campo `co_seq_dim_tempo`.

> **Não usar como fonte primária:** 1 linha por cidadão, sobrescrita a cada atendimento (sem histórico), e listada na Tabela 1 de `/dw/fatos/index.html` entre as tabelas de **relatórios operacionais** que "em breve" serão descontinuadas. Útil só como referência das listas CIAP/CID que o próprio PEC usa para HAS/DIA (abaixo) e para conferência cruzada.

Tabela de valores publicada na página (4.1 Relação entre condição de saúde e códigos CIAP2 e CID10):

| Condição | CIAP2 | CID10 |
|---|---|---|
| Hipertensão Arterial (HAS) | - K86 - K87 | - I10 - P292 - K766 - I270 - I272 - que contenha o código I13 - que contenha o código I15 |
| Diabetes (DIA) | - T90 - T89 | - que contenha o código E10 - que contenha o código E11 - que contenha o código E12 - que contenha o código E13 - que contenha o código E14 - que contenha o código O24 - E232 - N083 - N251 - P700 - P702 |
| Tabagismo | P17 | Z720 |
| Obesidade | T82 | que contenha o código E66 |
| AVC | - K89 - K90 - K91 | - G45 - G46 - I60 - que contenha o código I61 - I62 - que contenha o código I63 - I64 - I65 - I66 - que contenha o código I67, exceto I674 - I68 - I69 |
| Infarto | - K75 - K78 | - que contenha o código I21 - que contenha o código I22 - que contenha o código I23 - I241 - I48 |
| Doença Cardíaca | - K74 - K76 - K77 - K79 - K80 - K82 - K83 - K84 - K99 | - I20 - I240 - I249 - I25 - I50 - I47 - I49 - I27 - I28 - I34 - I35 - I36 - I37 - I31 - que contenha o código I42, exceto I424 - I43 - I44 - I45 - I46 - I51 - I52 - O903 - I71 - I72 - I77 - I780 - I788 - I789 - I79 - I85 - I86 - I871 - I879 - I890 - I98 - I99 - M30 - M31 - R57 - T063 |
| Rins | - U70 - U71 - U72 - U75 - U76 - U77 - U78 - U79 - U80 - U85 - U88 - U90 - U95 - U98 - U99 | - N10 - N11 - N12 - N151 - N159 - N30 - N390 - A560 - A562 - A590 - B374 - N34 - C64 - C65 - C67 - C66 - C68 - D30 - D099 - D091 - D41 - S370 - S371 - S372 - S373 - T190 - T191 - T283 - Q60 - Q61 - Q62 - Q63 - Q64 - N00 - N01 - N03 - N04 - N05 - N07 - N08 - N14 - N150 - N158 - N16 - N392 - N20 - N21 - N22 - N391 - R80 - R81 - R82 - N06 - N13 - N17 - N18 - N19 - N25 - N26 - N27 - N28 - N29 - N31 - N32 - N33 - N35 - N36 - N37 - N398 - R392 - T198 - T199 - Z905 - Z906 |
| Rastreamento Risco Cardiovascular | K22 | - Z136 - Z824 |

| Seção da doc | Coluna | LEDI | Descrição (doc oficial) | Marcação |
|---|---|---|---|---|
| Fato | `co_seq_fat_conslddo_ciddo_fai` | — | Código de identificação sequencial dos fatos consolidados de um cidadão | PK |
| Fato | `co_fat_cidadao_pec` | — | Código de identificação sequencial do cidadão. Campo `co_seq_fat_cidadao_pec` da `tb_fat_cidadao_pec` | FK → `tb_fat_cidadao_pec.co_seq_fat_cidadao_pec` **chave de pessoa** (alvo sem página na doc) |
| Fato | `nu_altura` | — | Armazena o valor mais atualizado sobre a altura do cidadão |  |
| Fato | `nu_peso` | — | Armazena o valor mais atualizado sobre a peso do cidadão |  |
| Fato | `nu_perimetro_cefalico_prcltra` | — | Somente armazena um valor quando é feito medição de perimêtro cefálico nos campos de "Antropometria, sinais vitais e glicemia capilar" dentro de um atendimento de puericultura |  |
| Fato | `nu_altura_prcltra` | — | Somente armazena um valor quando é feito medição de altura nos campos de "Antropometria, sinais vitais e glicemia capilar" dentro de um atendimento de puericultura |  |
| Fato | `nu_peso_prcltra` | — | Somente armazena um valor quando é feito medição de peso nos campos de "Antropometria, sinais vitais e glicemia capilar" dentro de um atendimento de puericultura |  |
| Fato | `st_vacinacao_em_dia_prcltra` | — | Armazena o valor mais atualizado sobre o campo "Vacinação em dia?" |  |
| Fato | `st_teste_pezinho` | — | Armazena o valor mais atualizado do resultado de exame "0202110052 - DOSAGEM DE FENILALANINA E TSH OU T4" |  |
| Fato | `st_teste_orelhinha` | — | Armazena o valor mais atualizado do resultado de exame "0211070149 - EMISSOES OTOACUSTICAS EVOCADAS P/ TRIAGEM AUDITIVA (TESTE DA ORELHINHA)" |  |
| Fato | `st_teste_olhinho` | — | Armazena o valor mais atualizado do resultado de exame "ABEX022 - TESTE DO OLHINHO (TRV)" |  |
| Fato | `st_risco_cardio` | — | Armazena o valor mais atualizado do resultado de exame "ABEX022 - TESTE DO OLHINHO (TRV)" | ⚠ descrição copiada de outro campo na doc |
| Dimensão | `co_dim_tempo_ultima_ficha` | — | Responsável por armazenar as datas de processamento da última ficha de maneira estruturada |  |
| Dimensão | `co_dim_tempo_has` | — | Responsável por armazer a data do último atendimento onde houve registro de CID10 ou CIAP2 relacionado a hipertensão arterial (HAS) |  |
| Dimensão | `co_dim_tempo_diabetes` | — | Responsável por armazer a data do último atendimento onde houve registro de CID10 ou CIAP2 relacionado a diabetes (DIA) |  |
| Dimensão | `co_dim_tempo_tabagismo` | — | Responsável por armazer a data do último atendimento onde houve registro de CID10 ou CIAP2 relacionado a tabagismo |  |
| Dimensão | `co_dim_tempo_obesidade` | — | Responsável por armazer a data do último atendimento onde houve registro de CID10 ou CIAP2 relacionado a obesidade |  |
| Dimensão | `co_dim_tempo_avc` | — | Responsável por armazer a data do último atendimento onde houve registro de CID10 ou CIAP2 relacionado a acidente vascular cerebral (AVC) |  |
| Dimensão | `co_dim_tempo_infarto` | — | Responsável por armazer a data do último atendimento onde houve registro de CID10 ou CIAP2 relacionado a infarto |  |
| Dimensão | `co_dim_tempo_doenca_cardiaca` | — | Responsável por armazer a data do último atendimento onde houve registro de CID10 ou CIAP2 relacionado a doença cardíaca |  |
| Dimensão | `co_dim_tempo_problema_rins` | — | Responsável por armazer a data do último atendimento onde houve registro de CID10 ou CIAP2 relacionado a algum problema no rim |  |
| Dimensão | `co_dim_tempo_rastr_rsco_crdo` | — | Responsável por armazer a data do último atendimento onde houve registro de CID10 ou CIAP2 relacionado a rastreamento risco cardiovascular |  |
| Dimensão | `co_dim_tempo_consulta_purperio` | — | Responsável por armazer a data do último atendimento de puerpério |  |
| Dimensão | `co_dim_tempo_consulta_prcltra` | — | Responsável por armazer a data do último atendimento de puericultura |  |
| Dimensão | `co_dim_tempo_cnslta_1_prcltra` | — | Responsável por armazer a data do primeiro atendimento de puericultura |  |
| Dimensão | `co_dim_aleitamento_prcltra` | — | Código do tipo de aleitamento. Campo `co_seq_dim_faixa_aleitamento` da `tb_dim_aleitamento` | FK → `tb_dim_aleitamento.co_seq_dim_faixa_aleitamento` |

#### `tb_fat_cnslddo_ciddo_fai_cid` — Tabela fato com informações consolidadas de CID10 de um cidadão geradas a partir de um atendimento individual

Fonte: <https://integracao.esusaps.bridge.ufsc.tech/dw/fatos/atendimento_individual/tb_fat_cnslddo_ciddo_fai_cid.html> — **Alterado em 13/07/2026**.

Texto da doc (objetivo/regras/notas):

- A `tb_fat_cnslddo_ciddo_fai_cid` só é preenchida quando são são processados registros de CID10 feitos durante um atendimento individual e o atendimento em questão é processado.

> Relatório operacional (Tabela 1 do índice de fatos) — mesma ressalva de descontinuação. Não usar.

| Seção da doc | Coluna | LEDI | Descrição (doc oficial) | Marcação |
|---|---|---|---|---|
| Fato | `co_seq_fat_conslddo_ciddo_fai` | — | Código de identificação sequencial dos fatos consolidados de CID10 de um cidadão | PK |
| Fato | `co_fat_cidadao_pec` | — | Código de identificação sequencial do cidadão. Campo `co_seq_fat_cidadao_pec` da `tb_fat_cidadao_pec` | FK → `tb_fat_cidadao_pec.co_seq_fat_cidadao_pec` **chave de pessoa** (alvo sem página na doc) |
| Dimensão | `co_dim_cid` | — | Código criado para cada CID10 registrado para o cidadão. Campo `co_seq_dim_cid` da `tb_dim_cid` | FK → `tb_dim_cid.co_seq_dim_cid` |


### 2.2 Procedimentos (MIP)

#### `tb_fat_procedimento` — Tabela fato dos procedimentos (individualizados e consolidados)

Fonte: <https://integracao.esusaps.bridge.ufsc.tech/dw/fatos/procedimentos/tb_fat_procedimento.html> — **Alterado em 13/07/2026**.

Texto da doc (objetivo/regras/notas):

- A tabela `tb_fat_procedimento` é populada sempre que os dados de um procedimento são processados. Os dados processados nessa tabela podem se referir tanto a procedimentos individualizados (quando há referência a um cidadão), quanto a procedimentos consolidados (quando não há referência a um cidadão).
- 1. A `tb_fat_procedimento` é preenchida quando são processado(a)s:
  - Fichas de procedimento recebidas através da importação de sistemas terceiros ou outras instalações do PEC e-SUS-APS;
  - Registro de um atendimento que possua ao menos um procedimento SIGTAP ou AB no Plano ou na Finalização do atendimento;
  - Registro de uma escuta inicial com profissional de nível médio que não seja de odontologia;
  - Registro de ao menos um procedimento individualizado no CDS;
  - Registro de ao menos um procedimento consolidado no CDS.
- Essa tabela não possui dimensões específicas.

> Cabeçalho da ficha de procedimentos, **individualizada e consolidada**. Não tem `co_fat_cidadao_pec`: contadores `nr_proc_consdd_*` são produção agregada (sem cidadão) e não servem para práticas por pessoa. A regra 1 inclui "atendimento que possua ao menos um procedimento SIGTAP ou AB no Plano ou na Finalização" e "escuta inicial com profissional de nível médio" — por isso medidas e procedimentos de atendimentos do PEC também aparecem aqui.

| Seção da doc | Coluna | LEDI | Descrição (doc oficial) | Marcação |
|---|---|---|---|---|
| Metadados | `co_seq_fat_procedimento` | — | Código de identificação sequencial criado automaticamente pelo sistema | PK |
| Metadados | `nu_uuid_ficha` | uuidFicha | Identificador universalmente único do registro |  |
| Metadados | `nu_uuid_dado_transp` | — | Identificador universalmente único da camada de transporte de dados. Caso o registro seja gerado dentro do PEC terá o mesmo valor do campo nu_uuid_ficha |  |
| Métricas | `nr_proc_consdd_pressao_arteria` | numTotalAfericaoPa | Quantidade de aferições de pressão realizadas |  |
| Métricas | `nr_proc_consdd_glicemia_capila` | numTotalGlicemiaCapilar | Quantidade de aferições de glicemia capilar realizadas |  |
| Métricas | `nr_proc_consdd_temperatura` | numTotalAfericaoTemperatura | Quantidade de aferições de temperatura realizadas |  |
| Métricas | `nr_proc_consdd_medicao_altura` | numTotalMedicaoAltura | Quantidade de aferições de altura realizadas |  |
| Métricas | `nr_proc_consdd_curativo_simple` | numTotalCurativoSimples | Quantidade de curativos simples realizados |  |
| Métricas | `nr_proc_consdd_medicao_peso` | numTotalMedicaoPeso | Quantidade de aferições de peso realizadas |  |
| Métricas | `nr_proc_consdd_mate_exame_labo` | numTotalColetaMaterialParaExameLaboratorial | Quantidade de coletas para exame laboratorial realizadas |  |
| Dim. comum ao grupo | `co_dim_municipio` | — | Código de identificação do município. Campo `co_seq_dim_municipio` da `tb_dim_municipio` | FK → `tb_dim_municipio.co_seq_dim_municipio` **recorte municipal** |
| Dim. comum ao grupo | `co_dim_tipo_ficha` | — | Código de identificação do tipo de ficha. Campo `co_seq_dim_tipo_ficha` da `tb_dim_tipo_ficha` | FK → `tb_dim_tipo_ficha.co_seq_dim_tipo_ficha` |
| Dim. comum ao grupo | `co_dim_profissional` | — | Código de identificação do profissional responsável. Campo `co_seq_dim_profissional` da `tb_dim_profissional` | FK → `tb_dim_profissional.co_seq_dim_profissional` |
| Dim. comum ao grupo | `co_dim_cbo` | — | Código de identificação do CBO profissional responsável. Campo `co_seq_dim_cbo` da `tb_dim_cbo` | FK → `tb_dim_cbo.co_seq_dim_cbo` |
| Dim. comum ao grupo | `co_dim_unidade_saude` | — | Código de identificação da unidade de sáude do profissional responsável. Campo `co_seq_dim_unidade_saude` da `tb_dim_unidade_saude` | FK → `tb_dim_unidade_saude.co_seq_dim_unidade_saude` |
| Dim. comum ao grupo | `co_dim_equipe` | — | Código de identificação da equipe do profissional responsável. Campo `co_seq_dim_equipe` da `tb_dim_equipe` | FK → `tb_dim_equipe.co_seq_dim_equipe` |
| Dim. comum ao grupo | `co_dim_tipo_origem_dado_transp` | — | Código da origem do dado no transporte. Campo `co_seq_dim_tp_orgm_dado_transp` da `tb_dim_tipo_origem_dado_transp` | FK → `tb_dim_tipo_origem_dado_transp.co_seq_dim_tp_orgm_dado_transp` |
| Dim. comum ao grupo | `co_dim_cds_tipo_origem` | — | Código da origem do dado no transporte. Campo `co_seq_dim_tipo_origem` da `tb_dim_tipo_origem` | FK → `tb_dim_tipo_origem.co_seq_dim_tipo_origem` |
| Dim. comum ao grupo | `co_dim_tempo` | — | Responsável por armazenar as datas de maneira estruturada. Campo `co_seq_dim_tempo` da `tb_dim_tempo` | FK → `tb_dim_tempo.co_seq_dim_tempo` **data do registro** |

#### `tb_fat_proced_atend` — Tabela fato dos atendimentos de procedimento (individualizados)

Fonte: <https://integracao.esusaps.bridge.ufsc.tech/dw/fatos/procedimentos/tb_fat_proced_atend.html> — **Alterado em 11/09/2026**.

Texto da doc (objetivo/regras/notas):

- A tabela `tb_fat_proced_atend` é populada quando os dados de um procedimento são processados. Os dados processados nessa tabela se referem apenas a procedimentos individualizados (quando há referência a um cidadão).

> Um registro por atendimento individualizado de procedimentos (tem cidadão, medidas e `st_escuta_inicial`). Peso/altura entraram na v4.2.1→4.2.3; PA sistólica/diastólica e demais medições na v5.3.6→5.3.7.

| Seção da doc | Coluna | LEDI | Descrição (doc oficial) | Marcação |
|---|---|---|---|---|
| Metadados | `co_seq_fat_proced_atend` | — | Código de identificação sequencial criado automaticamente pelo sistema | PK |
| Metadados | `co_fat_procedimento` | — | Campo `co_seq_fat_procedimento` da `tb_fat_procedimentos` | FK → `tb_fat_procedimentos.co_seq_fat_procedimento` ⚠ doc cita `tb_fat_procedimentos` (plural); página é `tb_fat_procedimento` |
| Metadados | `co_fat_cidadao_pec` | — | Campo `co_seq_fat_cidadao_pec` da `tb_fat_cidadao_pec` | FK → `tb_fat_cidadao_pec.co_seq_fat_cidadao_pec` **chave de pessoa** (alvo sem página na doc) |
| Metadados | `nu_prontuario` | numProntuario | Número do prontuário criptografado | **[PII — nunca projetar]** |
| Metadados | `nu_uuid_ficha` | uuidFicha | Identificador universalmente único do registro |  |
| Metadados | `nu_uuid_dado_transp` | uuidDadoSerializado | Identificador universalmente único da camada de transporte de dados. Caso o registro seja gerado dentro do PEC terá o mesmo valor do campo nu_uuid_ficha |  |
| Metadados | `nu_atendimento` | — | Uma ficha pode conter mais de um atendimento esse campo é utilizado para ordenar os atendimentos dentro um mesmo envio |  |
| Metadados | `nu_cpf_cidadao` | cpfCidadao | CPF do cidadão | **[PII — nunca projetar]** |
| Metadados | `nu_cns` | cnsCidadao | CNS do cidadão | **[PII — nunca projetar]** |
| Metadados | `st_nao_possui_cpf` | stCidadaoNaoPossuiCpf | Indica se o cidadão não possui CPF |  |
| Métricas | `dt_nascimento` | dtNascimento | Data de nascimento do cidadão | *[sensível — projetar só na capacidade "cidadão"]* |
| Métricas | `st_escuta_inicial` | statusEscutaInicialOrientacao | Status que indica a realização da escuta inicial |  |
| Métricas | `nu_medicao_circ_abdominal` | circunferenciaAbdominal | Circunferência abdominal do cidadão em centímetros |  |
| Métricas | `nu_medicao_perim_pantrlha` | perimetroPanturrilha | Perímetro da panturrilha do cidadão em centímetros |  |
| Métricas | `nu_medicao_pressao_sistolica` | pressaoArterialSistolica | Pressão arterial sistólica do cidadão em mmHg |  |
| Métricas | `nu_medicao_pressao_diastolica` | pressaoArterialDiastolica | Pressão arterial diastólica do cidadão em mmHg |  |
| Métricas | `nu_medicao_freq_respiratoria` | frequenciaRespiratoria | Frequência respiratória do cidadão em MPM |  |
| Métricas | `nu_medicao_freq_cardiaca` | frequenciaCardiaca | Frequência cardíaca do cidadão em BPM |  |
| Métricas | `nu_medicao_temperatura` | temperatura | Temperatura do cidadão em ºC |  |
| Métricas | `nu_medicao_saturacao_o2` | saturacaoO2 | Saturação de oxigênio do cidadão em percentual |  |
| Métricas | `nu_medicao_glicemia` | glicemiaCapilar | Glicemia capilar do cidadão em mg/dL |  |
| Métricas | `nu_peso` | peso | Peso do cidadão em quilogramas |  |
| Métricas | `nu_altura` | altura | Altura do cidadão em centímetros |  |
| Métricas | `nu_perimetro_cefalico` | perimetroCefalico | Perímetro cefálico do cidadão em centímetros |  |
| Métricas | `ds_filtro_procedimento` | procedimentos | Agrupa todos os procedimentos registrados no atendimento |  |
| Métricas | `dt_inicial_atendimento` | dataHoraInicialAtendimento | Data e hora do início do atendimento no formato "YYYY-MM-DD HH:MM:SS.MMM" |  |
| Métricas | `dt_final_atendimento` | dataHoraFinalAtendimento | Data e hora do fim do atendimento no formato "YYYY-MM-DD HH:MM:SS.MMM" |  |
| Dim. específica | `co_dim_sexo` | — | Código do sexo. Campo `co_seq_dim_faixa_sexo` da `tb_dim_sexo` | FK → `tb_dim_sexo.co_seq_dim_faixa_sexo` ⚠ PK real da dimensão é `co_seq_dim_sexo` |
| Dim. específica | `co_dim_turno` | — | Código do turno do atendimento. Campo `co_seq_dim_faixa_turno` da `tb_dim_turno` | FK → `tb_dim_turno.co_seq_dim_faixa_turno` |
| Dim. específica | `co_dim_local_atendimento` | — | Código do local de atendimento. Campo `co_seq_dim_local_atendimento` da `tb_dim_local_atendimento` | FK → `tb_dim_local_atendimento.co_seq_dim_local_atendimento` |
| Dim. específica | `co_dim_faixa_etaria` | — | Código da faixa etária. Campo `co_seq_dim_faixa_etaria` da `tb_dim_faixa_etaria` | FK → `tb_dim_faixa_etaria.co_seq_dim_faixa_etaria` |
| Dim. específica | `co_dim_just_nao_possui_cpf` | — | Código da justificativa de não possuir CPF. Campo `co_seq_dim_just_nao_possui_cpf` da `tb_dim_just_nao_possui_cpf` | FK → `tb_dim_just_nao_possui_cpf.co_seq_dim_just_nao_possui_cpf` |
| Dim. comum ao grupo | `co_dim_municipio` | — | Código de identificação do município. Campo `co_seq_dim_municipio` da `tb_dim_municipio` | FK → `tb_dim_municipio.co_seq_dim_municipio` **recorte municipal** |
| Dim. comum ao grupo | `co_dim_tipo_ficha` | — | Código de identificação do tipo de ficha. Campo `co_seq_dim_tipo_ficha` da `tb_dim_tipo_ficha` | FK → `tb_dim_tipo_ficha.co_seq_dim_tipo_ficha` |
| Dim. comum ao grupo | `co_dim_profissional` | — | Código de identificação do profissional responsável. Campo `co_seq_dim_profissional` da `tb_dim_profissional` | FK → `tb_dim_profissional.co_seq_dim_profissional` |
| Dim. comum ao grupo | `co_dim_cbo` | — | Código de identificação do CBO profissional responsável. Campo `co_seq_dim_cbo` da `tb_dim_cbo` | FK → `tb_dim_cbo.co_seq_dim_cbo` |
| Dim. comum ao grupo | `co_dim_unidade_saude` | — | Código de identificação da unidade de sáude do profissional responsável. Campo `co_seq_dim_unidade_saude` da `tb_dim_unidade_saude` | FK → `tb_dim_unidade_saude.co_seq_dim_unidade_saude` |
| Dim. comum ao grupo | `co_dim_equipe` | — | Código de identificação da equipe do profissional responsável. Campo `co_seq_dim_equipe` da `tb_dim_equipe` | FK → `tb_dim_equipe.co_seq_dim_equipe` |
| Dim. comum ao grupo | `co_dim_tipo_origem_dado_transp` | — | Código da origem do dado no transporte. Campo `co_seq_dim_tp_orgm_dado_transp` da `tb_dim_tipo_origem_dado_transp` | FK → `tb_dim_tipo_origem_dado_transp.co_seq_dim_tp_orgm_dado_transp` |
| Dim. comum ao grupo | `co_dim_cds_tipo_origem` | — | Código da origem do dado no transporte. Campo `co_seq_dim_tipo_origem` da `tb_dim_tipo_origem` | FK → `tb_dim_tipo_origem.co_seq_dim_tipo_origem` |
| Dim. comum ao grupo | `co_dim_tempo` | — | Responsável por armazenar as datas de maneira estruturada. Campo `co_seq_dim_tempo` da `tb_dim_tempo` | FK → `tb_dim_tempo.co_seq_dim_tempo` **data do registro** |

#### `tb_fat_proced_atend_proced` — Tabela fato da lista dos procedimentos de um atendimento de procedimentos

Fonte: <https://integracao.esusaps.bridge.ufsc.tech/dw/fatos/procedimentos/tb_fat_proced_atend_proced.html> — **Alterado em 10/11/2024**.

Texto da doc (objetivo/regras/notas):

- A tabela `tb_fat_proced_atend_proced` é populada quando os dados de um procedimento são processados. Os dados processados nessa tabela se referem apenas a procedimentos individualizados (quando há referência a um cidadão).

> Uma linha por procedimento SIGTAP/AB do atendimento. **Ligação com `tb_fat_proced_atend` não é declarada como FK única:** a doc diz que `co_fat_procedimento` é o `co_seq_fat_procedimento` "da `tb_fat_procedimentos` e `tb_fat_proced_atend`" e que `nu_atendimento` é o da `tb_fat_proced_atend`. Candidato (inferido): `(co_fat_procedimento, nu_atendimento)`. Validar cardinalidade ao vivo antes de usar.

| Seção da doc | Coluna | LEDI | Descrição (doc oficial) | Marcação |
|---|---|---|---|---|
| Metadados | `co_seq_fat_proced_atend_proced` | — | Código de identificação sequencial criado automaticamente pelo sistema | PK |
| Metadados | `co_fat_procedimento` | — | Campo `co_seq_fat_procedimento` da `tb_fat_procedimentos` e `tb_fat_proced_atend` | FK → `tb_fat_procedimentos.co_seq_fat_procedimento` ⚠ alvo ambíguo na doc (ver "Divergências") |
| Metadados | `co_fat_cidadao_pec` | — | Campo `co_seq_fat_cidadao_pec` da `tb_fat_cidadao_pec` | FK → `tb_fat_cidadao_pec.co_seq_fat_cidadao_pec` **chave de pessoa** (alvo sem página na doc) |
| Metadados | `nu_uuid_ficha` | uuidFicha | Identificador universalmente único do registro |  |
| Metadados | `nu_uuid_dado_transp` | — | Identificador universalmente único da camada de transporte de dados. Caso o registro seja gerado dentro do PEC terá o mesmo valor do campo nu_uuid_ficha |  |
| Metadados | `nu_atendimento` | — | Uma ficha pode conter mais de um atendimento esse campo é utilizado para ordenar os atendimentos dentro um mesmo envio. Campo `nu_atendimento` da `tb_fat_proced_atend` | FK → `tb_fat_proced_atend.nu_atendimento` |
| Metadados | `nu_cpf_cidadao` | cpfCidadao | CPF do cidadão | **[PII — nunca projetar]** |
| Metadados | `nu_cns` | cnsCidadao | CNS do cidadão | **[PII — nunca projetar]** |
| Métricas | `dt_nascimento` | dtNascimento | Data de nascimento do cidadão | *[sensível — projetar só na capacidade "cidadão"]* |
| Métricas | `st_escuta_inicial` | statusEscutaInicialOrientacao | Status que indica a realização da escuta inicial |  |
| Dim. específica | `co_dim_sexo` | — | Código do sexo. Campo `co_seq_dim_faixa_sexo` da `tb_dim_sexo` | FK → `tb_dim_sexo.co_seq_dim_faixa_sexo` ⚠ PK real da dimensão é `co_seq_dim_sexo` |
| Dim. específica | `co_dim_turno` | — | Código do turno do atendimento. Campo `co_seq_dim_faixa_turno` da `tb_dim_turno` | FK → `tb_dim_turno.co_seq_dim_faixa_turno` |
| Dim. específica | `co_dim_local_atendimento` | — | Código do local de atendimento. Campo `co_seq_dim_local_atendimento` da `tb_dim_local_atendimento` | FK → `tb_dim_local_atendimento.co_seq_dim_local_atendimento` |
| Dim. específica | `co_dim_procedimento` | — | Código procedimento do atendimento. Campo `co_seq_dim_procedimento` da `tb_dim_procedimento` | FK → `tb_dim_procedimento.co_seq_dim_procedimento` |
| Dim. comum ao grupo | `co_dim_municipio` | — | Código de identificação do município. Campo `co_seq_dim_municipio` da `tb_dim_municipio` | FK → `tb_dim_municipio.co_seq_dim_municipio` **recorte municipal** |
| Dim. comum ao grupo | `co_dim_tipo_ficha` | — | Código de identificação do tipo de ficha. Campo `co_seq_dim_tipo_ficha` da `tb_dim_tipo_ficha` | FK → `tb_dim_tipo_ficha.co_seq_dim_tipo_ficha` |
| Dim. comum ao grupo | `co_dim_profissional` | — | Código de identificação do profissional responsável. Campo `co_seq_dim_profissional` da `tb_dim_profissional` | FK → `tb_dim_profissional.co_seq_dim_profissional` |
| Dim. comum ao grupo | `co_dim_cbo` | — | Código de identificação do CBO profissional responsável. Campo `co_seq_dim_cbo` da `tb_dim_cbo` | FK → `tb_dim_cbo.co_seq_dim_cbo` |
| Dim. comum ao grupo | `co_dim_unidade_saude` | — | Código de identificação da unidade de sáude do profissional responsável. Campo `co_seq_dim_unidade_saude` da `tb_dim_unidade_saude` | FK → `tb_dim_unidade_saude.co_seq_dim_unidade_saude` |
| Dim. comum ao grupo | `co_dim_equipe` | — | Código de identificação da equipe do profissional responsável. Campo `co_seq_dim_equipe` da `tb_dim_equipe` | FK → `tb_dim_equipe.co_seq_dim_equipe` |
| Dim. comum ao grupo | `co_dim_tipo_origem_dado_transp` | — | Código da origem do dado no transporte. Campo `co_seq_dim_tp_orgm_dado_transp` da `tb_dim_tipo_origem_dado_transp` | FK → `tb_dim_tipo_origem_dado_transp.co_seq_dim_tp_orgm_dado_transp` |
| Dim. comum ao grupo | `co_dim_cds_tipo_origem` | — | Código da origem do dado no transporte. Campo `co_seq_dim_tipo_origem` da `tb_dim_tipo_origem` | FK → `tb_dim_tipo_origem.co_seq_dim_tipo_origem` |
| Dim. comum ao grupo | `co_dim_tempo` | — | Responsável por armazenar as datas de maneira estruturada. Campo `co_seq_dim_tempo` da `tb_dim_tempo` | FK → `tb_dim_tempo.co_seq_dim_tempo` **data do registro** |

#### `tb_fat_consolidado_cidadao_fp` — Tabela fato com informações consolidadas sobre um cidadão geradas a partir de um procedimento

Fonte: <https://integracao.esusaps.bridge.ufsc.tech/dw/fatos/procedimentos/tb_fat_consolidado_cidadao_fp.html> — **Alterado em 13/07/2026**.

Texto da doc (objetivo/regras/notas):

- A tabela `tb_fat_consolidado_cidadao_fp` é populada quando os dados de um procedimento são processados. Os dados processados nessa tabela se referem apenas a procedimentos individualizados (quando há referência a um cidadão).
- 1. Após o processamento do primeiro procedimento individualizado de um cidadão é criada uma nova linha na tabela `tb_fat_consolidado_cidadao_fp`. Os próximos procedimentos não criam novas linhas, substituem o registro antigo com as informações mais recentes do cidadão, as colunas só são atualizadas caso existam novos registros.

> Relatório operacional (1 linha por cidadão, sobrescrita). Não usar como fonte de evento.

| Seção da doc | Coluna | LEDI | Descrição (doc oficial) | Marcação |
|---|---|---|---|---|
| Fato | `co_seq_fat_conslddo_ciddo_fp` | — | Código de identificação sequencial dos fatos consolidados de um cidadão | PK |
| Fato | `co_fat_cidadao_pec` | — | Campo `co_seq_fat_cidadao_pec` da `tb_fat_cidadao_pec` | FK → `tb_fat_cidadao_pec.co_seq_fat_cidadao_pec` **chave de pessoa** (alvo sem página na doc) |
| Fato | `st_teste_orelhinha` | — | Status que indica a realização do procedimento "0211070149 - EMISSOES OTOACUSTICAS EVOCADAS P/ TRIAGEM AUDITIVA (TESTE DA ORELHINHA)" informado no procedimento individualizado do CDS |  |
| Fato | `st_teste_olhinho` | — | Status que indica a realização do procedimento "ABEX022 - TESTE DO OLHINHO (TRV)" informado no procedimento individualizado do CDS ou no campo SIGTAP da seção Intervenções e/ou procedimentos clínicos realizados que fica dentro do Plano do atendimento do PEC |  |
| Dimensão | `co_dim_tempo_ultima_ficha` | — | Responsável por armazenar as datas de processamento da última ficha de maneira estruturada. Campo `co_seq_dim_tempo` da `tb_dim_tempo` | FK → `tb_dim_tempo.co_seq_dim_tempo` |
| Dimensão | `co_dim_tempo_ult_aval_multi` | — | Responsável por armazenar a data da última avaliação multiprofissional de maneira estruturada. Campo `co_seq_dim_tempo` da `tb_dim_tempo` | FK → `tb_dim_tempo.co_seq_dim_tempo` |


### 2.3 Visita domiciliar e territorial (MIVDT)

#### `tb_fat_visita_domiciliar` — Tabela fato de visita domiciliar

Fonte: <https://integracao.esusaps.bridge.ufsc.tech/dw/fatos/visita_domiciliar/tb_fat_visita_domiciliar.html> — **Alterado em 11/09/2026**.

Texto da doc (objetivo/regras/notas):

- A tabela `tb_fat_visita_domiciliar` é populada sempre que os dados de uma Visita Domiciliar são processados.
- 1. A `tb_fat_visita_domiciliar` é preenchida quando são processadas:
  - Fichas de visita domiciliar recebidas através da importação de sistemas terceiros ou outras instalações do PEC e-SUS-APS;
  - Registro de uma visita domiciliar pelo ACS no CDS;
  - Registro de uma visita domiciliar no aplicativo e-SUS Território.

> Sem data própria: a data da visita só existe via `co_dim_tempo`. `st_nao_possui_cpf` e `co_dim_just_nao_possui_cpf` entraram na v5.5.21→5.5.23 (**ausentes no 5.4.37**). LEDI FVDT tem `pressaoSistolica` e `pressaoDiastolica` separadas, mas o DW documenta só `nu_medicao_pressao_arterial` (sem referência LEDI): formato a verificar. Visitas a imóveis sem cidadão são possíveis (`co_dim_tipo_imovel`); o comportamento de `co_fat_cidadao_pec` nesses casos não está documentado.

| Seção da doc | Coluna | LEDI | Descrição (doc oficial) | Marcação |
|---|---|---|---|---|
| Metadados | `co_seq_fat_visita_domiciliar` | — | Código de identificação sequencial criado automaticamente pelo sistema | PK |
| Metadados | `nu_uuid_ficha` | uuidFicha | Identificador universalmente único do registro |  |
| Metadados | `nu_uuid_dado_transp` | uuidDadoSerializado | Identificador universalmente único da camada de transporte de dados. Caso o registro seja gerado dentro do PEC terá o mesmo valor do campo nu_uuid_ficha |  |
| Metadados | `nu_prontuario` | numProntuario | Número do prontuário criptografado | **[PII — nunca projetar]** |
| Metadados | `nu_cns` | cnsCidadao | CNS do cidadão | **[PII — nunca projetar]** |
| Metadados | `nu_cpf_cidadao` | cpfCidadao | CPF do cidadão | **[PII — nunca projetar]** |
| Metadados | `co_fat_cidadao_pec` | — | Campo `co_seq_fat_cidadao_pec` da `tb_fat_cidadao_pec` | FK → `tb_fat_cidadao_pec.co_seq_fat_cidadao_pec` **chave de pessoa** (alvo sem página na doc) |
| Metadados | `co_uuid_origem_fcd` | uuidOrigemCadastroDomiciliar | Armazena o UUID do domicílio que recebeu a visita. Corresponde ao campo `nu_uuid_ficha_origem` da `tb_fat_cad_domiciliar` | *[pseudônimo de domicílio — não projetar]* |
| Metadados | `st_nao_possui_cpf` | stCidadaoNaoPossuiCpf | Indica se o cidadão não possui CPF |  |
| Métricas | `nu_micro_area` | microarea | Número da microárea onde o domicílio está localizado | *[quase-identificador — não projetar]* |
| Métricas | `dt_nascimento` | dtNascimento | Data de nascimento do cidadão | *[sensível — projetar só na capacidade "cidadão"]* |
| Métricas | `nu_peso` | pesoAcompanhamentoNutricional | Peso do cidadão em quilogramas |  |
| Métricas | `nu_altura` | alturaAcompanhamentoNutricional | Altura do cidadão em centímetros |  |
| Métricas | `st_visita_compartilhada` | statusVisitaCompartilhadaOutroProfissional | Status que indica se a visita foi realizada de forma compartilhada |  |
| Métricas | `st_mot_vis_cad_att` | motivosVisita | Indica se o motivo da visita foi atualização/cadastramento |  |
| Métricas | `st_mot_vis_visita_periodica` | motivosVisita | Indica se o motivo da visita foi visita periódica |  |
| Métricas | `st_mot_vis_egresso_internacao` | motivosVisita | Indica se o motivo da visita foi egresso de internação |  |
| Métricas | `st_mot_vis_convte_atvidd_cltva` | motivosVisita | Indica se o motivo da visita foi convite para atividade coletiva |  |
| Métricas | `st_mot_vis_orintacao_prevncao` | motivosVisita | Indica se o motivo da visita foi orientação/prevenção |  |
| Métricas | `st_mot_vis_outros` | motivosVisita | Indica se o motivo da visita foi outros |  |
| Métricas | `st_mot_vis_busca_ativa` | — | Agrupador — indica se alguma opção do grupo Busca ativa foi selecionada |  |
| Métricas | `st_mot_vis_acompanhamento` | — | Agrupador — indica se alguma opção do grupo Acompanhamento foi selecionada |  |
| Métricas | `st_mot_vis_ctrl_ambnte_vetor` | — | Agrupador — indica se alguma opção do grupo Controle ambiental / vetorial foi selecionada. Até a versão 2.0 esta opção era única; a partir da versão 2.1 passou a ser um agrupador |  |
| Métricas | `st_busca_ativa_consulta` | motivosVisita | Indica busca ativa para consulta |  |
| Métricas | `st_busca_ativa_exame` | motivosVisita | Indica busca ativa para exame |  |
| Métricas | `st_busca_ativa_vacina` | motivosVisita | Indica busca ativa para vacinação |  |
| Métricas | `st_busca_ativa_bolsa_familia` | motivosVisita | Indica busca ativa para Bolsa Família |  |
| Métricas | `st_acomp_gestante` | motivosVisita | Indica acompanhamento de gestante |  |
| Métricas | `st_acomp_puerpera` | motivosVisita | Indica acompanhamento de puérpera |  |
| Métricas | `st_acomp_recem_nascido` | motivosVisita | Indica acompanhamento de recém-nascido |  |
| Métricas | `st_acomp_crianca` | motivosVisita | Indica acompanhamento de criança |  |
| Métricas | `st_acomp_pessoa_desnutricao` | motivosVisita | Indica acompanhamento de pessoa com desnutrição |  |
| Métricas | `st_acomp_pessoa_reabil_deficie` | motivosVisita | Indica acompanhamento de pessoa em reabilitação ou com deficiência |  |
| Métricas | `st_acomp_pessoa_hipertensao` | motivosVisita | Indica acompanhamento de pessoa com hipertensão arterial |  |
| Métricas | `st_acomp_pessoa_diabetes` | motivosVisita | Indica acompanhamento de pessoa com diabetes |  |
| Métricas | `st_acomp_pessoa_asma` | motivosVisita | Indica acompanhamento de pessoa com asma |  |
| Métricas | `st_acomp_pessoa_dpoc_enfisema` | motivosVisita | Indica acompanhamento de pessoa com DPOC/enfisema |  |
| Métricas | `st_acomp_pessoa_cancer` | motivosVisita | Indica acompanhamento de pessoa com câncer |  |
| Métricas | `st_acomp_pessoa_doenca_cronica` | motivosVisita | Indica acompanhamento de pessoa com outra doença crônica |  |
| Métricas | `st_acomp_pessoa_hanseniase` | motivosVisita | Indica acompanhamento de pessoa com hanseníase |  |
| Métricas | `st_acomp_pessoa_tuberculose` | motivosVisita | Indica acompanhamento de pessoa com tuberculose |  |
| Métricas | `st_acomp_sintomaticos_respirat` | motivosVisita | Indica acompanhamento de sintomáticos respiratórios |  |
| Métricas | `st_acomp_tabagista` | motivosVisita | Indica acompanhamento de tabagista |  |
| Métricas | `st_acomp_domiciliados_acamados` | motivosVisita | Indica acompanhamento de domiciliados e acamados |  |
| Métricas | `st_acomp_condi_vulnerab_social` | motivosVisita | Indica acompanhamento de pessoa em condição de vulnerabilidade social |  |
| Métricas | `st_acomp_condi_bolsa_familia` | motivosVisita | Indica acompanhamento de beneficiário do Bolsa Família |  |
| Métricas | `st_acomp_saude_mental` | motivosVisita | Indica acompanhamento de pessoa com problema de saúde mental |  |
| Métricas | `st_acomp_usuario_alcool` | motivosVisita | Indica acompanhamento de usuário de álcool |  |
| Métricas | `st_acomp_usuario_outras_drogra` | motivosVisita | Indica acompanhamento de usuário de outras drogas |  |
| Métricas | `st_acomp_pessoa_idosa` | motivosVisita | Indica acompanhamento de pessoa idosa |  |
| Métricas | `st_ctrl_amb_vet_acao_educativa` | motivosVisita | Indica controle ambiental/vetorial — ação educativa |  |
| Métricas | `st_ctrl_amb_vet_imovel_foco` | motivosVisita | Indica controle ambiental/vetorial — imóvel com foco |  |
| Métricas | `st_ctrl_amb_vet_acao_mecanica` | motivosVisita | Indica controle ambiental/vetorial — ação mecânica |  |
| Métricas | `st_ctrl_amb_vet_tratamnt_focal` | motivosVisita | Indica controle ambiental/vetorial — tratamento focal |  |
| Métricas | `nu_medicao_glicemia` | glicemia | Glicemia capilar do cidadão em mg/dL |  |
| Métricas | `nu_medicao_pressao_arterial` | — | Pressão arterial do cidadão em mmHg | ⚠ coluna única; formato de sistólica/diastólica não documentado |
| Métricas | `nu_medicao_temperatura` | temperatura | Temperatura do cidadão em ºC |  |
| Métricas | `nu_latitude` | latitude | Latitude da localização do domicílio visitado | **[PII — nunca projetar]** |
| Métricas | `nu_longitude` | longitude | Longitude da localização do domicílio visitado | **[PII — nunca projetar]** |
| Métricas | `st_processado_origem_fcd` | — | Indica se o registro foi processado a partir de uma Ficha de Cadastro Domiciliar |  |
| Dimensão | `co_dim_municipio` | — | Código de identificação do município. Campo `co_seq_dim_municipio` da `tb_dim_municipio` | FK → `tb_dim_municipio.co_seq_dim_municipio` **recorte municipal** |
| Dimensão | `co_dim_tipo_ficha` | — | Código de identificação do tipo de ficha. Campo `co_seq_dim_tipo_ficha` da `tb_dim_tipo_ficha` | FK → `tb_dim_tipo_ficha.co_seq_dim_tipo_ficha` |
| Dimensão | `co_dim_profissional` | — | Código de identificação do profissional responsável. Campo `co_seq_dim_profissional` da `tb_dim_profissional` | FK → `tb_dim_profissional.co_seq_dim_profissional` |
| Dimensão | `co_dim_cbo` | — | Código de identificação do CBO profissional responsável. Campo `co_seq_dim_cbo` da `tb_dim_cbo` | FK → `tb_dim_cbo.co_seq_dim_cbo` |
| Dimensão | `co_dim_unidade_saude` | — | Código de identificação da unidade de saúde do profissional responsável. Campo `co_seq_dim_unidade_saude` da `tb_dim_unidade_saude` | FK → `tb_dim_unidade_saude.co_seq_dim_unidade_saude` |
| Dimensão | `co_dim_equipe` | — | Código de identificação da equipe do profissional responsável. Campo `co_seq_dim_equipe` da `tb_dim_equipe` | FK → `tb_dim_equipe.co_seq_dim_equipe` |
| Dimensão | `co_dim_tempo` | — | Responsável por armazenar as datas de maneira estruturada. Campo `co_seq_dim_tempo` da `tb_dim_tempo` | FK → `tb_dim_tempo.co_seq_dim_tempo` **data do registro** |
| Dimensão | `co_dim_turno` | — | Código do turno da visita. Campo `co_seq_dim_turno` da `tb_dim_turno` | FK → `tb_dim_turno.co_seq_dim_turno` |
| Dimensão | `co_dim_tipo_imovel` | — | Código do tipo de imóvel visitado. Campo `co_seq_dim_tipo_imovel` da `tb_dim_tipo_imovel` | FK → `tb_dim_tipo_imovel.co_seq_dim_tipo_imovel` |
| Dimensão | `co_dim_sexo` | — | Código do sexo do cidadão visitado. Campo `co_seq_dim_faixa_sexo` da `tb_dim_sexo` | FK → `tb_dim_sexo.co_seq_dim_faixa_sexo` ⚠ PK real da dimensão é `co_seq_dim_sexo` |
| Dimensão | `co_dim_faixa_etaria` | — | Código da faixa etária do cidadão visitado. Campo `co_seq_dim_faixa_etaria` da `tb_dim_faixa_etaria` | FK → `tb_dim_faixa_etaria.co_seq_dim_faixa_etaria` |
| Dimensão | `co_dim_just_nao_possui_cpf` | — | Código da justificativa de não possuir CPF. Campo `co_seq_dim_just_nao_possui_cpf` da `tb_dim_just_nao_possui_cpf` | FK → `tb_dim_just_nao_possui_cpf.co_seq_dim_just_nao_possui_cpf` |
| Dimensão | `co_dim_desfecho_visita` | — | Código do desfecho da visita. Campo `co_seq_dim_desfecho_visita` da `tb_dim_desfecho_visita` | FK → `tb_dim_desfecho_visita.co_seq_dim_desfecho_visita` |
| Dimensão | `co_dim_tipo_origem_dado_transp` | — | Código da origem do dado no transporte. Campo `co_seq_dim_tp_orgm_dado_transp` da `tb_dim_tipo_origem_dado_transp` | FK → `tb_dim_tipo_origem_dado_transp.co_seq_dim_tp_orgm_dado_transp` |
| Dimensão | `co_dim_cds_tipo_origem` | — | Código da origem do dado no transporte. Campo `co_seq_dim_tipo_origem` da `tb_dim_tipo_origem` | FK → `tb_dim_tipo_origem.co_seq_dim_tipo_origem` |
| Dimensão | `co_dim_tipo_glicemia` | — | Código do tipo de glicemia capilar. Campo `co_seq_dim_tipo_glicemia` da `tb_dim_tipo_glicemia` | FK → `tb_dim_tipo_glicemia.co_seq_dim_tipo_glicemia` |
| Dimensão | `codimjustnaopossui_cpf` | — | Código da justificativa para não informar CPF. Campo `co_seq_dim_just_nao_possui_cpf` da `tb_dim_just_nao_possui_cpf` | FK → `tb_dim_just_nao_possui_cpf.co_seq_dim_just_nao_possui_cpf` ⚠ duplicata/typo na doc |


### 2.4 Atividade coletiva (MIAC)

#### `tb_fat_atividade_coletiva` — Tabela fato da atividade coletiva

Fonte: <https://integracao.esusaps.bridge.ufsc.tech/dw/fatos/atividade_coletiva/tb_fat_atividade_coletiva.html> — **Alterado em 13/07/2026**.

Texto da doc (objetivo/regras/notas):

- A tabela `tb_fat_atividade_coletiva` é populada sempre que os dados de uma Atividade Coletiva são processados. Ela representa o cabeçalho da atividade, com os dados gerais do evento.
- 1. A `tb_fat_atividade_coletiva` é preenchida quando são processado(a)s:
  - Fichas de atividade coletiva recebidas através da importação de sistemas terceiros, outras instalações do PEC ou o aplicativo e-SUS Atividade Coletiva;
  - Registros de atividade coletiva no CDS ou no PEC.

> Cabeçalho da atividade; não tem cidadão. Data só via `co_dim_tempo`. LEDI FAC: participantes são obrigatórios quando `atividadeTipo` = 5 (Atendimento em grupo) ou 6 (Avaliação/Procedimento coletivo); práticas em saúde são obrigatórias para 6 e proibidas para 1, 2, 3, 4 e 7.

| Seção da doc | Coluna | LEDI | Descrição (doc oficial) | Marcação |
|---|---|---|---|---|
| Metadados | `co_seq_fat_atividade_coletiva` | — | Código de identificação sequencial de atividade coletiva | PK |
| Metadados | `nu_uuid_ficha` | uuidFicha | Identificador universalmente único do registro |  |
| Metadados | `nu_uuid_dado_transp` | uuidDadoSerializado | Identificador universalmente único da camada de transporte de dados. Caso o registro seja gerado dentro do PEC terá o mesmo valor do campo nu_uuid_ficha |  |
| Métricas | `nu_participantes` | numParticipantes | Número de cidadãos participantes registrados na seção de participantes |  |
| Métricas | `nu_participantes_registrados` | — | Número de cidadãos participantes registrados na seção de participantes |  |
| Métricas | `nu_avaliacoes_alteradas` | — | Número de avaliações alteradas registradas na seção de participantes |  |
| Métricas | `st_pse_educacao` | pseEducacao | Indica se a atividade foi realizada no âmbito do PSE — Educação |  |
| Métricas | `st_pse_saude` | pseSaude | Indica se a atividade foi realizada no âmbito do PSE — Saúde |  |
| Métricas | `ds_outra_localidade` | — | Descrição de outra localidade quando o campo "local de atividade" for "Outro" | **[PII — nunca projetar]** |
| Métricas | `ds_filtro_tema_reuniao` | — | Agrupa todos os identificadores dos temas para reunião informados na atividade coletiva |  |
| Métricas | `ds_filtro_tema_para_saude` | — | Agrupa todos os identificadores dos temas para saúde informados na atividade coletiva |  |
| Métricas | `ds_filtro_public_alvo` | — | Agrupa todos os identificadores dos públicos alvo informados na atividade coletiva |  |
| Métricas | `ds_filtro_pratica_em_saude` | — | Agrupa todos os identificadores das práticas em saúde informadas na atividade coletiva |  |
| Dim. específica | `co_dim_tipo_atividade` | — | Código do tipo de atividade. Campo `co_seq_dim_tipo_atividade` da `tb_dim_tipo_atividade` | FK → `tb_dim_tipo_atividade.co_seq_dim_tipo_atividade` |
| Dim. específica | `co_dim_procedimento` | — | Código do procedimento SIGTAP vinculado. Campo `co_seq_dim_procedimento` da `tb_dim_procedimento` | FK → `tb_dim_procedimento.co_seq_dim_procedimento` |
| Dim. específica | `co_dim_undd_sade_acdm_sade` | — | Código da unidade de saúde ou academia da saúde onde ocorreu a atividade. Campo `co_seq_dim_unidade_saude` da `tb_dim_unidade_saude` | FK → `tb_dim_unidade_saude.co_seq_dim_unidade_saude` |
| Dim. específica | `co_dim_inep` | — | Código INEP da escola onde ocorreu a atividade (PSE). Campo `co_seq_dim_inep` da `tb_dim_inep` | FK → `tb_dim_inep.co_seq_dim_inep` |
| Dim. comum ao grupo | `co_dim_municipio` | — | Código de identificação do município. Campo `co_seq_dim_municipio` da `tb_dim_municipio` | FK → `tb_dim_municipio.co_seq_dim_municipio` **recorte municipal** |
| Dim. comum ao grupo | `co_dim_tipo_ficha` | — | Código de identificação do tipo de ficha. Campo `co_seq_dim_tipo_ficha` da `tb_dim_tipo_ficha` | FK → `tb_dim_tipo_ficha.co_seq_dim_tipo_ficha` |
| Dim. comum ao grupo | `co_dim_profissional` | — | Código de identificação do profissional responsável. Campo `co_seq_dim_profissional` da `tb_dim_profissional` | FK → `tb_dim_profissional.co_seq_dim_profissional` |
| Dim. comum ao grupo | `co_dim_cbo` | — | Código de identificação do CBO profissional responsável. Campo `co_seq_dim_cbo` da `tb_dim_cbo` | FK → `tb_dim_cbo.co_seq_dim_cbo` |
| Dim. comum ao grupo | `co_dim_unidade_saude` | — | Código de identificação da unidade de saúde do profissional responsável. Campo `co_seq_dim_unidade_saude` da `tb_dim_unidade_saude` | FK → `tb_dim_unidade_saude.co_seq_dim_unidade_saude` |
| Dim. comum ao grupo | `co_dim_equipe` | — | Código de identificação da equipe do profissional responsável. Campo `co_seq_dim_equipe` da `tb_dim_equipe` | FK → `tb_dim_equipe.co_seq_dim_equipe` |
| Dim. comum ao grupo | `co_dim_tempo` | — | Responsável por armazenar as datas de maneira estruturada. Campo `co_seq_dim_tempo` da `tb_dim_tempo` | FK → `tb_dim_tempo.co_seq_dim_tempo` **data do registro** |
| Dim. comum ao grupo | `co_dim_turno` | — | Código do turno em que a atividade foi realizada. Campo `co_seq_dim_turno` da `tb_dim_turno` | FK → `tb_dim_turno.co_seq_dim_turno` |
| Dim. comum ao grupo | `co_dim_tipo_origem_dado_transp` | — | Código da origem do dado no transporte. Campo `co_seq_dim_tp_orgm_dado_transp` da `tb_dim_tipo_origem_dado_transp` | FK → `tb_dim_tipo_origem_dado_transp.co_seq_dim_tp_orgm_dado_transp` |
| Dim. comum ao grupo | `co_dim_cds_tipo_origem` | — | Código da origem do dado no transporte. Campo `co_seq_dim_tipo_origem` da `tb_dim_tipo_origem` | FK → `tb_dim_tipo_origem.co_seq_dim_tipo_origem` |

#### `tb_fat_atvdd_coletiva_part` — Tabela fato dos participantes da atividade coletiva

Fonte: <https://integracao.esusaps.bridge.ufsc.tech/dw/fatos/atividade_coletiva/tb_fat_atvdd_coletiva_part.html> — **Alterado em 11/09/2026**.

Texto da doc (objetivo/regras/notas):

- A tabela `tb_fat_atvdd_coletiva_part` registra os cidadãos participantes de uma atividade coletiva. Cada linha representa um participante individual e armazena dados de identificação, antropometria e avaliação.
- 1. A `tb_fat_atvdd_coletiva_part` é preenchida quando é processado registro de atividade coletiva em que são informados participantes individuais.

> **Sem pressão arterial**: nem a doc DW nem o LEDI FAC (`ParticipanteRowItem`) têm PA de participante — só peso e altura. `st_nao_possui_cpf`/`co_dim_just_nao_possui_cpf` entraram na v5.5.23→5.5.25 (ausentes no 5.4.37).

| Seção da doc | Coluna | LEDI | Descrição (doc oficial) | Marcação |
|---|---|---|---|---|
| Metadados | `co_seq_fat_atvdd_cltv_part` | — | Código de identificação sequencial do cidadão participante de um atividade coletiva | PK |
| Metadados | `co_fat_atividade_coletiva` | — | Campo `co_seq_fat_atividade_coletiva` da `tb_fat_atividade_coletiva` | FK → `tb_fat_atividade_coletiva.co_seq_fat_atividade_coletiva` |
| Metadados | `nu_uuid_ficha` | uuidFicha | Identificador universalmente único do registro |  |
| Metadados | `nu_uuid_dado_transp` | uuidDadoSerializado | Identificador universalmente único da camada de transporte de dados |  |
| Metadados | `co_fat_cidadao_pec` | — | Campo `co_seq_fat_cidadao_pec` da `tb_fat_cidadao_pec` | FK → `tb_fat_cidadao_pec.co_seq_fat_cidadao_pec` **chave de pessoa** (alvo sem página na doc) |
| Metadados | `nu_participante_cns` | cnsParticipante | CNS do participante | **[PII — nunca projetar]** |
| Metadados | `nu_cpf_participante` | cpfParticipante | CPF do participante | **[PII — nunca projetar]** |
| Metadados | `st_nao_possui_cpf` | stCidadaoNaoPossuiCpf | Indica se o participante não possui CPF |  |
| Métricas | `dt_participante_nascimento` | dataNascimento | Data de nascimento do participante | *[sensível — preferir a data do cadastro]* |
| Métricas | `nu_participante_altura` | altura | Altura do participante em centímetros |  |
| Métricas | `nu_participante_peso` | peso | Peso do participante em quilogramas |  |
| Métricas | `st_avaliacao_alterada` | avaliacaoAlterada | Indica se a avaliação do participante está alterada |  |
| Métricas | `st_pcnt_cessou_habito_fumar` | cessouHabitoFumar | Indica se o participante cessou o hábito de fumar |  |
| Métricas | `st_pcnt_abandonou_grupo` | abandonouGrupo | Indica se o participante abandonou o grupo |  |
| Dim. específica | `co_dim_tipo_atividade` | — | Código do tipo de atividade. Campo `co_seq_dim_tipo_atividade` da `tb_dim_tipo_atividade` | FK → `tb_dim_tipo_atividade.co_seq_dim_tipo_atividade` |
| Dim. específica | `co_dim_participante_sexo` | — | Código do sexo do participante. Campo `co_seq_dim_faixa_sexo` da `tb_dim_sexo` | FK → `tb_dim_sexo.co_seq_dim_faixa_sexo` ⚠ PK real da dimensão é `co_seq_dim_sexo` |
| Dim. específica | `co_dim_just_nao_possui_cpf` | — | Código da justificativa de não possuir CPF. Campo `co_seq_dim_just_nao_possui_cpf` da `tb_dim_just_nao_possui_cpf` | FK → `tb_dim_just_nao_possui_cpf.co_seq_dim_just_nao_possui_cpf` |
| Dim. comum ao grupo | `co_dim_municipio` | — | Código de identificação do município. Campo `co_seq_dim_municipio` da `tb_dim_municipio` | FK → `tb_dim_municipio.co_seq_dim_municipio` **recorte municipal** |
| Dim. comum ao grupo | `co_dim_tipo_ficha` | — | Código de identificação do tipo de ficha. Campo `co_seq_dim_tipo_ficha` da `tb_dim_tipo_ficha` | FK → `tb_dim_tipo_ficha.co_seq_dim_tipo_ficha` |
| Dim. comum ao grupo | `co_dim_profissional` | — | Código de identificação do profissional responsável. Campo `co_seq_dim_profissional` da `tb_dim_profissional` | FK → `tb_dim_profissional.co_seq_dim_profissional` |
| Dim. comum ao grupo | `co_dim_cbo` | — | Código de identificação do CBO profissional responsável. Campo `co_seq_dim_cbo` da `tb_dim_cbo` | FK → `tb_dim_cbo.co_seq_dim_cbo` |
| Dim. comum ao grupo | `co_dim_unidade_saude` | — | Código de identificação da unidade de saúde do profissional responsável. Campo `co_seq_dim_unidade_saude` da `tb_dim_unidade_saude` | FK → `tb_dim_unidade_saude.co_seq_dim_unidade_saude` |
| Dim. comum ao grupo | `co_dim_equipe` | — | Código de identificação da equipe do profissional responsável. Campo `co_seq_dim_equipe` da `tb_dim_equipe` | FK → `tb_dim_equipe.co_seq_dim_equipe` |
| Dim. comum ao grupo | `co_dim_tempo` | — | Responsável por armazenar as datas de maneira estruturada. Campo `co_seq_dim_tempo` da `tb_dim_tempo` | FK → `tb_dim_tempo.co_seq_dim_tempo` **data do registro** |
| Dim. comum ao grupo | `co_dim_turno` | — | Código do turno em que a atividade foi realizada. Campo `co_seq_dim_turno` da `tb_dim_turno` | FK → `tb_dim_turno.co_seq_dim_turno` |
| Dim. comum ao grupo | `co_dim_tipo_origem_dado_transp` | — | Código da origem do dado no transporte. Campo `co_seq_dim_tp_orgm_dado_transp` da `tb_dim_tipo_origem_dado_transp` | FK → `tb_dim_tipo_origem_dado_transp.co_seq_dim_tp_orgm_dado_transp` |
| Dim. comum ao grupo | `co_dim_cds_tipo_origem` | — | Código da origem do dado no transporte. Campo `co_seq_dim_tipo_origem` da `tb_dim_tipo_origem` | FK → `tb_dim_tipo_origem.co_seq_dim_tipo_origem` |

#### `tb_fat_atvdd_coletiva_ext` — Tabela fato das ações de saúde da atividade coletiva

Fonte: <https://integracao.esusaps.bridge.ufsc.tech/dw/fatos/atividade_coletiva/tb_fat_atvdd_coletiva_ext.html> — **Alterado em 10/09/2026**.

Texto da doc (objetivo/regras/notas):

- A tabela `tb_fat_atvdd_coletiva_ext` é populada quando o tipo de atividade processada corresponde a uma ação de saúde com usuários. Cada registro armazena os públicos-alvo, temas para saúde e práticas em saúde selecionados.
- 1. A `tb_fat_atvdd_coletiva_ext` é preenchida quando:
  - O tipo de atividade da `tb_fat_atividade_coletiva` vinculada refere-se a ações de saúde direcionadas aos usuários.

> Só existe para tipos de atividade de "ação de saúde com usuários". `st_prat_saude_pnct_manutencao` entrou na v5.5.25→5.5.26 (ausente no 5.4.37). Prática relevante: `st_prat_saude_antropometria`.

| Seção da doc | Coluna | LEDI | Descrição (doc oficial) | Marcação |
|---|---|---|---|---|
| Metadados | `co_seq_fat_atvdd_cltv_ext` | — | Código de identificação sequencial da atividade coletiva em saúde | PK |
| Metadados | `co_fat_atividade_coletiva` | — | Campo `co_seq_fat_atividade_coletiva` da `tb_fat_atividade_coletiva` | FK → `tb_fat_atividade_coletiva.co_seq_fat_atividade_coletiva` |
| Metadados | `nu_uuid_ficha` | uuidFicha | Identificador universalmente único do registro |  |
| Metadados | `nu_uuid_dado_transp` | — | Identificador universalmente único da camada de transporte de dados |  |
| Métricas | `st_pblc_alvo_comunidade_geral` | publicoAlvo | Indica público-alvo: comunidade em geral |  |
| Métricas | `st_pblc_alvo_crianca_0_3_anos` | publicoAlvo | Indica público-alvo: criança de 0 a 3 anos |  |
| Métricas | `st_pblc_alvo_crianca_4_5_anos` | publicoAlvo | Indica público-alvo: criança de 4 a 5 anos |  |
| Métricas | `st_pblc_alvo_crianca_6_11_anos` | publicoAlvo | Indica público-alvo: criança de 6 a 11 anos |  |
| Métricas | `st_pblc_alvo_adolescente` | publicoAlvo | Indica público-alvo: adolescente |  |
| Métricas | `st_pblc_alvo_mulher` | publicoAlvo | Indica público-alvo: mulher |  |
| Métricas | `st_pblc_alvo_gestante` | publicoAlvo | Indica público-alvo: gestante |  |
| Métricas | `st_pblc_alvo_homem` | publicoAlvo | Indica público-alvo: homem |  |
| Métricas | `st_pblc_alvo_familiares` | publicoAlvo | Indica público-alvo: familiares |  |
| Métricas | `st_pblc_alvo_idoso` | publicoAlvo | Indica público-alvo: idoso |  |
| Métricas | `st_pblc_alvo_pessoa_doen_croni` | publicoAlvo | Indica público-alvo: pessoa com doença crônica |  |
| Métricas | `st_pblc_alvo_usuario_tabaco` | publicoAlvo | Indica público-alvo: usuário de tabaco |  |
| Métricas | `st_pblc_alvo_usuario_alcool` | publicoAlvo | Indica público-alvo: usuário de álcool |  |
| Métricas | `st_pblc_alvo_usuario_outr_drog` | publicoAlvo | Indica público-alvo: usuário de outras drogas |  |
| Métricas | `st_pblc_alvo_pes_sofr_trtm_men` | publicoAlvo | Indica público-alvo: pessoa que sofre com transtorno mental |  |
| Métricas | `st_pblc_alvo_profiss_educacao` | publicoAlvo | Indica público-alvo: profissional de educação |  |
| Métricas | `st_pblc_alvo_adol_socied_aber` | publicoAlvo | Indica público-alvo: adolescente em atendimento socioeducativo em meio aberto |  |
| Métricas | `st_pblc_alvo_adol_socied_fech` | publicoAlvo | Indica público-alvo: adolescente em atendimento socioeducativo em meio fechado |  |
| Métricas | `st_pblc_alvo_outros` | publicoAlvo | Indica público-alvo: outros |  |
| Métricas | `st_tema_saude_comb_aedes_aegyp` | temasParaSaude | Indica tema para saúde: combate ao Aedes aegypti |  |
| Métricas | `st_tema_saude_agravos_negligen` | temasParaSaude | Indica tema para saúde: agravos negligenciados |  |
| Métricas | `st_tema_saude_aliment_saudavel` | temasParaSaude | Indica tema para saúde: alimentação saudável |  |
| Métricas | `st_tema_saude_pess_doenc_croni` | temasParaSaude | Indica tema para saúde: pessoa com doenças crônicas |  |
| Métricas | `st_tema_saude_cidad_dirt_human` | temasParaSaude | Indica tema para saúde: cidadania e direitos humanos |  |
| Métricas | `st_tema_saude_dependen_quimica` | temasParaSaude | Indica tema para saúde: dependência química |  |
| Métricas | `st_tema_saude_envelhecimento` | temasParaSaude | Indica tema para saúde: envelhecimento |  |
| Métricas | `st_tema_saude_pant_medic_fitot` | temasParaSaude | Indica tema para saúde: plantas medicinais/fitoterapia |  |
| Métricas | `st_tema_saude_preven_violencia` | temasParaSaude | Indica tema para saúde: prevenção da violência |  |
| Métricas | `st_tema_saude_saude_ambiental` | temasParaSaude | Indica tema para saúde: saúde ambiental |  |
| Métricas | `st_tema_saude_saude_bucal` | temasParaSaude | Indica tema para saúde: saúde bucal |  |
| Métricas | `st_tema_saude_saude_trabalhad` | temasParaSaude | Indica tema para saúde: saúde do trabalhador |  |
| Métricas | `st_tema_saude_saude_mental` | temasParaSaude | Indica tema para saúde: saúde mental |  |
| Métricas | `st_tema_saude_saude_sex_repro` | temasParaSaude | Indica tema para saúde: saúde sexual e reprodutiva |  |
| Métricas | `st_tema_saude_seman_saud_esco` | temasParaSaude | Indica tema para saúde: semana saúde na escola |  |
| Métricas | `st_tema_saude_amamentacao` | temasParaSaude | Indica tema para saúde: amamentação |  |
| Métricas | `st_tema_saude_intro_alimentar` | temasParaSaude | Indica tema para saúde: introdução alimentar |  |
| Métricas | `st_tema_saude_outros` | temasParaSaude | Indica tema para saúde: outros |  |
| Métricas | `st_prat_saude_antropometria` | praticasEmSaude | Indica prática em saúde: antropometria |  |
| Métricas | `st_prat_saude_aplic_topi_fluor` | praticasEmSaude | Indica prática em saúde: aplicação tópica de flúor |  |
| Métricas | `st_prat_saude_desenv_linguagem` | praticasEmSaude | Indica prática em saúde: teste de desenvolvimento de linguagem |  |
| Métricas | `st_prat_saude_escov_supervisio` | praticasEmSaude | Indica prática em saúde: escovação dental supervisionada |  |
| Métricas | `st_prat_saude_prt_corp_atv_fis` | praticasEmSaude | Indica prática em saúde: prática corporal / atividade física |  |
| Métricas | `st_prat_saude_pnct_1` | praticasEmSaude | Indica prática em saúde: PNCT 1ª consulta |  |
| Métricas | `st_prat_saude_pnct_2` | praticasEmSaude | Indica prática em saúde: PNCT consultas de acompanhamento |  |
| Métricas | `st_prat_saude_pnct_3` | praticasEmSaude | Indica prática em saúde: PNCT consulta de desfecho |  |
| Métricas | `st_prat_saude_pnct_4` | praticasEmSaude | Indica prática em saúde: PNCT abordagem intensiva |  |
| Métricas | `st_prat_saude_saude_auditiva` | praticasEmSaude | Indica prática em saúde: saúde auditiva |  |
| Métricas | `st_prat_saude_saude_ocular` | praticasEmSaude | Indica prática em saúde: saúde ocular |  |
| Métricas | `st_prat_saude_situacao_vacinal` | praticasEmSaude | Indica prática em saúde: verificação de situação vacinal |  |
| Métricas | `st_prat_saude_fornec_kit_bucal` | praticasEmSaude | Indica prática em saúde: fornecimento de kit bucal |  |
| Métricas | `st_prat_saude_pnct_manutencao` | praticasEmSaude | Indica prática em saúde: PNCT manutenção |  |
| Métricas | `st_prat_saude_outras` | praticasEmSaude | Indica prática em saúde: outras |  |
| Métricas | `st_prat_saude_outro_procedimen` | praticasEmSaude | Indica prática em saúde: outro procedimento (referencia `co_dim_procedimento`) |  |
| Dim. específica | `co_dim_tipo_atividade` | — | Código do tipo de atividade. Campo `co_seq_dim_tipo_atividade` da `tb_dim_tipo_atividade` | FK → `tb_dim_tipo_atividade.co_seq_dim_tipo_atividade` |
| Dim. específica | `co_dim_procedimento` | — | Código do procedimento SIGTAP vinculado à prática em saúde. Campo `co_seq_dim_procedimento` da `tb_dim_procedimento` | FK → `tb_dim_procedimento.co_seq_dim_procedimento` |
| Dim. comum ao grupo | `co_dim_municipio` | — | Código de identificação do município. Campo `co_seq_dim_municipio` da `tb_dim_municipio` | FK → `tb_dim_municipio.co_seq_dim_municipio` **recorte municipal** |
| Dim. comum ao grupo | `co_dim_tipo_ficha` | — | Código de identificação do tipo de ficha. Campo `co_seq_dim_tipo_ficha` da `tb_dim_tipo_ficha` | FK → `tb_dim_tipo_ficha.co_seq_dim_tipo_ficha` |
| Dim. comum ao grupo | `co_dim_profissional` | — | Código de identificação do profissional responsável. Campo `co_seq_dim_profissional` da `tb_dim_profissional` | FK → `tb_dim_profissional.co_seq_dim_profissional` |
| Dim. comum ao grupo | `co_dim_cbo` | — | Código de identificação do CBO profissional responsável. Campo `co_seq_dim_cbo` da `tb_dim_cbo` | FK → `tb_dim_cbo.co_seq_dim_cbo` |
| Dim. comum ao grupo | `co_dim_unidade_saude` | — | Código de identificação da unidade de saúde do profissional responsável. Campo `co_seq_dim_unidade_saude` da `tb_dim_unidade_saude` | FK → `tb_dim_unidade_saude.co_seq_dim_unidade_saude` |
| Dim. comum ao grupo | `co_dim_equipe` | — | Código de identificação da equipe do profissional responsável. Campo `co_seq_dim_equipe` da `tb_dim_equipe` | FK → `tb_dim_equipe.co_seq_dim_equipe` |
| Dim. comum ao grupo | `co_dim_tempo` | — | Responsável por armazenar as datas de maneira estruturada. Campo `co_seq_dim_tempo` da `tb_dim_tempo` | FK → `tb_dim_tempo.co_seq_dim_tempo` **data do registro** |
| Dim. comum ao grupo | `co_dim_turno` | — | Código do turno em que a atividade foi realizada. Campo `co_seq_dim_turno` da `tb_dim_turno` | FK → `tb_dim_turno.co_seq_dim_turno` |
| Dim. comum ao grupo | `co_dim_tipo_origem_dado_transp` | — | Código da origem do dado no transporte. Campo `co_seq_dim_tp_orgm_dado_transp` da `tb_dim_tipo_origem_dado_transp` | FK → `tb_dim_tipo_origem_dado_transp.co_seq_dim_tp_orgm_dado_transp` |
| Dim. comum ao grupo | `co_dim_cds_tipo_origem` | — | Código da origem do dado no transporte. Campo `co_seq_dim_tipo_origem` da `tb_dim_tipo_origem` | FK → `tb_dim_tipo_origem.co_seq_dim_tipo_origem` |


### 2.5 Vacinação (MIV)

#### `tb_fat_vacinacao` — Tabela fato da vacinação

Fonte: <https://integracao.esusaps.bridge.ufsc.tech/dw/fatos/vacinacao/tb_fat_vacinacao.html> — **Alterado em 13/07/2026**.

Texto da doc (objetivo/regras/notas):

- A tabela `tb_fat_vacinacao` é populada sempre que os dados de um registro de Vacinação são processados. Ela armazena o cabeçalho do atendimento de vacinação.
- 1. A `tb_fat_vacinacao` é preenchida quando são processado(a)s:
  - Fichas de vacinação recebidas através da importação de sistemas terceiros, outras instalações do PEC e-SUS-APS ou do aplicativo e-SUS Vacinação;
  - Registro de um atendimento de vacinação no CDS;
  - Registro de um atendimento de vacinação no módulo de vacinação do PEC e-SUS-APS.

> Cabeçalho do atendimento de vacinação (tem o cidadão). As doses estão em `tb_fat_vacinacao_vacina`, ligadas por `co_fat_vacinacao`. Fontes listadas nas regras: importação (terceiros, outras instalações, app e-SUS Vacinação), CDS e módulo de vacinação do PEC — **nenhuma menção a RNDS/RIA**.

| Seção da doc | Coluna | LEDI | Descrição (doc oficial) | Marcação |
|---|---|---|---|---|
| Metadados | `co_seq_fat_vacinacao` | — | Código de identificação sequencial criado automaticamente pelo sistema | PK |
| Metadados | `nu_uuid_ficha` | uuidFicha | Identificador universalmente único do registro |  |
| Metadados | `nu_uuid_dado_transp` | uuidDadoSerializado | Identificador universalmente único da camada de transporte de dados. Caso o registro seja gerado dentro do PEC e-SUS-APS, terá o mesmo valor do campo nu_uuid_ficha |  |
| Metadados | `co_fat_cidadao_pec` | — | Código de identificação sequencial do cidadão. Campo `co_seq_fat_cidadao_pec` da `tb_fat_cidadao_pec` | FK → `tb_fat_cidadao_pec.co_seq_fat_cidadao_pec` **chave de pessoa** (alvo sem página na doc) |
| Metadados | `nu_cns` | cnsCidadao | CNS do cidadão | **[PII — nunca projetar]** |
| Metadados | `nu_cpf_cidadao` | cpfCidadao | CPF do cidadão | **[PII — nunca projetar]** |
| Metadados | `nu_prontuario` | numProntuario | Número do prontuário do cidadão criptografado | **[PII — nunca projetar]** |
| Metadados | `nu_atendimento` | — | Número sequencial do atendimento na ficha. Uma ficha pode conter mais de um atendimento, esse campo é utilizado para ordenar os atendimentos dentro um mesmo envio |  |
| Metadados | `dt_inicial_atendimento` | dataHoraInicialAtendimento | Data e hora de início do atendimento no formato YYYY-MM-DD HH:MM:SS.MMM |  |
| Metadados | `dt_final_atendimento` | dataHoraFinalAtendimento | Data e hora de término do atendimento no formato YYYY-MM-DD HH:MM:SS.MMM |  |
| Métricas | `dt_nascimento` | dtNascimento | Data de nascimento do cidadão | *[sensível — projetar só na capacidade "cidadão"]* |
| Métricas | `st_viajante` | viajante | Indica se o cidadão é viajante |  |
| Métricas | `st_comunicante_hanseniase` | comunicanteHanseniase | Indica se o cidadão é comunicante de hanseníase |  |
| Métricas | `ds_filtro_imunobiologico` | — | Campo de filtro com os imunobiológicos aplicados |  |
| Métricas | `ds_filtro_estrategia_vacinacao` | — | Campo de filtro com as estratégias de vacinação utilizadas |  |
| Métricas | `ds_filtro_dose_imunobiologico` | — | Campo de filtro com as doses dos imunobiológicos |  |
| Métricas | `ds_filtro_lote` | — | Campo de filtro com os lotes das vacinas |  |
| Métricas | `ds_filtro_fabricante` | — | Campo de filtro com os fabricantes das vacinas |  |
| Métricas | `ds_filtro_grupo_atendimento` | — | Campo de filtro com os grupos de atendimento |  |
| Métricas | `st_nao_possui_cpf` | — | Indica se o cidadão não possui CPF |  |
| Dim. específica | `co_dim_faixa_etaria` | — | Código da faixa etária do cidadão. Campo `co_seq_dim_faixa_etaria` da `tb_dim_faixa_etaria` | FK → `tb_dim_faixa_etaria.co_seq_dim_faixa_etaria` |
| Dim. específica | `co_dim_sexo` | — | Código do sexo do cidadão. Campo `co_seq_dim_faixa_sexo` da `tb_dim_sexo` | FK → `tb_dim_sexo.co_seq_dim_faixa_sexo` ⚠ PK real da dimensão é `co_seq_dim_sexo` |
| Dim. específica | `co_dim_local_atendimento` | — | Código do local de atendimento. Campo `co_seq_dim_local_atendimento` da `tb_dim_local_atendimento` | FK → `tb_dim_local_atendimento.co_seq_dim_local_atendimento` |
| Dim. específica | `co_dim_turno` | — | Código do turno do atendimento. Campo `co_seq_dim_turno` da `tb_dim_turno` | FK → `tb_dim_turno.co_seq_dim_turno` |
| Dim. específica | `co_dim_condicao_maternal` | — | Código da condição maternal da cidadã. Campo `co_seq_dim_condicao_maternal` da `tb_dim_condicao_maternal` | FK → `tb_dim_condicao_maternal.co_seq_dim_condicao_maternal` |
| Dim. específica | `co_dim_just_nao_possui_cpf` | — | Código da justificativa de não possuir CPF. Campo `co_seq_dim_just_nao_possui_cpf` da `tb_dim_just_nao_possui_cpf` | FK → `tb_dim_just_nao_possui_cpf.co_seq_dim_just_nao_possui_cpf` |
| Dim. comum ao grupo | `co_dim_municipio` | — | Código de identificação do município. Campo `co_seq_dim_municipio` da `tb_dim_municipio` | FK → `tb_dim_municipio.co_seq_dim_municipio` **recorte municipal** |
| Dim. comum ao grupo | `co_dim_tipo_ficha` | — | Código de identificação do tipo de ficha. Campo `co_seq_dim_tipo_ficha` da `tb_dim_tipo_ficha` | FK → `tb_dim_tipo_ficha.co_seq_dim_tipo_ficha` |
| Dim. comum ao grupo | `co_dim_profissional` | — | Código de identificação do profissional responsável. Campo `co_seq_dim_profissional` da `tb_dim_profissional` | FK → `tb_dim_profissional.co_seq_dim_profissional` |
| Dim. comum ao grupo | `co_dim_cbo` | — | Código de identificação do CBO profissional responsável. Campo `co_seq_dim_cbo` da `tb_dim_cbo` | FK → `tb_dim_cbo.co_seq_dim_cbo` |
| Dim. comum ao grupo | `co_dim_unidade_saude` | — | Código de identificação da unidade de saúde do profissional responsável. Campo `co_seq_dim_unidade_saude` da `tb_dim_unidade_saude` | FK → `tb_dim_unidade_saude.co_seq_dim_unidade_saude` |
| Dim. comum ao grupo | `co_dim_equipe` | — | Código de identificação da equipe do profissional responsável. Campo `co_seq_dim_equipe` da `tb_dim_equipe` | FK → `tb_dim_equipe.co_seq_dim_equipe` |
| Dim. comum ao grupo | `co_dim_tempo` | — | Responsável por armazenar as datas de maneira estruturada. Campo `co_seq_dim_tempo` da `tb_dim_tempo` | FK → `tb_dim_tempo.co_seq_dim_tempo` **data do registro** |
| Dim. comum ao grupo | `co_dim_tipo_origem_dado_transp` | — | Código da origem do dado no transporte. Campo `co_seq_dim_tp_orgm_dado_transp` da `tb_dim_tipo_origem_dado_transp` | FK → `tb_dim_tipo_origem_dado_transp.co_seq_dim_tp_orgm_dado_transp` |
| Dim. comum ao grupo | `co_dim_cds_tipo_origem` | — | Código da origem do dado no transporte. Campo `co_seq_dim_tipo_origem` da `tb_dim_tipo_origem` | FK → `tb_dim_tipo_origem.co_seq_dim_tipo_origem` |

#### `tb_fat_vacinacao_vacina` — Tabela fato das vacinas aplicadas

Fonte: <https://integracao.esusaps.bridge.ufsc.tech/dw/fatos/vacinacao/tb_fat_vacinacao_vacina.html> — **Alterado em 13/07/2026**.

Texto da doc (objetivo/regras/notas):

- A tabela `tb_fat_vacinacao_vacina` registra cada vacina aplicada em um atendimento de vacinação processado. Cada linha representa uma vacina individual e está vinculada à `tb_fat_vacinacao` pelo campo `co_fat_vacinacao`.
- 1. A `tb_fat_vacinacao_vacina` é preenchida quando é processado o registro de ao menos uma vacina em um atendimento de vacinação.

> **Não tem `co_fat_cidadao_pec`**: o cidadão vem do cabeçalho (`co_fat_vacinacao` → `tb_fat_vacinacao.co_seq_fat_vacinacao`). `st_registro_anterior` e `co_dim_tempo_vacina_aplicada` entraram na v4.1.4→4.2.0. LEDI FV: `stRegistroAnterior` = "imunobiológico aplicado em atendimento anterior" (a página de regras de vacinação chama isso de **"Transcrição de caderneta"**) e `dataRegistroAnterior` = "data em que foi aplicada a vacina" (obrigatória quando `stRegistroAnterior` = true). Inferido: para transcrição, a data de aplicação está em `co_dim_tempo_vacina_aplicada` e `co_dim_tempo` é a data do registro.

| Seção da doc | Coluna | LEDI | Descrição (doc oficial) | Marcação |
|---|---|---|---|---|
| Metadados | `co_seq_fat_vacinacao_vacina` | — | Código de identificação sequencial criado automaticamente pelo sistema | PK |
| Metadados | `co_fat_vacinacao` | — | Código de identificação da vacinação. Campo `co_seq_fat_vacinacao` da `tb_fat_vacinacao` | FK → `tb_fat_vacinacao.co_seq_fat_vacinacao` |
| Metadados | `nu_uuid_ficha` | uuidFicha | Identificador universalmente único do registro |  |
| Metadados | `nu_uuid_dado_transp` | — | Identificador universalmente único da camada de transporte de dados |  |
| Metadados | `nu_atendimento` | — | Número sequencial do atendimento na ficha. Uma ficha pode conter mais de um atendimento, esse campo é utilizado para ordenar os atendimentos dentro de um mesmo envio |  |
| Métricas | `no_lote` | — | Número do lote da vacina aplicada | *[não necessário]* |
| Métricas | `st_registro_anterior` | — | Indica se a vacina foi aplicada anteriormente (registro retroativo) |  |
| Métricas | `st_aplicado_exterior` | — | Indica se a vacina foi aplicada no exterior |  |
| Métricas | `st_pesquisa_clinica` | — | Indica se a vacina faz parte de uma pesquisa clínica |  |
| Métricas | `ds_anvisa_protocolo_estudo` | — | Indica o número do protocolo do estudo clínico na Anvisa. |  |
| Métricas | `ds_anvisa_protocolo_versao` | — | Indica o número da versão do protocolo do estudo na Anvisa. |  |
| Métricas | `ds_anvisa_numero_registro` | — | Indica o número do registro sanitário da vacina na Anvisa. |  |
| Dim. específica | `co_dim_imunobiologico` | — | Código do imunobiológico aplicado. Campo `co_seq_dim_imunobiologico` da `tb_dim_imunobiologico` | FK → `tb_dim_imunobiologico.co_seq_dim_imunobiologico` |
| Dim. específica | `co_dim_estrategia_vacinacao` | — | Código da estratégia de vacinação. Campo `co_seq_dim_estrategia_vacinacao` da `tb_dim_estrategia_vacinacao` | FK → `tb_dim_estrategia_vacinacao.co_seq_dim_estrategia_vacinacao` |
| Dim. específica | `co_dim_dose_imunobiologico` | — | Código da dose do imunobiológico aplicada. Campo `co_seq_dim_dose_imunobiologico` da `tb_dim_dose_imunobiologico` | FK → `tb_dim_dose_imunobiologico.co_seq_dim_dose_imunobiologico` |
| Dim. específica | `co_dim_grupo_atendimento` | — | Código do grupo de atendimento da vacinação. Campo `co_seq_dim_grupo_atendimento` da `tb_dim_grupo_atendimento` | FK → `tb_dim_grupo_atendimento.co_seq_dim_grupo_atendimento` |
| Dim. específica | `co_dim_tempo_vacina_aplicada` | — | Data em que a vacina foi aplicada. Campo `co_seq_dim_tempo` da `tb_dim_tempo` | FK → `tb_dim_tempo.co_seq_dim_tempo` **data de aplicação** |
| Dim. específica | `co_dim_cbo_prescritor` | — | Código do CBO do profissional que prescreveu a vacina. Campo `co_seq_dim_cbo` da `tb_dim_cbo` | FK → `tb_dim_cbo.co_seq_dim_cbo` |
| Dim. específica | `co_dim_cid_motivo_indicacao` | — | Código do CID10 que motivou a indicação da vacina. Campo `co_seq_dim_cid10` da `tb_dim_cid10` | FK → `tb_dim_cid10.co_seq_dim_cid10` ⚠ aponta `tb_dim_cid10` (sem página) |
| Dim. específica | `co_dim_via_adm_vacina` | — | Código da via de administração da vacina. Campo `co_seq_dim_via_adm_vacina` da `tb_dim_via_adm_vacina` | FK → `tb_dim_via_adm_vacina.co_seq_dim_via_adm_vacina` |
| Dim. específica | `co_dim_local_apl_vacina` | — | Código do local de aplicação da vacina. Campo `co_seq_dim_local_apl_vacina` da `tb_dim_local_apl_vacina` | FK → `tb_dim_local_apl_vacina.co_seq_dim_local_apl_vacina` |
| Dim. específica | `co_dim_imunobiologico_fabrc` | — | Código do fabricante do imunobiológico. Campo `co_seq_dim_imunobiologico_fabrc` da `tb_dim_imunobiologico_fabrc` | FK → `tb_dim_imunobiologico_fabrc.co_seq_dim_imunobiologico_fabrc` ⚠ dimensão-alvo sem página na doc |
| Dim. comum ao grupo | `co_dim_municipio` | — | Código de identificação do município. Campo `co_seq_dim_municipio` da `tb_dim_municipio` | FK → `tb_dim_municipio.co_seq_dim_municipio` **recorte municipal** |
| Dim. comum ao grupo | `co_dim_tipo_ficha` | — | Código de identificação do tipo de ficha. Campo `co_seq_dim_tipo_ficha` da `tb_dim_tipo_ficha` | FK → `tb_dim_tipo_ficha.co_seq_dim_tipo_ficha` |
| Dim. comum ao grupo | `co_dim_profissional` | — | Código de identificação do profissional responsável. Campo `co_seq_dim_profissional` da `tb_dim_profissional` | FK → `tb_dim_profissional.co_seq_dim_profissional` |
| Dim. comum ao grupo | `co_dim_cbo` | — | Código de identificação do CBO profissional responsável. Campo `co_seq_dim_cbo` da `tb_dim_cbo` | FK → `tb_dim_cbo.co_seq_dim_cbo` |
| Dim. comum ao grupo | `co_dim_unidade_saude` | — | Código de identificação da unidade de saúde do profissional responsável. Campo `co_seq_dim_unidade_saude` da `tb_dim_unidade_saude` | FK → `tb_dim_unidade_saude.co_seq_dim_unidade_saude` |
| Dim. comum ao grupo | `co_dim_equipe` | — | Código de identificação da equipe do profissional responsável. Campo `co_seq_dim_equipe` da `tb_dim_equipe` | FK → `tb_dim_equipe.co_seq_dim_equipe` |
| Dim. comum ao grupo | `co_dim_tempo` | — | Responsável por armazenar as datas de maneira estruturada. Campo `co_seq_dim_tempo` da `tb_dim_tempo` | FK → `tb_dim_tempo.co_seq_dim_tempo` **data do registro** |
| Dim. comum ao grupo | `co_dim_tipo_origem_dado_transp` | — | Código da origem do dado no transporte. Campo `co_seq_dim_tp_orgm_dado_transp` da `tb_dim_tipo_origem_dado_transp` | FK → `tb_dim_tipo_origem_dado_transp.co_seq_dim_tp_orgm_dado_transp` |
| Dim. comum ao grupo | `co_dim_cds_tipo_origem` | — | Código da origem do dado no transporte. Campo `co_seq_dim_tipo_origem` da `tb_dim_tipo_origem` | FK → `tb_dim_tipo_origem.co_seq_dim_tipo_origem` |


### 2.6 Atendimento odontológico (MIAO)

#### `tb_fat_atendimento_odonto` — Tabela fato do atendimento odontológico

Fonte: <https://integracao.esusaps.bridge.ufsc.tech/dw/fatos/atendimento_odontologico/tb_fat_atendimento_odonto.html> — **Alterado em 11/09/2026**.

Texto da doc (objetivo/regras/notas):

- A tabela `tb_fat_atendimento_odonto` é populada sempre que os dados de um atendimento odontológico são processados.
- 1. A `tb_fat_atendimento_odonto` é preenchida quando são processado(a)s:
  - Fichas de atendimento odontológico recebidas através da importação de sistemas terceiros ou outras instalações do PEC;
  - Registro de um atendimento odontológico;
  - Registro de uma escuta inicial com profissional de nível superior de odontologia;
  - Registro de um atendimento odontológico no CDS.

> Inclui escuta inicial com profissional de nível superior de odontologia (regras). `nu_peso`/`nu_altura` entraram na v4.2.1→4.2.3. Sem PA.

| Seção da doc | Coluna | LEDI | Descrição (doc oficial) | Marcação |
|---|---|---|---|---|
| Metadados | `co_seq_fat_atd_odnt` | — | Código de identificação sequencial criado automaticamente pelo sistema | PK |
| Metadados | `co_fat_cidadao_pec` | — | Campo `co_seq_fat_cidadao_pec` da `tb_fat_cidadao_pec` | FK → `tb_fat_cidadao_pec.co_seq_fat_cidadao_pec` **chave de pessoa** (alvo sem página na doc) |
| Metadados | `nu_prontuario` | numProntuario | Número do prontuário criptografado | **[PII — nunca projetar]** |
| Metadados | `nu_uuid_ficha` | uuidFicha | Identificador universalmente único do registro |  |
| Metadados | `nu_uuid_dado_transp` | uuidDadoSerializado | Identificador universalmente único da camada de transporte de dados. Caso o registro seja gerado dentro do PEC terá o mesmo valor do campo nu_uuid_ficha |  |
| Metadados | `nu_atendimento` | — | Uma ficha pode conter mais de um atendimento esse campo é utilizado para ordenar os atendimentos dentro um mesmo envio |  |
| Metadados | `nu_cpf_cidadao` | cpfCidadao | CPF do cidadão | **[PII — nunca projetar]** |
| Metadados | `nu_cns` | cnsCidadao | CNS do cidadão | **[PII — nunca projetar]** |
| Metadados | `st_nao_possui_cpf` | stCidadaoNaoPossuiCpf | Indica se o cidadão não possui CPF |  |
| Métricas | `dt_nascimento` | dtNascimento | Data de nascimento do cidadão | *[sensível — projetar só na capacidade "cidadão"]* |
| Métricas | `st_paciente_necessidades_espec` | necessidadesEspeciais | Indica se o atendimento é a pessoa com necessidades especiais |  |
| Métricas | `st_gestante` | gestante | Indica se o atendimento é a pessoa gestante |  |
| Métricas | `st_vigil_abscesso_dentoalveola` | tiposVigilanciaSaudeBucal | Indica ação de vigilância em saúde bucal por abscesso dentoalveolar |  |
| Métricas | `st_vigil_alterac_tecidos_moles` | tiposVigilanciaSaudeBucal | Indica ação de vigilância em saúde bucal por alteração em tecidos moles |  |
| Métricas | `st_vigil_dor_dente` | tiposVigilanciaSaudeBucal | Indica ação de vigilância em saúde bucal por dor de dente |  |
| Métricas | `st_vigil_fendas_fissuras_labio` | tiposVigilanciaSaudeBucal | Indica ação de vigilância em saúde bucal por fendas ou fissuras lábio palatais |  |
| Métricas | `st_vigil_fluorose_dentaria` | tiposVigilanciaSaudeBucal | Indica ação de vigilância em saúde bucal por fluorose dentária moderada ou severa |  |
| Métricas | `st_vigil_traumat_dentoalveolar` | tiposVigilanciaSaudeBucal | Indica ação de vigilância em saúde bucal por traumatismo dentoalveolar |  |
| Métricas | `st_vigil_nao_identificado` | tiposVigilanciaSaudeBucal | Indica ação de vigilância em saúde bucal por motivo não identificado |  |
| Métricas | `st_fornecimento_escova_dental` | tiposFornecimOdonto | Indica se foi realizado o fornecimento de escova dental |  |
| Métricas | `st_fornecimento_creme_dental` | tiposFornecimOdonto | Indica se foi realizado o fornecimento de creme dental |  |
| Métricas | `st_fornecimento_fio_dental` | tiposFornecimOdonto | Indica se foi realizado o fornecimento de fio dental |  |
| Métricas | `nu_peso` | peso | Peso do cidadão em quilogramas |  |
| Métricas | `nu_altura` | altura | Altura do cidadão em centímetros |  |
| Métricas | `st_conduta_outros_profissio_ab` | tiposEncamOdonto | Indica a conduta "Agendamento para outros profissionais AB" |  |
| Métricas | `st_conduta_consulta_agendada` | tiposEncamOdonto | Indica a conduta "Retorno para consulta agendada" |  |
| Métricas | `st_conduta_agendamento_grupos` | tiposEncamOdonto | Indica a conduta "Agendamento para grupos" |  |
| Métricas | `st_conduta_agendamento_nasf` | tiposEncamOdonto | Indica a conduta "Agendamento para eMulti" |  |
| Métricas | `st_conduta_alta_episodio` | tiposEncamOdonto | Indica a conduta "Alta do episódio" |  |
| Métricas | `st_conduta_tratamento_concluid` | tiposEncamOdonto | Indica a conduta "Tratamento concluído" |  |
| Métricas | `st_encaminhamento_necess_espec` | tiposEncamOdonto | Indica o encaminhamento "Atendimento à pacientes com necessidades especiais" |  |
| Métricas | `st_encaminhamento_cirurgia_bmf` | tiposEncamOdonto | Indica o encaminhamento "Cirurgia BMF" |  |
| Métricas | `st_encaminhamento_endodontia` | tiposEncamOdonto | Indica o encaminhamento "Endodontia" |  |
| Métricas | `st_encaminhamento_estomatologi` | tiposEncamOdonto | Indica o encaminhamento "Estomatologia" |  |
| Métricas | `st_encaminhamento_implantodont` | tiposEncamOdonto | Indica o encaminhamento "Implantodontia" |  |
| Métricas | `st_encaminhamento_odontopediat` | tiposEncamOdonto | Indica o encaminhamento "Odontopediatria" |  |
| Métricas | `st_encaminhamento_ortod_ortop` | tiposEncamOdonto | Indica o encaminhamento "Ortodontia / Ortopedia" |  |
| Métricas | `st_encaminhamento_periodontia` | tiposEncamOdonto | Indica o encaminhamento "Periodontia" |  |
| Métricas | `st_encaminhamento_protese_dent` | tiposEncamOdonto | Indica o encaminhamento "Prótese dentária" |  |
| Métricas | `st_encaminhamento_radiologia` | tiposEncamOdonto | Indica o encaminhamento "Radiologia" |  |
| Métricas | `st_encaminhamento_outros` | tiposEncamOdonto | Indica o encaminhamento "Outros" |  |
| Métricas | `st_encaminhamento_nao_aplica` | — | - |  |
| Métricas | `ds_filtro_cids` | — | Agrupa todos as CID 10 registradas na Avaliação do atendimento |  |
| Métricas | `ds_filtro_ciaps` | — | Agrupa todas as CIAPs registradas na Avaliação do atendimento |  |
| Métricas | `ds_filtro_procedimentos` | — | Agrupa todos os procedimentos registrados no atendimento |  |
| Métricas | `dt_inicial_atendimento` | dataHoraInicialAtendimento | Data e hora do início do atendimento no formato "YYYY-MM-DD HH:MM:SS.MMM" |  |
| Métricas | `dt_final_atendimento` | dataHoraFinalAtendimento | Data e hora do fim do atendimento no formato "YYYY-MM-DD HH:MM:SS.MMM" |  |
| Métricas | `st_conduta_agendamento_emulti` | tiposEncamOdonto | Indica a conduta "Agendamento para eMulti" |  |
| Dim. específica | `co_dim_faixa_etaria` | — | Código da faixa etária. Campo `co_seq_dim_faixa_etaria` da `tb_dim_faixa_etaria` | FK → `tb_dim_faixa_etaria.co_seq_dim_faixa_etaria` |
| Dim. específica | `co_dim_sexo` | — | Código do sexo. Campo `co_seq_dim_faixa_sexo` da `tb_dim_sexo` | FK → `tb_dim_sexo.co_seq_dim_faixa_sexo` ⚠ PK real da dimensão é `co_seq_dim_sexo` |
| Dim. específica | `co_dim_turno` | — | Código do turno do atendimento. Campo `co_seq_dim_turno` da `tb_dim_turno` | FK → `tb_dim_turno.co_seq_dim_turno` |
| Dim. específica | `co_dim_local_atendimento` | — | Código do local de atendimento. Campo `co_seq_dim_local_atendimento` da `tb_dim_local_atendimento` | FK → `tb_dim_local_atendimento.co_seq_dim_local_atendimento` |
| Dim. específica | `co_dim_tipo_atendimento` | — | Código do tipo de atendimento. Campo `co_seq_dim_tipo_atendimento` da `tb_dim_tipo_atendimento` | FK → `tb_dim_tipo_atendimento.co_seq_dim_tipo_atendimento` |
| Dim. específica | `co_dim_tipo_consulta` | — | Código do tipo de atendimento. Campo `co_seq_dim_tipo_cnsulta_odonto` da `tb_dim_tipo_consulta_odonto` | FK → `tb_dim_tipo_consulta_odonto.co_seq_dim_tipo_cnsulta_odonto` |
| Dim. específica | `co_dim_tp_particip_cidadao` | — | Código do tipo de participação do cidadão. Campo `co_seq_dim_tp_particip_atend` da `tb_dim_tp_participacao_atend` | FK → `tb_dim_tp_participacao_atend.co_seq_dim_tp_particip_atend` ⚠ dimensão-alvo sem página; nome difere do FAI |
| Dim. específica | `co_dim_tp_particip_prof_conv` | — | Código do tipo de participação do profissional convidado. Campo `co_seq_dim_tp_particip_atend` da `tb_dim_tp_participacao_atend` | FK → `tb_dim_tp_participacao_atend.co_seq_dim_tp_particip_atend` ⚠ dimensão-alvo sem página; nome difere do FAI |
| Dim. específica | `co_dim_just_nao_possui_cpf` | — | Código da justificativa de não possuir CPF. Campo `co_seq_dim_just_nao_possui_cpf` da `tb_dim_just_nao_possui_cpf` | FK → `tb_dim_just_nao_possui_cpf.co_seq_dim_just_nao_possui_cpf` |
| Dim. comum ao grupo | `co_dim_municipio` | — | Código de identificação do município. Campo `co_seq_dim_municipio` da `tb_dim_municipio` | FK → `tb_dim_municipio.co_seq_dim_municipio` **recorte municipal** |
| Dim. comum ao grupo | `co_dim_tipo_ficha` | — | Código de identificação do tipo de ficha. Campo `co_seq_dim_tipo_ficha` da `tb_dim_tipo_ficha` | FK → `tb_dim_tipo_ficha.co_seq_dim_tipo_ficha` |
| Dim. comum ao grupo | `co_dim_profissional_1` | — | Código de identificação do profissional responsável. Campo `co_seq_dim_profissional` da `tb_dim_profissional` | FK → `tb_dim_profissional.co_seq_dim_profissional` |
| Dim. comum ao grupo | `co_dim_profissional_2` | — | Código de identificação do profissional auxiliar. Campo `co_seq_dim_profissional` da `tb_dim_profissional` | FK → `tb_dim_profissional.co_seq_dim_profissional` |
| Dim. comum ao grupo | `co_dim_cbo_1` | — | Código de identificação do CBO profissional responsável. Campo `co_seq_dim_cbo` da `tb_dim_cbo` | FK → `tb_dim_cbo.co_seq_dim_cbo` |
| Dim. comum ao grupo | `co_dim_cbo_2` | — | Código de identificação do CBO profissional auxiliar. Campo `co_seq_dim_cbo` da `tb_dim_cbo` | FK → `tb_dim_cbo.co_seq_dim_cbo` |
| Dim. comum ao grupo | `co_dim_unidade_saude_1` | — | Código de identificação da unidade de saúde do profissional responsável. Campo `co_seq_dim_unidade_saude` da `tb_dim_unidade_saude` | FK → `tb_dim_unidade_saude.co_seq_dim_unidade_saude` |
| Dim. comum ao grupo | `co_dim_unidade_saude_2` | — | Código de identificação da unidade de saúde do profissional auxiliar. Campo `co_seq_dim_unidade_saude` da `tb_dim_unidade_saude` | FK → `tb_dim_unidade_saude.co_seq_dim_unidade_saude` |
| Dim. comum ao grupo | `co_dim_equipe_1` | — | Código de identificação da equipe do profissional responsável. Campo `co_seq_dim_equipe` da `tb_dim_equipe` | FK → `tb_dim_equipe.co_seq_dim_equipe` |
| Dim. comum ao grupo | `co_dim_equipe_2` | — | Código de identificação da equipe do profissional auxiliar. Campo `co_seq_dim_equipe` da `tb_dim_equipe` | FK → `tb_dim_equipe.co_seq_dim_equipe` |
| Dim. comum ao grupo | `co_dim_tipo_origem_dado_transp` | — | Código da origem do dado no transporte. Campo `co_seq_dim_tp_orgm_dado_transp` da `tb_dim_tipo_origem_dado_transp` | FK → `tb_dim_tipo_origem_dado_transp.co_seq_dim_tp_orgm_dado_transp` |
| Dim. comum ao grupo | `co_dim_cds_tipo_origem` | — | Código da origem do dado no transporte. Campo `co_seq_dim_tipo_origem` da `tb_dim_tipo_origem` | FK → `tb_dim_tipo_origem.co_seq_dim_tipo_origem` |
| Dim. comum ao grupo | `co_dim_tempo` | — | Responsável por armazenar as datas de maneira estruturada. Campo `co_seq_dim_tempo` da `tb_dim_tempo` | FK → `tb_dim_tempo.co_seq_dim_tempo` **data do registro** |

#### `tb_fat_atend_odonto_proced` — Tabela fato dos procedimentos do atendimento odontológico

Fonte: <https://integracao.esusaps.bridge.ufsc.tech/dw/fatos/atendimento_odontologico/tb_fat_atend_odonto_proced.html> — **Alterado em 13/07/2026**.

Texto da doc (objetivo/regras/notas):

- A tabela `tb_fat_atend_odonto_proced` só é preenchida quando são processados procedimentos registrados em um atendimento odontológico e o atendimento em questão é processado.

> Procedimentos realizados no atendimento odontológico (`co_dim_procedimento` + `qt_procedimentos`).

| Seção da doc | Coluna | LEDI | Descrição (doc oficial) | Marcação |
|---|---|---|---|---|
| Metadados | `co_seq_fat_atend_odonto_proced` | — | Código de identificação sequencial dos resultado de exame registrados | PK |
| Metadados | `co_fat_atd_odnt` | — | Código de identificação sequencial do atendimento odontológico. Campo `co_seq_fat_atd_odnt` da `tb_fat_atendimento_odonto` | FK → `tb_fat_atendimento_odonto.co_seq_fat_atd_odnt` |
| Metadados | `co_fat_cidadao_pec` | — | Campo `co_seq_fat_cidadao_pec` da `tb_fat_cidadao_pec` | FK → `tb_fat_cidadao_pec.co_seq_fat_cidadao_pec` **chave de pessoa** (alvo sem página na doc) |
| Metadados | `nu_uuid_ficha` | uuidFicha | Identificador universalmente único da camada de transporte de dados |  |
| Metadados | `nu_uuid_dado_transp` | — | Identificador universalmente único da camada de transporte de dados. Caso o registro seja gerado dentro do PEC terá o mesmo valor do campo nu_uuid_ficha |  |
| Metadados | `nu_atendimento` | — | Uma ficha pode conter mais de um atendimento esse campo é utilizado para ordenar os atendimentos dentro um mesmo envio |  |
| Metadados | `nu_cpf_cidadao` | cpfCidadao | CPF do cidadão | **[PII — nunca projetar]** |
| Metadados | `nu_cns` | cnsCidadao | CNS do cidadão | **[PII — nunca projetar]** |
| Métricas | `qt_procedimentos` | quantidade | Quantidade de procedimentos realizados. |  |
| Métricas | `dt_inicial_atendimento` | dataHoraInicialAtendimento | Data e hora do início do atendimento no formato "YYYY-MM-DD HH:MM:SS.MMM" |  |
| Dim. específica | `co_dim_procedimento` | — | Código SIGTAP do procedimento realizado. Campo `co_seq_dim_procedimento` da `tb_dim_procedimento` | FK → `tb_dim_procedimento.co_seq_dim_procedimento` |
| Dim. específica | `co_dim_sexo` | — | Código do sexo. Campo `co_seq_dim_faixa_sexo` da `tb_dim_sexo` | FK → `tb_dim_sexo.co_seq_dim_faixa_sexo` ⚠ PK real da dimensão é `co_seq_dim_sexo` |
| Dim. específica | `co_dim_turno` | — | Código do turno do atendimento. Campo `co_seq_dim_turno` da `tb_dim_turno` | FK → `tb_dim_turno.co_seq_dim_turno` |
| Dim. específica | `co_dim_tipo_atendimento` | — | Código do tipo de atendimento. Campo `co_seq_dim_tipo_atendimento` da `tb_dim_tipo_atendimento` | FK → `tb_dim_tipo_atendimento.co_seq_dim_tipo_atendimento` |
| Dim. específica | `co_dim_faixa_etaria` | — | Código da faixa etária. Campo `co_seq_dim_faixa_etaria` da `tb_dim_faixa_etaria` | FK → `tb_dim_faixa_etaria.co_seq_dim_faixa_etaria` |
| Dim. comum ao grupo | `co_dim_municipio` | — | Código de identificação do município. Campo `co_seq_dim_municipio` da `tb_dim_municipio` | FK → `tb_dim_municipio.co_seq_dim_municipio` **recorte municipal** |
| Dim. comum ao grupo | `co_dim_tipo_ficha` | — | Código de identificação do tipo de ficha. Campo `co_seq_dim_tipo_ficha` da `tb_dim_tipo_ficha` | FK → `tb_dim_tipo_ficha.co_seq_dim_tipo_ficha` |
| Dim. comum ao grupo | `co_dim_profissional_1` | — | Código de identificação do profissional responsável. Campo `co_seq_dim_profissional` da `tb_dim_profissional` | FK → `tb_dim_profissional.co_seq_dim_profissional` |
| Dim. comum ao grupo | `co_dim_profissional_2` | — | Código de identificação do profissional auxiliar. Campo `co_seq_dim_profissional` da `tb_dim_profissional` | FK → `tb_dim_profissional.co_seq_dim_profissional` |
| Dim. comum ao grupo | `co_dim_cbo_1` | — | Código de identificação do CBO profissional responsável. Campo `co_seq_dim_cbo` da `tb_dim_cbo` | FK → `tb_dim_cbo.co_seq_dim_cbo` |
| Dim. comum ao grupo | `co_dim_cbo_2` | — | Código de identificação do CBO profissional auxiliar. Campo `co_seq_dim_cbo` da `tb_dim_cbo` | FK → `tb_dim_cbo.co_seq_dim_cbo` |
| Dim. comum ao grupo | `co_dim_unidade_saude_1` | — | Código de identificação da unidade de saúde do profissional responsável. Campo `co_seq_dim_unidade_saude` da `tb_dim_unidade_saude` | FK → `tb_dim_unidade_saude.co_seq_dim_unidade_saude` |
| Dim. comum ao grupo | `co_dim_unidade_saude_2` | — | Código de identificação da unidade de saúde do profissional auxiliar. Campo `co_seq_dim_unidade_saude` da `tb_dim_unidade_saude` | FK → `tb_dim_unidade_saude.co_seq_dim_unidade_saude` |
| Dim. comum ao grupo | `co_dim_equipe_1` | — | Código de identificação da equipe do profissional responsável. Campo `co_seq_dim_equipe` da `tb_dim_equipe` | FK → `tb_dim_equipe.co_seq_dim_equipe` |
| Dim. comum ao grupo | `co_dim_equipe_2` | — | Código de identificação da equipe do profissional auxiliar. Campo `co_seq_dim_equipe` da `tb_dim_equipe` | FK → `tb_dim_equipe.co_seq_dim_equipe` |
| Dim. comum ao grupo | `co_dim_tipo_origem_dado_transp` | — | Código da origem do dado no transporte. Campo `co_seq_dim_tp_orgm_dado_transp` da `tb_dim_tipo_origem_dado_transp` | FK → `tb_dim_tipo_origem_dado_transp.co_seq_dim_tp_orgm_dado_transp` |
| Dim. comum ao grupo | `co_dim_cds_tipo_origem` | — | Código da origem do dado no transporte. Campo `co_seq_dim_tipo_origem` da `tb_dim_tipo_origem` | FK → `tb_dim_tipo_origem.co_seq_dim_tipo_origem` |
| Dim. comum ao grupo | `co_dim_tempo` | — | Responsável por armazenar as datas de maneira estruturada. Campo `co_seq_dim_tempo` da `tb_dim_tempo` | FK → `tb_dim_tempo.co_seq_dim_tempo` **data do registro** |


### 2.7 Cadastro individual e domiciliar (MICI / MICDT)

#### `tb_fat_cad_individual` — Tabela fato do cadastro individual

Fonte: <https://integracao.esusaps.bridge.ufsc.tech/dw/fatos/cadastro_individual/tb_fat_cad_individual.html> — **Alterado em 13/07/2026**.

Texto da doc (objetivo/regras/notas):

- A tabela `tb_fat_cad_individual` é populada sempre que os dados de um Cadastro Individual do Cidadão são processados.
- 1. A `tb_fat_cad_individual` é preenchida quando são processado(a)s:
  - Fichas de cadastro individual recebidas através da importação de sistemas terceiros, outras instalações do PEC e-SUS-APS ou aplicativo e-SUS Território;
  - Registro da criação de um cadastro individual no CDS ou no módulo Cadastro individual,
  - Registro de atualização de um cadastro individual no CDS, módulo de Cadastro individual, Acompanhamento do Território, Reterritorialização ou Unificação de prontuários.
- Colunas que a doc declara **criptografadas**: `no_nome`, `no_nome_social`, `no_nome_mae`, `no_nome_pai`, `nu_nis`, `nu_portaria_naturalizacao`, `nu_celular`, `no_email`, `nu_obito_do`, `no_maternidade_referencia`, `no_causa_internacao12`, `no_plantas_medicinais`, `no_outra_condicao1`, `no_outra_condicao2`, `no_outra_condicao3`, `no_acompanhado_instituicao`, `no_visita_familiar_parentesco`, `nu_cpf_cidadao`, `nu_cpf_responsavel`.

> **Tabela versionada:** recebe a criação **e cada atualização** do cadastro (CDS, módulo de cadastro, acompanhamento do território, reterritorialização, unificação de prontuários). Para a coorte numa data de corte é preciso escolher a versão vigente (≤ corte), e não "a última". A visualização aponta a versão atual por `co_unico_ultima_ficha = nu_uuid_ficha`. Note que o **CPF é criptografado aqui, mas o CNS não** — ambos são PII. `co_dim_equipe`/`co_dim_unidade_saude` são documentados como "equipe/unidade do profissional responsável" pelo registro.

| Seção da doc | Coluna | LEDI | Descrição (doc oficial) | Marcação |
|---|---|---|---|---|
| Metadados | `co_seq_fat_cad_individual` | — | Código de identificação sequencial criado automaticamente pelo sistema | PK |
| Metadados | `nu_uuid_ficha` | uuid | Identificador universalmente único do registro |  |
| Metadados | `nu_uuid_ficha_origem` | uuidFichaOriginadora | Identificador universalmente único da ficha de origem do cadastro | *[pseudônimo — só para versionar cadastro]* |
| Metadados | `nu_uuid_dado_transp` | uuidDadoSerializado | Identificador universalmente único da camada de transporte de dados. Caso o registro seja gerado dentro do PEC terá o mesmo valor do campo nu_uuid_ficha |  |
| Metadados | `nu_cns` | cnsCidadao | CNS do cidadão | **[PII — nunca projetar]** |
| Metadados | `nu_cpf_cidadao` | cpfCidadao | CPF do cidadão criptografado | **[PII — nunca projetar]** |
| Metadados | `nu_cpf_responsavel` | cpfResponsavelFamiliar | CPF do responsável familiar criptografado | **[PII — nunca projetar]** |
| Metadados | `nu_cns_responsavel` | cnsResponsavelFamiliar | CNS do responsável familiar | **[PII — nunca projetar]** |
| Metadados | `co_fat_cidadao_pec` | — | Campo `co_seq_fat_cidadao_pec` da `tb_fat_cidadao_pec` | FK → `tb_fat_cidadao_pec.co_seq_fat_cidadao_pec` **chave de pessoa** (alvo sem página na doc) |
| Metadados | `co_fat_cidadao_pec_responsvl` | — | Campo `co_seq_fat_cidadao_pec` da `tb_fat_cidadao_pec` referente ao responsável familiar | FK → `tb_fat_cidadao_pec.co_seq_fat_cidadao_pec` |
| Métricas | `st_recusa_cadastro` | statusTermoRecusaCadastroIndividualAtencaoBasica | Status que indica se o cidadão recusou o cadastro |  |
| Métricas | `dt_nascimento` | dataNascimentoCidadao | Data de nascimento do cidadão | *[sensível — projetar só na capacidade "cidadão"]* |
| Métricas | `st_desconhece_mae` | desconheceNomeMae | Status que indica se não há identificação da mãe do cidadão |  |
| Métricas | `st_desconhece_pai` | desconheceNomePai | Status que indica se não há identificação do pai do cidadão |  |
| Métricas | `st_responsavel_familiar` | statusEhResponsavel | Status que indica se o cidadão é o responsável familiar |  |
| Métricas | `st_gestante` | statusEhGestante | Status que indica se a cidadã está gestante |  |
| Métricas | `st_deficiencia` | statusTemAlgumaDeficiencia | Status que indica se o cidadão tem alguma deficiência |  |
| Métricas | `st_defi_auditiva` `st_defi_intelectual_cognitiva` `st_defi_outra` `st_defi_visual` `st_defi_fisica` | deficienciasCidadao | Indica o(s) tipo(s) de deficiência do cidadão |  |
| Métricas | `st_defi_tea` | — | Indica se o cidadão possui Transtorno do Espectro Autista (TEA) |  |
| Métricas | `st_fumante` | statusEhFumante | Status que indica se o cidadão é fumante |  |
| Métricas | `st_alcool` | statusEhDependenteAlcool | Status que indica se o cidadão usa álcool |  |
| Métricas | `st_outra_droga` | statusEhDependenteOutrasDrogas | Status que indica se o cidadão usa outras drogas |  |
| Métricas | `st_hipertensao_arterial` | statusTemHipertensaoArterial | Status que indica se o cidadão tem hipertensão arterial |  |
| Métricas | `st_diabete` | statusTemDiabetes | Status que indica se o cidadão tem diabetes |  |
| Métricas | `st_avc` | statusTeveAvcDerrame | Status que indica se o cidadão já teve AVC/derrame |  |
| Métricas | `st_infarto` | statusTeveInfarto | Status que indica se o cidadão já teve infarto |  |
| Métricas | `st_hanseniase` | statusTemHanseniase | Status que indica se o cidadão tem hanseníase |  |
| Métricas | `st_tuberculose` | statusTemTuberculose | Status que indica se o cidadão tem tuberculose |  |
| Métricas | `st_cancer` | statusTemTeveCancer | Status que indica se o cidadão tem câncer |  |
| Métricas | `st_internacao_12` | statusTeveInternadoem12Meses | Status que indica se o cidadão esteve internado nos últimos 12 meses |  |
| Métricas | `st_tratamento_psiquiatra` | statusDiagnosticoMental | Status que indica se o cidadão está em tratamento psiquiátrico |  |
| Métricas | `st_acamado` | statusEstaAcamado | Status que indica se o cidadão está acamado |  |
| Métricas | `st_domiciliado` | statusEstaDomiciliado | Status que indica se o cidadão está domiciliado |  |
| Métricas | `st_usa_planta_medicinal` | statusUsaPlantasMedicinais | Status que indica se o cidadão usa plantas medicinais |  |
| Métricas | `st_doenca_cardiaca` | statusTeveDoencaCardiaca | Status que indica se o cidadão tem doença cardíaca |  |
| Métricas | `st_doenca_card_insuficiencia` `st_doenca_card_outro` `st_doenca_card_n_sabe` | doencaCardiaca | Indica o(s) tipo(s) de doença cardíaca do cidadão |  |
| Métricas | `st_doenca_respiratoria` | statusTemDoencaRespiratoria | Status que indica se o cidadão tem doença respiratória |  |
| Métricas | `st_doenca_respira_asma` `st_doenca_respira_dpoc_enfisem` `st_doenca_respira_outra` `st_doenca_respira_n_sabe` | doencaRespiratoria | Indica o(s) tipo(s) de doença respiratória do cidadão |  |
| Métricas | `st_problema_rins` | statusTemTeveDoencasRins | Status que indica se o cidadão tem problema nos rins |  |
| Métricas | `st_problema_rins_insuficiencia` `st_problema_rins_outro` `st_problema_rins_nao_sabe` | doencaRins | Indica o(s) tipo(s) de problema renal do cidadão |  |
| Métricas | `st_pic` | statusUsaOutrasPraticasIntegrativasOuComplementares | Status que indica se o cidadão usa práticas integrativas e complementares em saúde (PIC) |  |
| Métricas | `st_plano_saude_privado` | statusPossuiPlanoSaudePrivado | Status que indica se o cidadão possui plano de saúde privado |  |
| Métricas | `st_participa_grupo_comunitario` | statusParticipaGrupoComunitario | Status que indica se o cidadão participa de algum grupo comunitário |  |
| Métricas | `st_frequenta_creche` | statusFrequentaEscola | Status que indica se a criança frequenta creche |  |
| Métricas | `st_frequenta_cuidador` | statusFrequentaBenzedeira | Status que indica se a criança fica com cuidador |  |
| Métricas | `st_comunidade_tradicional` | statusMembroPovoComunidadeTradicional | Status que indica se o cidadão é membro de comunidade tradicional |  |
| Métricas | `st_morador_rua` | statusSituacaoRua | Status que indica se o cidadão é morador de rua |  |
| Métricas | `st_recebe_beneficio` | statusRecebeBeneficio | Status que indica se o cidadão recebe algum benefício social |  |
| Métricas | `st_beneficio_bolsa_familia` `st_beneficio_cesta_alimento` `st_beneficio_leite_nao_humano` `st_beneficio_nao_recebe` `st_beneficio_outros` `st_beneficio_aposentado` `st_beneficio_prest_continuada` | statusRecebeBeneficio | Indica o(s) tipo(s) de benefício que o cidadão recebe |  |
| Métricas | `st_referencia_familiar` | statusPossuiReferenciaFamiliar | Status que indica se o cidadão é a referência da família no território |  |
| Métricas | `st_acompanhado_instituicao` | statusAcompanhadoPorOutraInstituicao | Status que indica se o cidadão é acompanhado por alguma instituição |  |
| Métricas | `st_visita_familiar_frequente` | statusVisitaFamiliarFrequentemente | Status que indica se o cidadão visita a família com frequência |  |
| Métricas | `st_higiene_pessoal_acesso` | statusTemAcessoHigienePessoalSituacaoRua | Status que indica se o cidadão em situação de rua tem acesso a higiene pessoal |  |
| Métricas | `st_hig_pess_banho` `st_hig_pess_sanitario` `st_hig_pess_higiene_bucal` `st_hig_pess_outros` | higienePessoalSituacaoRua | Indica o(s) tipo(s) de acesso a higiene pessoal |  |
| Métricas | `st_orig_alimen_restaurante_pop` `st_orig_alimen_doacao_reli` `st_orig_alimen_doacao_rest` `st_orig_alimen_doacao_popular` `st_orig_alimen_outros` | origemAlimentoSituacaoRua | Indica a(s) origem(ns) da alimentação do cidadão em situação de rua |  |
| Métricas | `st_respons_crianca_adulto_resp` `st_respons_crianca_outra_crian` `st_respons_crianca_adolescente` `st_respons_crianca_sozinha` `st_respons_crianca_creche` `st_respons_crianca_outro` | responsavelPorCrianca | Indica com quem fica a criança de 0 a 9 anos |  |
| Métricas | `st_informar_orientacao_sexual` | statusDesejaInformarOrientacaoSexual | Status que indica se o cidadão optou por não informar a orientação sexual |  |
| Métricas | `st_informar_identidade_genero` | statusDesejaInformarIdentidadeGenero | Status que indica se o cidadão optou por não informar a identidade de gênero |  |
| Métricas | `dt_naturalizacao` | dtNaturalizacao | Data de naturalização do cidadão | *[não necessário]* |
| Métricas | `dt_entrada_brasil` | dtEntradaBrasil | Data de entrada no Brasil | *[não necessário]* |
| Métricas | `dt_obito` | dataObito | Data do óbito do cidadão | *[sensível — usar só como data de exclusão]* |
| Métricas | `nu_micro_area` | microarea | Número da microárea onde o cidadão reside | *[quase-identificador — não projetar]* |
| Métricas | `st_comeu_que_tinha_dnheir_acab` | comeuAlgunsAlimentosQueTinhaDinheiroAcabou | Indica se nos últimos três meses o cidadão comeu apenas alguns alimentos que ainda tinha porque o dinheiro acabou |  |
| Métricas | `st_alimentos_acab_sem_dinheiro` | alimentosAcabaramAntesTerDinheiroComprarMais | Indica se nos últimos três meses os alimentos acabaram antes que houvesse dinheiro para comprar mais |  |
| Métricas | `st_atend_socioeducativo` | — | Indica se o cidadão está em atendimento socioeducativo |  |
| Métricas | `nu_dnv_cidadao` | dnv | Número da Declaração de Nascido Vivo do cidadão | **[PII — nunca projetar]** |
| Métricas | `st_nao_possui_cpf` | stNaoPossuiCpf | Indica se o cidadão não possui CPF |  |
| Métricas | `no_nome` | nomeCidadao | Nome do cidadão | **[PII — nunca projetar]** |
| Métricas | `no_nome_social` | nomeSocial | Nome social do cidadão | **[PII — nunca projetar]** |
| Métricas | `no_nome_mae` | nomeMaeCidadao | Nome da mãe do cidadão | **[PII — nunca projetar]** |
| Métricas | `no_nome_pai` | nomePaiCidadao | Nome do pai do cidadão | **[PII — nunca projetar]** |
| Métricas | `nu_nis` | numeroNisPisPasep | NIS/PIS/PASEP do cidadão | **[PII — nunca projetar]** |
| Métricas | `nu_portaria_naturalizacao` | portariaNaturalizacao | Número da portaria de naturalização | **[PII — nunca projetar]** |
| Métricas | `nu_celular` | telefoneCelular | Número de celular do cidadão | **[PII — nunca projetar]** |
| Métricas | `no_email` | emailCidadao | Endereço de e-mail do cidadão | **[PII — nunca projetar]** |
| Métricas | `nu_obito_do` | numeroDO | Número da Declaração de Óbito do cidadão | **[PII — nunca projetar]** |
| Métricas | `no_maternidade_referencia` | maternidadeDeReferencia | Nome da maternidade de referência | **[PII — nunca projetar]** |
| Métricas | `no_causa_internacao12` | descricaoCausaInternacaoEm12Meses | Descrição da causa de internação nos últimos 12 meses | **[PII — nunca projetar]** |
| Métricas | `no_plantas_medicinais` | descricaoPlantasMedicinaisUsadas | Descrição das plantas medicinais utilizadas | **[PII — nunca projetar]** |
| Métricas | `no_outra_condicao1` | descricaoOutraCondicao1 | Descrição de outra condição de saúde 1 | **[PII — nunca projetar]** |
| Métricas | `no_outra_condicao2` | descricaoOutraCondicao2 | Descrição de outra condição de saúde 2 | **[PII — nunca projetar]** |
| Métricas | `no_outra_condicao3` | descricaoOutraCondicao3 | Descrição de outra condição de saúde 3 | **[PII — nunca projetar]** |
| Métricas | `no_acompanhado_instituicao` | outraInstituicaoQueAcompanha | Nome da instituição que acompanha o cidadão | **[PII — nunca projetar]** |
| Métricas | `no_visita_familiar_parentesco` | grauParentescoFamiliarFrequentado | Grau de parentesco do familiar visitado frequentemente | **[PII — nunca projetar]** |
| Métricas | `st_cidadao_aldeado` | — | Indica se o cidadão reside em aldeia indígena |  |
| Métricas | `st_ficha_inativa` | — | Indica se a ficha de cadastro está inativa |  |
| Métricas | `st_gerado_automaticamente` | — | Indica se o registro foi gerado automaticamente pelo sistema |  |
| Métricas | `st_processo_cidadao` | — | Indica o processamento do cadastro no contexto do processo do cidadão |  |
| Métricas | `st_processo_linha_tempo` | — | Indica o processamento do cadastro na linha do tempo do cidadão |  |
| Métricas | `st_proc_operacionais` | — | Indica dados de processamento operacional do cadastro |  |
| Dim. específica | `co_dim_sexo` | — | Código do sexo do cidadão. Campo `co_seq_dim_faixa_sexo` da `tb_dim_sexo` | FK → `tb_dim_sexo.co_seq_dim_faixa_sexo` ⚠ PK real da dimensão é `co_seq_dim_sexo` |
| Dim. específica | `co_dim_raca_cor` | — | Código da raça/cor do cidadão. Campo `co_seq_dim_raca_cor` da `tb_dim_raca_cor` | FK → `tb_dim_raca_cor.co_seq_dim_raca_cor` |
| Dim. específica | `co_dim_etnia` | — | Código da etnia do cidadão. Campo `co_seq_dim_etnia` da `tb_dim_etnia` | FK → `tb_dim_etnia.co_seq_dim_etnia` |
| Dim. específica | `co_dim_nacionalidade` | — | Código da nacionalidade do cidadão. Campo `co_seq_dim_nacionalidade` da `tb_dim_nacionalidade` | FK → `tb_dim_nacionalidade.co_seq_dim_nacionalidade` |
| Dim. específica | `co_dim_pais_nascimento` | — | Código do país de nascimento do cidadão. Campo `co_seq_dim_pais` da `tb_dim_pais` | FK → `tb_dim_pais.co_seq_dim_pais` |
| Dim. específica | `co_dim_municipio_cidadao` | — | Código do município de nascimento do cidadão. Campo `co_seq_dim_municipio` da `tb_dim_municipio` | FK → `tb_dim_municipio.co_seq_dim_municipio` ⚠ município de **nascimento** — nunca usar no recorte |
| Dim. específica | `co_dim_faixa_etaria` | — | Código da faixa etária do cidadão. Campo `co_seq_dim_faixa_etaria` da `tb_dim_faixa_etaria` | FK → `tb_dim_faixa_etaria.co_seq_dim_faixa_etaria` |
| Dim. específica | `co_dim_frequencia_alimentacao` | — | Código da frequência de alimentação. Campo `co_seq_dim_frequencia_alimentacao` da `tb_dim_frequencia_alimentacao` | FK → `tb_dim_frequencia_alimentacao.co_seq_dim_frequencia_alimentacao` |
| Dim. específica | `co_dim_tipo_parentesco` | — | Código do tipo de parentesco com o responsável familiar. Campo `co_seq_dim_tipo_parentesco` da `tb_dim_tipo_parentesco` | FK → `tb_dim_tipo_parentesco.co_seq_dim_tipo_parentesco` |
| Dim. específica | `co_dim_cbo` | — | Código do CBO do profissional que realizou o cadastro. Campo `co_seq_dim_cbo` da `tb_dim_cbo` | FK → `tb_dim_cbo.co_seq_dim_cbo` |
| Dim. específica | `co_dim_cbo_cidadao` | — | Código do CBO (ocupação) do cidadão. Campo `co_seq_dim_cbo` da `tb_dim_cbo` | FK → `tb_dim_cbo.co_seq_dim_cbo` |
| Dim. específica | `co_dim_tipo_escolaridade` | — | Código do grau de escolaridade do cidadão. Campo `co_seq_dim_tipo_escolaridade` da `tb_dim_tipo_escolaridade` | FK → `tb_dim_tipo_escolaridade.co_seq_dim_tipo_escolaridade` |
| Dim. específica | `co_dim_situacao_trabalho` | — | Código da situação de trabalho do cidadão. Campo `co_seq_dim_situacao_trabalho` da `tb_dim_situacao_trabalho` | FK → `tb_dim_situacao_trabalho.co_seq_dim_situacao_trabalho` |
| Dim. específica | `co_dim_tipo_orientacao_sexual` | — | Código da orientação sexual do cidadão. Campo `co_seq_dim_tipo_orientacao_sexual` da `tb_dim_tipo_orientacao_sexual` | FK → `tb_dim_tipo_orientacao_sexual.co_seq_dim_tipo_orientacao_sexual` |
| Dim. específica | `co_dim_identidade_genero` | — | Código da identidade de gênero do cidadão. Campo `co_seq_dim_identidade_genero` da `tb_dim_identidade_genero` | FK → `tb_dim_identidade_genero.co_seq_dim_identidade_genero` |
| Dim. específica | `co_dim_tipo_saida_cadastro` | — | Código do tipo de saída do cadastro. Campo `co_seq_dim_tipo_saida_cadastro` da `tb_dim_tipo_saida_cadastro` | FK → `tb_dim_tipo_saida_cadastro.co_seq_dim_tipo_saida_cadastro` |
| Dim. específica | `co_dim_tipo_condicao_peso` | — | Código da condição de peso do cidadão. Campo `co_seq_dim_tipo_condicao_peso` da `tb_dim_tipo_condicao_peso` | FK → `tb_dim_tipo_condicao_peso.co_seq_dim_tipo_condicao_peso` |
| Dim. específica | `co_dim_tempo_morador_rua` | — | Código do tempo em situação de rua. Campo `co_seq_dim_tempo` da `tb_dim_tempo` | FK → `tb_dim_tempo.co_seq_dim_tempo` |
| Dim. específica | `co_dim_tipo_sanguineo` | — | Código do tipo sanguíneo do cidadão. Campo `co_seq_dim_tipo_sanguineo` da `tb_dim_tipo_sanguineo` | FK → `tb_dim_tipo_sanguineo.co_seq_dim_tipo_sanguineo` |
| Dim. específica | `co_dim_estado_civil` | — | Código do estado civil do cidadão. Campo `co_seq_dim_estado_civil` da `tb_dim_estado_civil` | FK → `tb_dim_estado_civil.co_seq_dim_estado_civil` |
| Dim. específica | `co_dim_tipo_socioeducativo` | — | Código do tipo de atendimento socioeducativo. Campo `co_seq_tipo_socioeducativo` da `tb_dim_tipo_socioeducativo` | FK → `tb_dim_tipo_socioeducativo.co_seq_tipo_socioeducativo` |
| Dim. específica | `co_dim_tempo_socioeducativo` | — | Código do tempo de atendimento socioeducativo. Campo `co_seq_tempo_socioeducativo` da `tb_dim_tempo_socioeducativo` | FK → `tb_dim_tempo_socioeducativo.co_seq_tempo_socioeducativo` |
| Dim. específica | `co_dim_just_nao_possui_cpf` | — | Código da justificativa de não possuir CPF. Campo `co_seq_dim_just_nao_possui_cpf` da `tb_dim_just_nao_possui_cpf` | FK → `tb_dim_just_nao_possui_cpf.co_seq_dim_just_nao_possui_cpf` |
| Dim. específica | `co_dim_povo_comunidad_trad` | — | Código do povo ou comunidade tradicional do cidadão. Campo `co_seq_dim_povo_comunidad_trad` da `tb_dim_povo_comunidad_trad` | FK → `tb_dim_povo_comunidad_trad.co_seq_dim_povo_comunidad_trad` |
| Dim. comum ao grupo | `co_dim_municipio` | — | Código de identificação do município. Campo `co_seq_dim_municipio` da `tb_dim_municipio` | FK → `tb_dim_municipio.co_seq_dim_municipio` **recorte municipal** |
| Dim. comum ao grupo | `co_dim_tipo_ficha` | — | Código de identificação do tipo de ficha. Campo `co_seq_dim_tipo_ficha` da `tb_dim_tipo_ficha` | FK → `tb_dim_tipo_ficha.co_seq_dim_tipo_ficha` |
| Dim. comum ao grupo | `co_dim_profissional` | — | Código de identificação do profissional responsável. Campo `co_seq_dim_profissional` da `tb_dim_profissional` | FK → `tb_dim_profissional.co_seq_dim_profissional` |
| Dim. comum ao grupo | `co_dim_unidade_saude` | — | Código de identificação da unidade de saúde do profissional responsável. Campo `co_seq_dim_unidade_saude` da `tb_dim_unidade_saude` | FK → `tb_dim_unidade_saude.co_seq_dim_unidade_saude` |
| Dim. comum ao grupo | `co_dim_equipe` | — | Código de identificação da equipe do profissional responsável. Campo `co_seq_dim_equipe` da `tb_dim_equipe` | FK → `tb_dim_equipe.co_seq_dim_equipe` |
| Dim. comum ao grupo | `co_dim_tempo` | — | Responsável por armazenar as datas de maneira estruturada. Campo `co_seq_dim_tempo` da `tb_dim_tempo` | FK → `tb_dim_tempo.co_seq_dim_tempo` **data do registro** |
| Dim. comum ao grupo | `co_dim_tempo_validade` | — | Responsável por armazenar a data de validade do cadastro. Campo `co_seq_dim_tempo` da `tb_dim_tempo` | FK → `tb_dim_tempo.co_seq_dim_tempo` |
| Dim. comum ao grupo | `co_dim_tempo_validade_recusa` | — | Responsável por armazenar a data de validade da recusa de cadastro. Campo `co_seq_dim_tempo` da `tb_dim_tempo` | FK → `tb_dim_tempo.co_seq_dim_tempo` |
| Dim. comum ao grupo | `co_dim_tipo_origem_dado_transp` | — | Código da origem do dado no transporte. Campo `co_seq_dim_tp_orgm_dado_transp` da `tb_dim_tipo_origem_dado_transp` | FK → `tb_dim_tipo_origem_dado_transp.co_seq_dim_tp_orgm_dado_transp` |
| Dim. comum ao grupo | `co_dim_cds_tipo_origem` | — | Código da origem do dado no transporte. Campo `co_seq_dim_tipo_origem` da `tb_dim_tipo_origem` | FK → `tb_dim_tipo_origem.co_seq_dim_tipo_origem` |

#### `tb_fat_cad_dom_familia` — Tabela fato das famílias do cadastro domiciliar

Fonte: <https://integracao.esusaps.bridge.ufsc.tech/dw/fatos/cadastro_domiciliar/tb_fat_cad_dom_familia.html> — **Alterado em 13/07/2026**.

Texto da doc (objetivo/regras/notas):

- A tabela `tb_fat_cad_dom_familia` é populada para cada família registrada em um Cadastro Domiciliar e Territorial. Cada registro representa um núcleo familiar dentro do domicílio e está vinculado à `tb_fat_cad_domiciliar` pelo campo `co_fat_cad_domiciliar`.
- 1. A `tb_fat_cad_dom_familia` é preenchida quando são processados:
  - Fichas de cadastro domiciliar com famílias recebidas através da importação de sistemas terceiros, de outras instalações do PEC e-SUS-APS ou do aplicativo e-SUS Território;
  - Registro de um cadastro domiciliar com famílias no CDS, Acompanhamento do Território ou Reterritorialização.
- Colunas que a doc declara **criptografadas**: `nu_cpf_responsavel`, `nu_prontuario`.

> Relevante só pelo sinal de mudança da família (`st_mudou`) e pelo responsável familiar; o cidadão (`co_fat_cidadao_pec`) é o **responsável familiar**, não cada membro.

| Seção da doc | Coluna | LEDI | Descrição (doc oficial) | Marcação |
|---|---|---|---|---|
| Metadados | `co_seq_fat_cad_dom_familia` | — | Código de identificação sequencial criado automaticamente pelo sistema | PK |
| Metadados | `nu_uuid_ficha` | uuid | Identificador universalmente único do registro |  |
| Metadados | `nu_uuid_ficha_origem` | uuidFichaOriginadora | UUID da ficha de origem do domicílio | *[pseudônimo — só para versionar cadastro]* |
| Metadados | `nu_uuid_dado_transp` | uuidDadoSerializado | Identificador universalmente único da camada de transporte de dados |  |
| Metadados | `co_fat_cad_domiciliar` | — | Campo `co_seq_fat_cad_domiciliar` da `tb_fat_cad_domiciliar` ao qual esta família pertence | FK → `tb_fat_cad_domiciliar.co_seq_fat_cad_domiciliar` |
| Metadados | `co_fat_cidadao_pec` | — | Campo `co_seq_fat_cidadao_pec` da `tb_fat_cidadao_pec` referente ao responsável familiar | FK → `tb_fat_cidadao_pec.co_seq_fat_cidadao_pec` **chave de pessoa** (alvo sem página na doc) |
| Metadados | `nu_cns_responsavel` | numeroCnsResponsavel | CNS do responsável familiar | **[PII — nunca projetar]** |
| Metadados | `st_recusa_cadastro` | statusTermoRecusa | Indica se houve recusa ao cadastramento |  |
| Metadados | `st_normaliza_ficha_cadastros` | — | Flag de controle de normalização da ficha de cadastros |  |
| Metadados | `nu_cpf_responsavel` | cpfResponsavel | CPF do responsável familiar | **[PII — nunca projetar]** |
| Metadados | `nu_prontuario` | numeroProntuario | Número do prontuário do responsável familiar | **[PII — nunca projetar]** |
| Métricas | `dt_nascimento` | dataNascimentoResponsavel | Data de nascimento do responsável familiar | *[sensível — projetar só na capacidade "cidadão"]* |
| Métricas | `dt_inicio_residencia` | resideDesde | Data de início de residência da família no domicílio |  |
| Métricas | `st_mudou` | stMudanca | Indica se a família se mudou do domicílio |  |
| Métricas | `nu_micro_area` | microArea | Número da microárea onde a família reside | *[quase-identificador — não projetar]* |
| Métricas | `qt_membro_familiar` | numeroMembrosFamilia | Quantidade de membros da família |  |
| Dim. específica | `co_dim_tipo_renda_familiar` | — | Código do tipo de renda familiar. Campo `co_seq_dim_tipo_renda_familiar` da `tb_dim_tipo_renda_familiar` | FK → `tb_dim_tipo_renda_familiar.co_seq_dim_tipo_renda_familiar` |
| Dim. comum ao grupo | `co_dim_municipio` | — | Código de identificação do município. Campo `co_seq_dim_municipio` da `tb_dim_municipio` | FK → `tb_dim_municipio.co_seq_dim_municipio` **recorte municipal** |
| Dim. comum ao grupo | `co_dim_tipo_ficha` | — | Código de identificação do tipo de ficha. Campo `co_seq_dim_tipo_ficha` da `tb_dim_tipo_ficha` | FK → `tb_dim_tipo_ficha.co_seq_dim_tipo_ficha` |
| Dim. comum ao grupo | `co_dim_profissional` | — | Código de identificação do profissional responsável. Campo `co_seq_dim_profissional` da `tb_dim_profissional` | FK → `tb_dim_profissional.co_seq_dim_profissional` |
| Dim. comum ao grupo | `co_dim_cbo` | — | Código de identificação do CBO profissional responsável. Campo `co_seq_dim_cbo` da `tb_dim_cbo` | FK → `tb_dim_cbo.co_seq_dim_cbo` |
| Dim. comum ao grupo | `co_dim_unidade_saude` | — | Código de identificação da unidade de saúde do profissional responsável. Campo `co_seq_dim_unidade_saude` da `tb_dim_unidade_saude` | FK → `tb_dim_unidade_saude.co_seq_dim_unidade_saude` |
| Dim. comum ao grupo | `co_dim_equipe` | — | Código de identificação da equipe do profissional responsável. Campo `co_seq_dim_equipe` da `tb_dim_equipe` | FK → `tb_dim_equipe.co_seq_dim_equipe` |
| Dim. comum ao grupo | `co_dim_tempo` | — | Responsável por armazenar as datas de maneira estruturada. Campo `co_seq_dim_tempo` da `tb_dim_tempo` | FK → `tb_dim_tempo.co_seq_dim_tempo` **data do registro** |
| Dim. comum ao grupo | `co_dim_tempo_validade` | — | Armazena a data de validade do cadastro de forma estruturada. Campo `co_seq_dim_tempo` da `tb_dim_tempo` | FK → `tb_dim_tempo.co_seq_dim_tempo` |
| Dim. comum ao grupo | `co_dim_tempo_validade_recusa` | — | Armazena a data de validade em caso de recusa de cadastro. Campo `co_seq_dim_tempo` da `tb_dim_tempo` | FK → `tb_dim_tempo.co_seq_dim_tempo` |
| Dim. comum ao grupo | `co_dim_tipo_origem_dado_transp` | — | Código da origem do dado no transporte. Campo `co_seq_dim_tp_orgm_dado_transp` da `tb_dim_tipo_origem_dado_transp` | FK → `tb_dim_tipo_origem_dado_transp.co_seq_dim_tp_orgm_dado_transp` |
| Dim. comum ao grupo | `co_dim_cds_tipo_origem` | — | Código da origem do dado no transporte. Campo `co_seq_dim_tipo_origem` da `tb_dim_tipo_origem` | FK → `tb_dim_tipo_origem.co_seq_dim_tipo_origem` |


### 2.8 Visualização

#### `tb_acomp_cidadaos_vinculados` — Tabela de acompanhamento de cidadãos vinculados

Fonte: <https://integracao.esusaps.bridge.ufsc.tech/dw/visualizacoes/acompanhamento_cidadaos_vinculados.html> — **Alterado em 19/06/2026**.

Texto da doc (objetivo/regras/notas):

- Esta estratégia de negócio refere-se ao Cadastro Individual do Cidadão, que pode ser realizado diretamente no módulo de Cadastro Individual do CDS ou do PEC. Em sua composição também são utilizados dados do Cadastro Domiciliar e Territorial para composição de informações como endereço, microárea e estrutura do núcleo familiar.
- 1. Os Cidadãos estão armazenados na `tb_acomp_cidadaos_vinculados`.
- 2. Somente são considerados cidadãos que possuem vínculo com alguma equipe em seu cadastro.
- 3. Por padrão, é utilizada a equipe definida no "Cadastro Individual do Cidadão", sendo que essa informação pode ser alterada através da atualização do mesmo.
- 4. Com exceção dos casos de cadastro duplicado, um mesmo cidadão é apresentado uma única vez nesta tabela, estando vinculado exclusivamente a uma única equipe.
- 5. Dados referente ao "Cadastro Domiciliar e Territorial" serão preenchidos somente para cidadãos que estejam devidamente estruturados em um núcleo familiar. A gestão do núcleo também pode ser realizada através do acompanhamento do território.
- 6. A estrutura comporta tanto os dados de endereço do "Cadastro individual" quanto do "Cadastro Domiciliar e Territorial", o PEC sempre prioriza os dados do domicílio (quando existem) em relação aos dados do cadastro individual.

> **Única tabela com tipos de dado documentados.** Sem histórico: o índice do DW diz que "as visualizações não possuem dados históricos", refletindo o último processamento — não reconstrói a coorte de competências passadas. Não tem código IBGE (só nomes de município de endereço, que são PII), nem óbito, nem tipo de equipe. `co_fat_cidadao_pec` entrou na v5.4.7→5.4.8; `co_cds_domicilio`, `ds_tipo_localizacao_domicilio`, `no_raca_cor` na v5.3.25→5.3.26. O vínculo aqui é a regra local do PEC, **não** o vínculo calculado pelo Siaps.

| Seção da doc | Coluna | Tipo (doc) | Descrição (doc oficial) | Marcação |
|---|---|---|---|---|
| Campo | `co_seq_acomp_cidadaos_vinc` | inteiro | Código sequencial da tabela. | PK |
| Campo | `no_cidadao` | texto | Nome do cidadão. | **[PII — nunca projetar]** |
| Campo | `no_social_cidadao` | texto | Nome social do cidadão. | **[PII — nunca projetar]** |
| Campo | `dt_nascimento_cidadao` | data | Data de nascimento do cidadão. | *[sensível — projetar só na capacidade "cidadão"]* |
| Campo | `no_sexo_cidadao` | texto | Descrição do Sexo do cidadão. |  |
| Campo | `tp_identidade_genero_cidadao` | texto | Identidade de gênero do cidadão. |  |
| Campo | `nu_cpf_cidadao` | texto | CPF do cidadão. | **[PII — nunca projetar]** |
| Campo | `nu_cns_cidadao` | texto | CNS do cidadão. | **[PII — nunca projetar]** |
| Campo | `st_usar_cadastro_individual` | inteiro | Se a opção "Utilizando a informação do Cadastro Individual do cidadão" foi marcada na hora de vincular um cidadão a uma equipe (0 - Não; 1 - Sim). |  |
| Campo | `st_possui_fci` | inteiro | Se o cidadão possui uma FCI (0 - Não; 1 - Sim), quando "Não", significa que o registro possui apenas o "Cadastro Simplificado do Cidadão¹". |  |
| Campo | `st_possui_fcdt` | inteiro | Se o cidadão possui uma FCDT (0 - Não; 1 - Sim), quando "Não", significa que o registro não está vinculado a um "Cadastro Domiciliar e Territorial". |  |
| Campo | `nu_telefone_celular` | texto | Número do telefone celular do cidadão. | **[PII — nunca projetar]** |
| Campo | `nu_telefone_contato` | texto | Número do telefone de contato do cidadão. | **[PII — nunca projetar]** |
| Campo | `co_unico_ultima_ficha` | texto | Uuid do último "Cadastro Individual do Cidadão" associado ao cidadão. Campo `nu_uuid_ficha` da `tb_fat_cad_individual` | FK → `tb_fat_cad_individual.nu_uuid_ficha` |
| Campo | `dt_ultima_atualizacao_cidadao` | data | Data da última atualização dos dados do cidadão, seja ela feita através do "Cadastro Individual do Cidadão" ou através do "Cadastro Simplificado do Cidadão¹" no módulo do cidadão do PEC. |  |
| Campo | `co_cidadao` | inteiro | Código identificador do cidadão. |  |
| Campo | `no_responsavel` | texto | Nome do responsável pelo cidadão definido através do vínculo do responsável familiar no "Cadastro Individual do Cidadão". | **[PII — nunca projetar]** |
| Campo | `nu_fone_residencial` | texto | Telefone da residência do cidadão. | **[PII — nunca projetar]** |
| Campo | `nu_cnes_vinc_equipe` | texto | CNES da unidade de saúde responsável pelo cidadão de acordo com as regras definidas neste documento. |  |
| Campo | `dt_atualizacao_fcd` | data | Data de atualização do "Cadastro Domiciliar e Territorial" que originou os dados de vínculo domiciliar do cidadão. |  |
| Campo | `nu_ine_vinc_equipe` | texto | INE da equipe responsável pelo cidadão de acordo com as regras definidas neste documento. |  |
| Campo | `no_equipe_vinc_equipe` | texto | Nome da equipe responsável pelo cidadão de acordo com as regras definidas neste documento. |  |
| Campo | `nu_micro_area_tb_cidadao` | texto | Número da microárea do cidadão definido no "Cadastro Simplificado do Cidadão¹". | *[quase-identificador — não projetar]* |
| Campo | `nu_micro_area_domicilio` | texto | Número da microárea do domicílio definido no "Cadastro Domiciliar e Territorial" que o cidadão está vinculado. | *[quase-identificador — não projetar]* |
| Campo | `no_tipo_logradouro_tb_cidadao` | texto | Nome do Tipo do Logradouro definido no "Cadastro Simplificado do Cidadão¹". | **[PII — nunca projetar]** |
| Campo | `ds_logradouro_tb_cidadao` | texto | Logradouro definido no "Cadastro Simplificado do Cidadão¹". | **[PII — nunca projetar]** |
| Campo | `nu_numero_tb_cidadao` | texto | Número definido no "Cadastro Simplificado do Cidadão¹". | **[PII — nunca projetar]** |
| Campo | `st_sem_numero_tb_cidadao` | inteiro | Se o endereço possui um número definido no "Cadastro Simplificado do Cidadão¹" (0 - Não; 1 - Sim). | **[PII — nunca projetar]** |
| Campo | `ds_complemento_tb_cidadao` | texto | Complemento definido no "Cadastro Simplificado do Cidadão¹". | **[PII — nunca projetar]** |
| Campo | `no_bairro_tb_cidadao` | texto | Nome do bairro definido no "Cadastro Simplificado do Cidadão¹". | **[PII — nunca projetar]** |
| Campo | `no_municipio_tb_cidadao` | texto | Nome do município definido no "Cadastro Simplificado do Cidadão¹". | **[PII — nunca projetar]** |
| Campo | `sg_uf_tb_cidadao` | texto | Sigla da UF definida no "Cadastro Simplificado do Cidadão¹". | **[PII — nunca projetar]** |
| Campo | `ds_cep_tb_cidadao` | texto | CEP definido no "Cadastro Simplificado do Cidadão¹". | **[PII — nunca projetar]** |
| Campo | `no_tipo_logradouro_domicilio` | texto | Nome do Tipo do Logradouro definido no "Cadastro Domiciliar e Territorial" que o cidadão está vinculado. | **[PII — nunca projetar]** |
| Campo | `ds_logradouro_domicilio` | texto | Logradouro definido no "Cadastro Domiciliar e Territorial" que o cidadão está vinculado. | **[PII — nunca projetar]** |
| Campo | `nu_numero_domicilio` | texto | Número oriundo definido no "Cadastro Domiciliar e Territorial" que o cidadão está vinculado. | **[PII — nunca projetar]** |
| Campo | `st_sem_numero_domicilio` | inteiro | Se o endereço possui um número definido no "Cadastro Domiciliar e Territorial" que o cidadão está vinculado (0 - Não; 1 - Sim). | **[PII — nunca projetar]** |
| Campo | `ds_complemento_domicilio` | texto | Complemento definido no "Cadastro Domiciliar e Territorial" que o cidadão está vinculado. | **[PII — nunca projetar]** |
| Campo | `no_bairro_domicilio` | texto | Nome do bairro definido no "Cadastro Domiciliar e Territorial" que o cidadão está vinculado. | **[PII — nunca projetar]** |
| Campo | `no_municipio_domicilio` | texto | Nome do município definido no "Cadastro Domiciliar e Territorial" que o cidadão está vinculado. | **[PII — nunca projetar]** |
| Campo | `sg_uf_domicilio` | texto | Sigla da UF definida no "Cadastro Domiciliar e Territorial" que o cidadão está vinculado. | **[PII — nunca projetar]** |
| Campo | `ds_cep_domicilio` | texto | CEP definido no "Cadastro Domiciliar e Territorial" que o cidadão está vinculado. | **[PII — nunca projetar]** |
| Campo | `ds_logradouro_tb_cidadao_filtr` | texto | Coluna com dado normalizado referente ao logradouro do "Cadastro Simplificado do Cidadão" utilizada para realizar buscas com performance otimizada. | **[PII — nunca projetar]** |
| Campo | `no_bairro_tb_cidadao_filtro` | texto | Coluna com dado normalizado referente ao bairro do "Cadastro Simplificado do Cidadão" utilizada para realizar buscas com performance otimizada. | **[PII — nunca projetar]** |
| Campo | `ds_logradouro_domicilio_filtro` | texto | Coluna com dado normalizado referente ao logradouro do "Cadastro Domiciliar e Territorial" utilizada para realizar buscas com performance otimizada. | **[PII — nunca projetar]** |
| Campo | `no_bairro_domicilio_filtro` | texto | Coluna com dado normalizado referente ao bairro do "Cadastro Domiciliar e Territorial" utilizada para realizar buscas com performance otimizada. | **[PII — nunca projetar]** |
| Campo | `co_cds_domicilio` | inteiro | Código identificador do domicílio ao qual o cidadão está vinculado. Relacionado ao campo `co_seq_cds_domicilio` da `tb_cds_domicilio`. | *[pseudônimo de domicílio — não projetar]* |
| Campo | `ds_tipo_localizacao_domicilio` | texto | Descrição da Localização definida no "Cadastro Domiciliar e Territorial" que o cidadão está vinculado. Relacionado ao campo `ds_tipo_localizacao` da `tb_dim_tipo_localizacao`. |  |
| Campo | `no_raca_cor` | texto | Nome da Raça/Cor do cidadão. | *[sensível — não necessário a C2–C7]* |
| Campo | `co_fat_cidadao_pec` | inteiro | Código identificador do cidadão nas tabelas fato, relacionado a outras tabelas de fato, como `tb_fat_atendimento_individual` | **chave de pessoa** (alvo sem página na doc) |


### 2.9 Dimensões — tempo, território, equipe, profissional e pessoa

#### `tb_dim_tempo` — Tabela de dimensão de datas

Fonte: <https://integracao.esusaps.bridge.ufsc.tech/dw/dimensoes/dim_tempo.html> — **Alterado em 10/11/2024**.

Texto da doc (objetivo/regras/notas):

- A tabela `tb_dim_tempo` é utilizada para armazenar as datas de maneira estruturada.

> Junção usada pelo C1: `JOIN tb_dim_tempo t ON t.co_seq_dim_tempo = f.co_dim_tempo`, filtro em `t.dt_registro`. A doc não define o formato de `co_seq_dim_tempo` (não presumir AAAAMMDD). Outras FKs de data que apontam para esta tabela: `co_dim_tempo_vacina_aplicada`, `co_dim_data_inicio_problema`, `co_dim_data_fim_problema`, `co_dim_tempo_validade`, `co_dim_tempo_validade_recusa` e os `co_dim_tempo_*` das tabelas consolidadas.

| Seção da doc | Coluna | Descrição (doc oficial) | Marcação |
|---|---|---|---|
| Campo | `co_seq_dim_tempo` | Código de identificação sequencial | PK |
| Campo | `dt_registro` | Data no formato AAAA-MM-DD |  |
| Campo | `nu_dia` | Número referente ao dia da data |  |
| Campo | `nu_mes` | Número referente ao mês da data |  |
| Campo | `nu_ano` | Número referente ao ano da data |  |
| Campo | `ds_dia_semana` | Descrição referente ao dia da semana da data |  |

#### `tb_dim_municipio` — Tabela de dimensão de município

Fonte: <https://integracao.esusaps.bridge.ufsc.tech/dw/dimensoes/dim_municipio.html> — **Alterado em 06/12/2024**.

Texto da doc (objetivo/regras/notas):

- A tabela `tb_dim_municipio` é utilizada para armazenar os municípios. Possui como referência a Tabela de municípios do LEDI.
- 1. A tabela é preenchida sempre que um cidadão com um município diferente é cadastrado.

> Referência nacional (1870 linhas no CT 133). Ligar **sempre** por `co_ibge` (7 dígitos, texto); nunca pela chave substituta `co_seq_dim_municipio`, que é local da instalação.

| Seção da doc | Coluna | Descrição (doc oficial) | Marcação |
|---|---|---|---|
| Campo | `co_seq_dim_municipio` | Código de identificação sequencial | PK |
| Campo | `no_municipio` | Nome do município |  |
| Campo | `co_ibge` | Código do IBGE do município |  |
| Campo | `co_dim_uf` | Código da Unidade Federativa do município. Campo `co_seq_dim_uf` da `tb_dim_uf` | FK → `tb_dim_uf.co_seq_dim_uf` |
| Campo | `st_registro_valido` | Identifica se o respectivo registro está completo na tabela do DW. Isto ocorre durante o processamento inicial dos dados. Caso o identificador não exista na tabela, o mesmo é inserido. Neste momento, apenas o identificador existe no DW, então a coluna "st_registro_valido" é marcada com o valor "null" para que seja reprocessado novamente e adicionadas as informações complementares. Este reprocessamento realiza a busca na base de dados do e-SUS APS PEC e, encontrando o identificador, inclui as demais informações e marca o registro como "válido" (valor 1). |  |
| Campo | `ds_filtro` | Concatenação entre o código do IBGE, nome e UF do município, sem acentos |  |

#### `tb_dim_uf` — Tabela de dimensão de UF

Fonte: <https://integracao.esusaps.bridge.ufsc.tech/dw/dimensoes/dim_uf.html> — **Alterado em 13/07/2026**.

Texto da doc (objetivo/regras/notas):

- A tabela `tb_dim_uf` é utilizada para armazenar as Unidades Federativas do Brasil. Os dados são importados do IBGE.

| Seção da doc | Coluna | Descrição (doc oficial) | Marcação |
|---|---|---|---|
| Campo | `co_seq_dim_uf` | Código de identificação sequencial | PK |
| Campo | `co_uf` | Código IBGE da UF |  |
| Campo | `no_uf` | Nome da UF |  |
| Campo | `no_identificador` | Identificador textual da UF |  |
| Campo | `sg_uf` | Sigla da UF |  |
| Campo | `ds_filtro` | Nome da UF sem acentuação, utilizado para filtros |  |
| Campo | `co_dim_pais` | Código do país ao qual a UF pertence. Campo `co_seq_dim_pais` da `tb_dim_pais` | FK → `tb_dim_pais.co_seq_dim_pais` |
| Campo | `st_registro_valido` | Identifica se o respectivo registro está completo na tabela do DW. Isto ocorre durante o processamento inicial dos dados. Caso o identificador não exista na tabela, o mesmo é inserido. Neste momento, apenas o identificador existe no DW, então a coluna "st_registro_valido" é marcada com o valor "null" para que seja reprocessado novamente e adicionadas as informações complementares. Este reprocessamento realiza a busca na base de dados do e-SUS APS PEC e, encontrando o identificador, inclui as demais informações e marca o registro como "válido" (valor 1). |  |

#### `tb_dim_unidade_saude` — Tabela de dimensão de unidade de saúde

Fonte: <https://integracao.esusaps.bridge.ufsc.tech/dw/dimensoes/dim_unidade_saude.html> — **Alterado em 13/07/2026**.

Texto da doc (objetivo/regras/notas):

- A tabela `tb_dim_unidade_saude` é utilizada para armazenar os dados das unidades de saúde. Os dados são importados do CNES — Cadastro Nacional de Estabelecimentos de Saúde.

> **Sem coluna de município** e sem tipo de estabelecimento. No CT 133 há linhas `st_registro_valido = 0` ("CNES NÃO ENCONTRADO").

| Seção da doc | Coluna | Descrição (doc oficial) | Marcação |
|---|---|---|---|
| Campo | `co_seq_dim_unidade_saude` | Código de identificação sequencial | PK |
| Campo | `nu_cnes` | Número do Cadastro Nacional de Estabelecimentos de Saúde |  |
| Campo | `no_unidade_saude` | Nome da unidade de saúde |  |
| Campo | `no_bairro` | Nome do bairro da unidade de saúde |  |
| Campo | `st_registro_valido` | Identifica se o respectivo registro está completo na tabela do DW. Isto ocorre durante o processamento inicial dos dados. Caso o identificador não exista na tabela, o mesmo é inserido. Neste momento, apenas o identificador existe no DW, então a coluna "st_registro_valido" é marcada com o valor "null" para que seja reprocessado novamente e adicionadas as informações complementares. Este reprocessamento realiza a busca na base de dados do e-SUS APS PEC e, encontrando o identificador, inclui as demais informações e marca o registro como "válido" (valor 1). |  |
| Campo | `ds_filtro` | Concatenação de campos para busca, sem acentos |  |

#### `tb_dim_equipe` — Tabela de dimensão de equipes

Fonte: <https://integracao.esusaps.bridge.ufsc.tech/dw/dimensoes/dim_equipe.html> — **Alterado em 10/11/2024**.

Texto da doc (objetivo/regras/notas):

- A tabela `tb_dim_equipe` é utilizada para armazenar as equipes. Os dados são de acordo com o importado no CNES da instalação.
- 1. Quando uma equipe é informada em algum registro, que não seja o próprio cadastro da equipe, a tabela é preenchida.

> **Sem tipo de equipe** (nem código, nem descrição) e **sem município**. Linha-sentinela "SEM EQUIPE" (id 1) observada no CT 133. Ver Lacuna L1.

| Seção da doc | Coluna | Descrição (doc oficial) | Marcação |
|---|---|---|---|
| Campo | `co_seq_dim_equipe` | Código de identificação sequencial | PK |
| Campo | `nu_ine` | Número identificador da equipe |  |
| Campo | `no_equipe` | Nome da equipe |  |
| Campo | `st_registro_valido` | Identifica se o respectivo registro está completo na tabela do DW. Isto ocorre durante o processamento inicial dos dados. Caso o identificador não exista na tabela, o mesmo é inserido. Neste momento, apenas o identificador existe no DW, então a coluna "st_registro_valido" é marcada com o valor "null" para que seja reprocessado novamente e adicionadas as informações complementares. Este reprocessamento realiza a busca na base de dados do e-SUS APS PEC e, encontrando o identificador, inclui as demais informações e marca o registro como "válido" (valor 1). |  |
| Campo | `ds_filtro` | Concatenação entre o número e nome da equipe, sem acentos |  |

#### `tb_dim_vinculacao_equipes` — Tabela de dimensão de vinculação entre equipes

Fonte: <https://integracao.esusaps.bridge.ufsc.tech/dw/dimensoes/dim_vinculacao_equipes.html> — **Alterado em 13/07/2026**.

Texto da doc (objetivo/regras/notas):

- A tabela `tb_dim_vinculacao_equipes` é utilizada para armazenar os vínculos entre equipes.

> Vínculo **entre equipes** (ex.: equipe de apoio ↔ equipe de referência). Não é o vínculo do cidadão e não traz o tipo de equipe. Incluída na v5.2.4→5.2.5.

| Seção da doc | Coluna | Descrição (doc oficial) | Marcação |
|---|---|---|---|
| Campo | `co_seq_dim_vinculacao_equipes` | Código de identificação sequencial | PK |
| Campo | `nu_ine_equipe` | Número identificador da equipe (INE) |  |
| Campo | `nu_ine_equipe_vinculada` | Número identificador da equipe vinculada (INE) |  |
| Campo | `co_dim_equipe_principal` | Código de identificação da equipe principal. Campo `co_seq_dim_equipe` da `tb_dim_equipe` | FK → `tb_dim_equipe.co_seq_dim_equipe` |
| Campo | `co_dim_equipe_vinculada` | Código de identificação da equipe vinculada. Campo `co_seq_dim_equipe` da `tb_dim_equipe` | FK → `tb_dim_equipe.co_seq_dim_equipe` |
| Campo | `st_registro_valido` | Identifica se o respectivo registro está completo na tabela do DW. Isto ocorre durante o processamento inicial dos dados. Caso o identificador não exista na tabela, o mesmo é inserido. Neste momento, apenas o identificador existe no DW, então a coluna "st_registro_valido" é marcada com o valor "null" para que seja reprocessado novamente e adicionadas as informações complementares. Este reprocessamento realiza a busca na base de dados do e-SUS APS PEC e, encontrando o identificador, inclui as demais informações e marca o registro como "válido" (valor 1). |  |

#### `tb_dim_agrupador_filtro` — Tabela de dimensão de agrupadores de filtro

Fonte: <https://integracao.esusaps.bridge.ufsc.tech/dw/dimensoes/dim_agrupador_filtro.html> — **Alterado em 10/11/2024**.

Texto da doc (objetivo/regras/notas):

- A tabela `tb_dim_agrupador_filtro` é utilizada para organização e apresentação da tela de filtros dos relatórios do e-SUS APS PEC. É a junção dos dados dos profissionais de forma que seja fácil identificar dados básicos relacionados a um profissional durante a montagem de um filtro.
- 1. A tabela é preenchida sempre que um novo fato relacionado a profissional é gerado.
- 2. A coluna referente a microárea é preenchida quando são gerados os fatos de Cadastro Individual, Cadastro Domiciliar e Territorial e Visita Domiciliar e Territorial.

> Única estrutura documentada que associa equipe/unidade a município (`co_dim_municipio` + `co_dim_unidade_saude` + `co_dim_equipe` por profissional). Caminho alternativo de recorte (inferido) para objetos sem `co_dim_municipio`; não é uma tabela 1:1 unidade→município.

| Seção da doc | Coluna | Descrição (doc oficial) | Marcação |
|---|---|---|---|
| Campo | `co_seq_dim_agrupador_filtro` | Código de identificação sequencial | PK |
| Campo | `co_dim_municipio` | Código do município do profissional. Campo `co_seq_dim_municipio` da `tb_dim_municipio` | FK → `tb_dim_municipio.co_seq_dim_municipio` **recorte municipal** |
| Campo | `co_dim_unidade_saude` | Código da unidade de saúde do profissional. Campo `co_seq_dim_unidade_saude` da `tb_dim_municipio` | FK → `tb_dim_municipio.co_seq_dim_unidade_saude` ⚠ doc diz `tb_dim_municipio` (erro de cópia); alvo real é `tb_dim_unidade_saude` |
| Campo | `co_dim_equipe` | Código da equipe do profissional. Campo `co_seq_dim_equipe` da `tb_dim_equipe` | FK → `tb_dim_equipe.co_seq_dim_equipe` |
| Campo | `co_dim_profissional` | Código do profissional. Campo `co_seq_dim_profissional` da `tb_dim_profissional` | FK → `tb_dim_profissional.co_seq_dim_profissional` |
| Campo | `co_dim_cbo` | Código da CBO do profissional. Campo `co_seq_dim_cbo` da `tb_dim_cbo` | FK → `tb_dim_cbo.co_seq_dim_cbo` |
| Campo | `nu_micro_area` | Número da microárea preenchida pelo profissional | *[quase-identificador — não projetar]* |

#### `tb_dim_profissional` — Tabela de dimensão de profissionais

Fonte: <https://integracao.esusaps.bridge.ufsc.tech/dw/dimensoes/dim_profissional.html> — **Alterado em 31/01/2025**.

Texto da doc (objetivo/regras/notas):

- A tabela `tb_dim_profissional` é utilizada para armazenar os profissionais.
- 1. A tabela é preenchida sempre que um profissional realiza um novo registro.

> Dados pessoais do profissional (CNS, nome). Para C2–C7 basta o CBO do fato; não ler esta dimensão.

| Seção da doc | Coluna | Descrição (doc oficial) | Marcação |
|---|---|---|---|
| Campo | `co_seq_dim_profissional` | Código de identificação sequencial | PK |
| Campo | `nu_cns` | CNS do profissional | **[PII — nunca projetar]** |
| Campo | `no_profissional` | Nome do profissional | **[PII — nunca projetar]** |
| Campo | `st_registro_valido` | Identifica se o respectivo registro está completo na tabela do DW. Isto ocorre durante o processamento inicial dos dados. Caso o identificador não exista na tabela, o mesmo é inserido. Neste momento, apenas o identificador existe no DW, então a coluna "st_registro_valido" é marcada com o valor "null" para que seja reprocessado novamente e adicionadas as informações complementares. Este reprocessamento realiza a busca na base de dados do e-SUS APS PEC e, encontrando o identificador, inclui as demais informações e marca o registro como "válido" (valor 1). |  |
| Campo | `ds_filtro` | Concatenação entre o código do IBGE, nome e UF do município, sem acentos | **[PII — nunca projetar]** |

#### `tb_dim_cbo` — Tabela de dimensão de CBO

Fonte: <https://integracao.esusaps.bridge.ufsc.tech/dw/dimensoes/dim_cbo.html> — **Alterado em 10/11/2024**.

Texto da doc (objetivo/regras/notas):

- A tabela `tb_dim_cbo` é utilizada para armazenar CBOs. Possui como referência a tabela de CBO do LEDI.

> `nu_cbo` deve ser tratado como **texto**: a lista LEDI de CBOs tem código com letra (`2235C3`). Códigos da lista LEDI (`/ledi/documentacao/referencias/cbo_disponiveis.html`, Alterado em 19/06/2026): ACS **515105**, TACS **322255**, médico ESF 225142, médico de família 225130, médico clínico 225125, enfermeiro 223505, enfermeiro ESF 223565, cirurgião-dentista ESF 223293 (família 2232: 223204…223293), TSB **322405**, TSB ESF **322425**. Atenção: a família 3224 também contém ASB (322415, 322430), protético (322410) e auxiliar de prótese (322420) — a lista da ficha precisa ser congelada por código, não por prefixo.

| Seção da doc | Coluna | Descrição (doc oficial) | Marcação |
|---|---|---|---|
| Campo | `co_seq_dim_cbo` | Código de identificação sequencial | PK |
| Campo | `nu_cbo` | Número identificador da CBO |  |
| Campo | `no_cbo` | Nome da CBO |  |
| Campo | `st_registro_valido` | Identifica se o respectivo registro está completo na tabela do DW. Isto ocorre durante o processamento inicial dos dados. Caso o identificador não exista na tabela, o mesmo é inserido. Neste momento, apenas o identificador existe no DW, então a coluna "st_registro_valido" é marcada com o valor "null" para que seja reprocessado novamente e adicionadas as informações complementares. Este reprocessamento realiza a busca na base de dados do e-SUS APS PEC e, encontrando o identificador, inclui as demais informações e marca o registro como "válido" (valor 1). |  |
| Campo | `ds_filtro` | Concatenação entre o número e nome da CBO, sem acentos |  |

#### `tb_dim_grupo_cbo` — Tabela de dimensão de grupos de CBO

Fonte: <https://integracao.esusaps.bridge.ufsc.tech/dw/dimensoes/dim_grupo_cbo.html> — **Alterado em 10/11/2024**.

Texto da doc (objetivo/regras/notas):

- A tabela `tb_dim_grupo_cbo` é utilizada para agrupar as CBOs em determinados grupos.
- 1. Múltiplas CBOs podem estar relacionados ao mesmo grupo de CBO.

| Seção da doc | Coluna | Descrição (doc oficial) | Marcação |
|---|---|---|---|
| Campo | `co_seq_dim_grupo_cbo` | Código de identificação sequencial | PK |
| Campo | `nu_grupo_cbo` | Número de identificação do grupo de CBO |  |
| Campo | `ds_grupo_cbo` | Descrição do grupo de CBO |  |
| Campo | `co_dim_cbo` | Código do CBO relacionado ao grupo. Campo `co_seq_dim_cbo` da `tb_dim_cbo` | FK → `tb_dim_cbo.co_seq_dim_cbo` |
| Campo | `ds_filtro` | Descrição do grupo de CBO sem acentos, para utilização em filtros |  |

#### `tb_dim_cidadao_pec_grupo` — Tabela de dimensão de referências de cadastro de um mesmo cidadão

Fonte: <https://integracao.esusaps.bridge.ufsc.tech/dw/dimensoes/dim_cidadao_pec_grupo.html> — **Alterado em 13/07/2026**.

Texto da doc (objetivo/regras/notas):

- A tabela `tb_dim_cidacao_pec_grupo` é utilizada para identificar todas as referências de cadastro de um mesmo cidadão.
- 1. É criado um registro sempre que há um tipo de identificação diferente para o cidadão, ou seja, se no mesmo registro existir CNS, CPF e estiver relacionado a uma Ficha de Cadastro Individual, serão gerados 3 registros nessa tabela.

> Grafia da doc: título/URL `dim_cidadao_pec_grupo`, texto `tb_dim_cidacao_pec_grupo`, FK `tb_fat_cidacao_pec.co_seq_fat_cidacao_pec` — o nome real precisa ser confirmado no catálogo. **Várias linhas por `co_fat_cidadao_pec`** (uma por tipo de identificação: UUID de origem, CNS, CPF). `co_cidadao` e `co_cidadao_master` apontam para `tb_cidadao` (tabela transacional do PEC, fora do DW). Nunca projetar `co_identificacao` (contém CPF/CNS).

| Seção da doc | Coluna | Descrição (doc oficial) | Marcação |
|---|---|---|---|
| Campo | `co_seq_dim_cidadao_pec_grupo` | Código de identificação sequencial | PK |
| Campo | `co_identificacao` | Código de identificação do cidadão. CNS, CPF ou Número do UUID de Origem | **[PII — nunca projetar]** |
| Campo | `tp_identificacao` | 0 - UUID de Origem, 1 - CNS e 2 - CPF |  |
| Campo | `co_fat_cidadao_pec` | Código do fato de cidadão relacionado a esse registro. Campo `co_seq_fat_cidacao_pec` da `tb_fat_cidacao_pec` | FK → `tb_fat_cidacao_pec.co_seq_fat_cidacao_pec` **chave de pessoa** (alvo sem página na doc) ⚠ grafia `cidacao` é da doc |
| Campo | `co_cidadao` | Código do cidadão relacionado a esse registro. Campo `co_seq_cidadao` da `tb_cidadao` | FK → `tb_cidadao.co_seq_cidadao` |
| Campo | `co_cidadao_master` | Código do cidadão unificado. Campo `co_seq_cidadao` da `tb_cidadao` | FK → `tb_cidadao.co_seq_cidadao` |

#### `tb_dim_sexo` — Tabela de dimensão de sexo

Fonte: <https://integracao.esusaps.bridge.ufsc.tech/dw/dimensoes/dim_sexo.html> — **Alterado em 31/01/2025**.

Texto da doc (objetivo/regras/notas):

- A tabela `tb_dim_sexo` é utilizada para armazenar o cadastro de sexo. Possui como referência a Tabela de sexo do LEDI.

> Códigos LEDI (`Sexo`): 0 Masculino; 1 Feminino; 4 Ignorado; 5 Indeterminado. A PK nesta página é `co_seq_dim_sexo`, mas todas as páginas de fato dizem `co_seq_dim_faixa_sexo` — confirmar no catálogo.

| Seção da doc | Coluna | Descrição (doc oficial) | Marcação |
|---|---|---|---|
| Campo | `co_seq_dim_sexo` | Código de identificação sequencial | PK |
| Campo | `nu_identificador` | Número identificador do sexo |  |
| Campo | `ds_sexo` | Descrição do sexo |  |
| Campo | `sg_sexo` | Sigla do sexo |  |
| Campo | `co_ordem` | Código para ordenação do sexo |  |

#### `tb_dim_identidade_genero` — Tabela de dimensão de identidade de gênero

Fonte: <https://integracao.esusaps.bridge.ufsc.tech/dw/dimensoes/dim_identidade_genero.html> — **Alterado em 06/12/2024**.

Texto da doc (objetivo/regras/notas):

- A tabela `tb_dim_identidade_genero` é utilizada para armazenar as identidades de gênero. Possui como referência a tabela de Identidade de gênero do LEDI.

> Códigos LEDI (`identidadeGeneroCidadao`): 149 Homem transgênero; 150 Mulher transgênero; 156 Travesti; 200 Homem cisgênero; 201 Mulher cisgênero; 203 Não-binário; 151 Outro.

| Seção da doc | Coluna | Descrição (doc oficial) | Marcação |
|---|---|---|---|
| Campo | `co_seq_dim_identidade_genero` | Código de identificação sequencial | PK |
| Campo | `nu_identificador` | Número identificador da identidade de gênero |  |
| Campo | `ds_identidade_genero` | Descrição da identidade de gênero |  |
| Campo | `co_ordem` | Código para ordenação da identidade de gênero |  |

#### `tb_dim_tipo_saida_cadastro` — Tabela de dimensão de motivo de saída do cadastro

Fonte: <https://integracao.esusaps.bridge.ufsc.tech/dw/dimensoes/dim_tipo_saida_cadastro.html> — **Alterado em 13/07/2026**.

Texto da doc (objetivo/regras/notas):

- A tabela `tb_dim_tipo_saida_cadastro` é utilizada para armazenar os motivos de saída do cidadão do cadastro. Possui como referência a Saída do cidadão do cadastro do LEDI.

> Códigos LEDI (`MotivoSaida`): **135 Óbito; 136 Mudança de território**.

| Seção da doc | Coluna | Descrição (doc oficial) | Marcação |
|---|---|---|---|
| Campo | `co_seq_dim_tipo_saida_cadastro` | Código de identificação sequencial | PK |
| Campo | `nu_identificador` | Número identificador do motivo de saída do cadastro |  |
| Campo | `ds_dim_tipo_saida_cadastro` | Descrição do motivo de saída do cadastro |  |
| Campo | `co_ordem` | Código para ordenação |  |

#### `tb_dim_faixa_etaria` — Tabela de dimensão de faixa etária

Fonte: <https://integracao.esusaps.bridge.ufsc.tech/dw/dimensoes/dim_faixa_etaria.html> — **Alterado em 06/12/2024**.

Texto da doc (objetivo/regras/notas):

- A tabela `tb_dim_faixa_etaria` é utilizada para armazenar as faixas etárias. Possui como referência a tabela de Tabela de Faixa Etária do LEDI.

> Valores publicados no Anexo 1 de `/dw/dimensoes/index.html`: 1 = menos de 1 ano … 21 = 80 anos ou mais (faixas de 5 anos a partir de 5–9). É a faixa **no momento do registro** — não serve para marcos exatos (30 dias, 2 anos, 9–14, 25–64); usar data de nascimento.

| Seção da doc | Coluna | Descrição (doc oficial) | Marcação |
|---|---|---|---|
| Campo | `co_seq_dim_faixa_etaria` | Código de identificação sequencial | PK |
| Campo | `nu_identificador` | Número identificador da faixa etária |  |
| Campo | `ds_faixa_etaria` | Descrição da faixa etária |  |
| Campo | `ds_filtro` | Nome da faixa etária, sem acentos |  |
| Campo | `nu_faixa_inicial_anos` | Número de anos inicial da faixa etária |  |
| Campo | `nu_faixa_final_anos` | Número de anos final da faixa etária |  |


### 2.10 Dimensões — atendimento, condição, exame e procedimento

#### `tb_dim_tipo_atendimento` — Tabela de dimensão de tipo de atendimento

Fonte: <https://integracao.esusaps.bridge.ufsc.tech/dw/dimensoes/dim_tipo_atendimento.html> — **Alterado em 13/07/2026**.

Texto da doc (objetivo/regras/notas):

- A tabela `tb_dim_tipo_atendimento` é utilizada para armazenar os tipos de atendimento. Possui como referência o Tipo de atendimento do LEDI. Também apresenta a opção "Consultas". A coluna `co_dim_tipo_atendimento_pai` referencia o registro pai, utilizado para agrupamento da informação e visualização hierárquica.

> Códigos LEDI (`TipoDeAtendimento`, dicionário LEDI): 1 Consulta agendada programada / Cuidado continuado; 2 Consulta agendada; 4 Escuta inicial / Orientação; 5 Consulta no dia; 6 Atendimento de urgência; 7 Atendimento programado (só AD); 8 Atendimento não programado (só AD); 9 Visita domiciliar pós-óbito (só AD).
>
> **Os ids do DW são outros**: no CT 133 e no 5.5.28, `co_seq_dim_tipo_atendimento` 2 = "Consulta agendada programada / Cuidado continuado", 3 = "Consulta agendada", 5 = "Escuta inicial / Orientação", 6 = "Consulta no dia", 7 = "Atendimento de urgência"; 1 = "Consultas" e 4 = "Demanda espontânea" são agrupadores. É o caso concreto do aviso do índice de dimensões ("os códigos identificadores de cada opção podem não corresponder aos códigos utilizados no LEDI"). Se `nu_identificador` guarda o código LEDI, ainda não foi verificado.

| Seção da doc | Coluna | Descrição (doc oficial) | Marcação |
|---|---|---|---|
| Campo | `co_seq_dim_tipo_atendimento` | Código de identificação sequencial | PK |
| Campo | `nu_identificador` | Número identificador do tipo de atendimento |  |
| Campo | `ds_tipo_atendimento` | Descrição do tipo de atendimento |  |
| Campo | `co_dim_tipo_atendimento_pai` | Código do tipo de atendimento pai (agrupador hierárquico). Campo `co_seq_dim_tipo_atendimento` da própria `tb_dim_tipo_atendimento` |  |
| Campo | `co_ordem` | Código para ordenação |  |
| Campo | `ds_filtro` | Concatenação de campos para busca, sem acentos |  |

#### `tb_dim_local_atendimento` — Tabela de dimensão de local de atendimento

Fonte: <https://integracao.esusaps.bridge.ufsc.tech/dw/dimensoes/dim_local_atendimento.html> — **Alterado em 06/12/2024**.

Texto da doc (objetivo/regras/notas):

- A tabela `tb_dim_local_atendimento` é utilizada para armazenar os locais de atendimento. Possui como referência a Tabela de local de atendimento.

> Códigos LEDI (`LocalDeAtendimento`): 1 UBS; 2 Unidade móvel; 3 Rua; **4 Domicílio**; 5 Escola/Creche; 6 Outros; 7 Polo (academia da saúde); 8 Instituição/Abrigo; 9 Unidade prisional ou congêneres; 10 Unidade socioeducativa; 11 Hospital, 12 UPA, 13 CACON/UNACON (só AD); 16 UBSI; 17 UBSI Fluvial; 18 Sede de polo base tipo I; 19 CASAI. O LEDI FAI aceita só 1 a 10. Coluna candidata para o código: `nu_identificador` (inferido).

| Seção da doc | Coluna | Descrição (doc oficial) | Marcação |
|---|---|---|---|
| Campo | `co_seq_dim_local_atendimento` | Código de identificação sequencial | PK |
| Campo | `nu_identificador` | Número identificador do local de atendimento |  |
| Campo | `ds_local_atendimento` | Descrição do local de atendimento |  |
| Campo | `co_ordem` | Código para ordenação do local de atendimento |  |
| Campo | `ds_filtro` | Descrição do local de atendimento, sem acentos |  |

#### `tb_dim_tipo_ficha` — Tabela de dimensão de tipo de ficha

Fonte: <https://integracao.esusaps.bridge.ufsc.tech/dw/dimensoes/dim_tipo_ficha.html> — **Alterado em 13/07/2026**.

Texto da doc (objetivo/regras/notas):

- A tabela `tb_dim_tipo_ficha` é utilizada para armazenar os tipos de ficha (modelos de informação) do PEC e-SUS APS. Possui como referência o Tipo de ficha do LEDI. Também apresenta as opções "AD resumo" e "ESUS PEC Atendimento".

> Valores não publicados no DW; a v5.2.16→5.2.17 incluiu "ESUS PEC Cuidado compartilhado". Útil para separar origem (ficha CDS × PEC) — listar ao vivo.

| Seção da doc | Coluna | Descrição (doc oficial) | Marcação |
|---|---|---|---|
| Campo | `co_seq_dim_tipo_ficha` | Código de identificação sequencial | PK |
| Campo | `nu_identificador` | Número identificador do tipo de ficha |  |
| Campo | `ds_tipo_ficha` | Descrição do tipo de ficha |  |
| Campo | `co_ordem` | Código para ordenação |  |
| Campo | `ds_filtro` | Concatenação de campos para busca, sem acentos |  |

#### `tb_dim_tipo_origem` — Tabela de dimensão de tipo de origem do dado

Fonte: <https://integracao.esusaps.bridge.ufsc.tech/dw/dimensoes/dim_tipo_origem.html> — **Alterado em 13/07/2026**.

Texto da doc (objetivo/regras/notas):

- A tabela `tb_dim_tipo_origem` é utilizada para armazenar o sistema de origem do registro. Indica qual sistema gerou a informação.

> Valores (Anexo 1 do índice de dimensões): 0 Offline (CDS offline); 1 Online (CDS); 2 PEC; 3 Externo (LEDI); 4 Android_ACS (e-SUS Território); 5 Android_AC (e-SUS Atividade Coletiva); 6 APP_VACINACAO.

| Seção da doc | Coluna | Descrição (doc oficial) | Marcação |
|---|---|---|---|
| Campo | `co_seq_dim_tipo_origem` | Código de identificação sequencial | PK |
| Campo | `nu_identificador` | Número identificador do tipo de origem |  |
| Campo | `no_tipo_origem` | Nome do tipo de origem |  |

#### `tb_dim_tipo_origem_dado_transp` — Tabela de dimensão de tipo de origem do dado transportado

Fonte: <https://integracao.esusaps.bridge.ufsc.tech/dw/dimensoes/dim_tipo_origem_dado_transp.html> — **Alterado em 13/07/2026**.

Texto da doc (objetivo/regras/notas):

- A tabela `tb_dim_tipo_origem_dado_transp` é utilizada para armazenar a forma como o dado foi registrado na base.

> Valores (Anexo 1): 1 Criado local; 2 Recebido online (outro PEC); 3 Importado de arquivo; 4 Originado em versão < 1.3.00; 5 ACS (e-SUS Território); 6 AC; 7 e-SUS Vacinação; 8 Recebido online externo (API).

| Seção da doc | Coluna | Descrição (doc oficial) | Marcação |
|---|---|---|---|
| Campo | `co_seq_dim_tp_orgm_dado_transp` | Código de identificação sequencial | PK |
| Campo | `nu_identificador` | Número identificador do tipo de origem do dado transportado |  |
| Campo | `no_tipo_origem_dado_transp` | Nome do tipo de origem do dado transportado |  |

#### `tb_dim_situacao_problema` — Tabela de dimensão de situação do problema/condição

Fonte: <https://integracao.esusaps.bridge.ufsc.tech/dw/dimensoes/dim_situacao_problema.html> — **Alterado em 13/07/2026**.

Texto da doc (objetivo/regras/notas):

- A tabela `tb_dim_situacao_problema` é utilizada para armazenar as situações de um problema ou condição de saúde na lista de problemas/condições do cidadão. Possui como referência a Situação de problema ou condição do LEDI.

> Códigos LEDI (`SituacaoProblemasCondicoes`): **0 Ativo, 1 Latente, 2 Resolvido**. Incluída na v5.3.14→5.3.15. A FK nos fatos varia: `co_dim_situacao` (FAI/FAO) e `co_dim_situacao_problema` (atendimento domiciliar).

| Seção da doc | Coluna | Descrição (doc oficial) | Marcação |
|---|---|---|---|
| Campo | `co_seq_dim_situacao` | Código de identificação sequencial | PK |
| Campo | `nu_identificador` | Número identificador da situação do problema ou condição |  |
| Campo | `ds_situacao_problema` | Descrição da situação do problema ou condição |  |

#### `tb_dim_ciap` — Tabela de dimensão de CIAP-2

Fonte: <https://integracao.esusaps.bridge.ufsc.tech/dw/dimensoes/dim_ciap.html> — **Alterado em 10/11/2024**.

Texto da doc (objetivo/regras/notas):

- A tabela `tb_dim_ciap` é utilizada para armazenar as CIAP-2. Possui como referência a Classificação Internacional de Atenção Primária - Segunda Edição (CIAP2) do LEDI.
- 1. São armazenados todos os códigos AB presentes no CDS.
- 2. Quando uma CIAP-2 é informada em um atendimento a tabela é preenchida.

> Os fatos de problemas (FAI/FAO) e o AD (`tb_fat_atend_dom_prob_cond`) apontam para `tb_dim_ciap`; encaminhamentos e cuidado compartilhado apontam para `tb_dim_ciap2` (sem página). Confirmar no catálogo se são a mesma tabela.

| Seção da doc | Coluna | Descrição (doc oficial) | Marcação |
|---|---|---|---|
| Campo | `co_seq_dim_ciap` | Código de identificação sequencial | PK |
| Campo | `nu_ciap` | Número identificador da CIAP |  |
| Campo | `no_ciap` | Nome da CIAP |  |
| Campo | `st_registro_valido` | Identifica se o respectivo registro está completo na tabela do DW. Isto ocorre durante o processamento inicial dos dados. Caso o identificador não exista na tabela, o mesmo é inserido. Neste momento, apenas o identificador existe no DW, então a coluna "st_registro_valido" é marcada com o valor "null" para que seja reprocessado novamente e adicionadas as informações complementares. Este reprocessamento realiza a busca na base de dados do e-SUS APS PEC e, encontrando o identificador, inclui as demais informações e marca o registro como "válido" (valor 1). |  |
| Campo | `ds_filtro` | Concatenação entre o número e nome da CIAP, sem acentos |  |

#### `tb_dim_cid` — Tabela de dimensão de CID-10

Fonte: <https://integracao.esusaps.bridge.ufsc.tech/dw/dimensoes/dim_cid.html> — **Alterado em 10/11/2024**.

Texto da doc (objetivo/regras/notas):

- A tabela `tb_dim_cid` é utilizada para armazenar as CID-10. Possui como referência a Classificação Internacional de Doenças - Versão 10 do LEDI.
- 1. Quando uma CID-10 é informada em um atendimento a tabela é preenchida.

> Os fatos de problemas (FAI/FAO) apontam para `tb_dim_cid`; atendimento domiciliar, encaminhamentos, cuidado compartilhado, vacina (CID de indicação) e elegibilidade apontam para `tb_dim_cid10` (sem página).

| Seção da doc | Coluna | Descrição (doc oficial) | Marcação |
|---|---|---|---|
| Campo | `co_seq_dim_cid` | Código de identificação sequencial | PK |
| Campo | `nu_cid` | Número identificador da CID |  |
| Campo | `no_cid` | Nome da CID |  |
| Campo | `st_ativo` | 0 - Inativo, null ou 1 - Ativo |  |
| Campo | `st_registro_valido` | Identifica se o respectivo registro está completo na tabela do DW. Isto ocorre durante o processamento inicial dos dados. Caso o identificador não exista na tabela, o mesmo é inserido. Neste momento, apenas o identificador existe no DW, então a coluna "st_registro_valido" é marcada com o valor "null" para que seja reprocessado novamente e adicionadas as informações complementares. Este reprocessamento realiza a busca na base de dados do e-SUS APS PEC e, encontrando o identificador, inclui as demais informações e marca o registro como "válido" (valor 1). |  |
| Campo | `ds_filtro` | Concatenação entre o número e nome da CID, sem acentos |  |

#### `tb_dim_procedimento` — Tabela de dimensão de procedimentos

Fonte: <https://integracao.esusaps.bridge.ufsc.tech/dw/dimensoes/dim_procedimento.html> — **Alterado em 31/01/2025**.

Texto da doc (objetivo/regras/notas):

- A tabela `tb_dim_procedimento` é utilizada para armazenar os procedimentos. Possui como referência a Tabela Unificada SIGTAP do LEDI.

> `co_proced` guarda o código SIGTAP (10 dígitos, sem pontuação) **ou** um código AB (ex.: `ABEX022`): o LEDI FAI manda informar o código AB quando o exame não tem SIGTAP, e `co_seq_dim_proced_ref_ab` liga AB↔SIGTAP (inferido). Listas das fichas (ex.: HbA1c `0202010503`, HPV molecular `0202100251`, pé diabético `0301040095`) devem ser aplicadas aos dois tipos de código.

| Seção da doc | Coluna | Descrição (doc oficial) | Marcação |
|---|---|---|---|
| Campo | `co_seq_dim_procedimento` | Código de identificação sequencial | PK |
| Campo | `co_proced` | Código do procedimento |  |
| Campo | `ds_proced` | Descrição do procedimento |  |
| Campo | `co_seq_dim_proced_ref_ab` | Código do procedimento AB referênciado ao procedimento SIGTAP | PK |
| Campo | `co_pai` | - |  |
| Campo | `ds_filtro` | Concatenação entre código e descrição do procedimento, sem acento |  |
| Campo | `st_registro_valido` | Identifica se o respectivo registro está completo na tabela do DW. Isto ocorre durante o processamento inicial dos dados. Caso o identificador não exista na tabela, o mesmo é inserido. Neste momento, apenas o identificador existe no DW, então a coluna "st_registro_valido" é marcada com o valor "null" para que seja reprocessado novamente e adicionadas as informações complementares. Este reprocessamento realiza a busca na base de dados do e-SUS APS PEC e, encontrando o identificador, inclui as demais informações e marca o registro como "válido" (valor 1). |  |

#### `tb_dim_tipo_consulta_odonto` — Tabela de dimensão de tipo de consulta odontológica

Fonte: <https://integracao.esusaps.bridge.ufsc.tech/dw/dimensoes/dim_tipo_consulta_odonto.html> — **Alterado em 13/07/2026**.

Texto da doc (objetivo/regras/notas):

- A tabela `tb_dim_tipo_consulta_odonto` é utilizada para armazenar os tipos de consulta odontológica. Possui como referência o Tipo de consulta do LEDI. Para manter a compatibilidade com versões anteriores, também apresenta as opções "Consulta de conclusão de tratamento" e "Não se aplica (NA)".

> Códigos LEDI (`TipoDeConsultaOdonto`): 1 Primeira consulta odontológica programática; 2 Consulta de retorno; 4 Consulta de manutenção.

| Seção da doc | Coluna | Descrição (doc oficial) | Marcação |
|---|---|---|---|
| Campo | `co_seq_dim_tipo_cnsulta_odonto` | Código de identificação sequencial | PK |
| Campo | `nu_identificador` | Número identificador do tipo de consulta odontológica |  |
| Campo | `ds_tipo_consulta_odonto` | Descrição do tipo de consulta odontológica |  |
| Campo | `co_ordem` | Código para ordenação |  |


### 2.11 Dimensões — visita, atividade coletiva e vacinação

#### `tb_dim_desfecho_visita` — Tabela de dimensão de desfecho de visitas

Fonte: <https://integracao.esusaps.bridge.ufsc.tech/dw/dimensoes/dim_desfecho_visita.html> — **Alterado em 10/11/2024**.

Texto da doc (objetivo/regras/notas):

- A tabela `tb_dim_desfecho_visita` é utilizada para armazenar os desfechos de visita. Possui como referência a tabela de Desfecho Visita do LEDI.

> Códigos LEDI (`Desfecho`): **1 Visita realizada**, 2 Visita recusada, 3 Ausente. O LEDI FVDT proíbe peso/altura/PA quando o desfecho é 2 ou 3.

| Seção da doc | Coluna | Descrição (doc oficial) | Marcação |
|---|---|---|---|
| Campo | `co_seq_dim_desfecho_visita` | Código de identificação sequencial | PK |
| Campo | `nu_identificador` | Número identificador do desfecho |  |
| Campo | `ds_desfecho_visita` | Descrição do desfecho |  |
| Campo | `co_ordem` | Código para ordenação do desfecho |  |

#### `tb_dim_tipo_atividade` — Tabela de dimensão de tipo de atividade coletiva

Fonte: <https://integracao.esusaps.bridge.ufsc.tech/dw/dimensoes/dim_tipo_atividade.html> — **Alterado em 13/07/2026**.

Texto da doc (objetivo/regras/notas):

- A tabela `tb_dim_tipo_atividade` é utilizada para armazenar os tipos de atividade coletiva. Possui como referência a Atividade do LEDI.

> Códigos LEDI (`TipoAtividadeColetiva`): 1 Reunião de equipe; 2 Reunião com outras equipes de saúde; 3 Reunião intersetorial/Conselho local/Controle social; **4 Educação em saúde; 5 Atendimento em grupo; 6 Avaliação / Procedimento coletivo**; 7 Mobilização social. Os "códigos 04/05/06" das fichas correspondem a 4/5/6 aqui (candidato: `nu_identificador`, inferido).

| Seção da doc | Coluna | Descrição (doc oficial) | Marcação |
|---|---|---|---|
| Campo | `co_seq_dim_tipo_atividade` | Código de identificação sequencial | PK |
| Campo | `nu_identificador` | Número identificador do tipo de atividade |  |
| Campo | `ds_tipo_atividade` | Descrição do tipo de atividade |  |
| Campo | `co_categoria_tipo_atividade` | Código da categoria do tipo de atividade (reunião ou ação de saúde) |  |
| Campo | `ds_categoria_tipo_atividade` | Descrição da categoria do tipo de atividade |  |
| Campo | `co_ordem` | Código para ordenação |  |

#### `tb_dim_imunobiologico` — Tabela de dimensão de imunobiológico

Fonte: <https://integracao.esusaps.bridge.ufsc.tech/dw/dimensoes/dim_imunobiologico.html> — **Alterado em 06/12/2024**.

Texto da doc (objetivo/regras/notas):

- A tabela `tb_dim_imunobiologico` é utilizada para armazenar os imunobiológicos. Possui como referência a Tabela de imunobiológicos.
- 1. Quando um imunobiológico é informado em um atendimento a tabela é preenchida.

> Códigos LEDI (`Imunobiologico`) que aparecem nas práticas vacinais (lista **não exaustiva**; vale a lista congelada da ficha): 9 HB; 15 BCG; 17 Hib; 22 VIP; 24 SCR (tríplice viral); 26 VPC10; 29 Penta acelular; 33 Influenza trivalente; 39 Tetra (DTP/Hib); **42 "vacina penta (DTP/HB/Hib)", sigla `PENTA`**; 43 Hexa acelular; 45 Rotavírus; 56 e 73 tetraviral/SCRV; **57 dTpa adulto**; 58 DTPa/VIP; 59 VPC13; 60 HPV2; **67 HPV4**; 77 Influenza tetravalente; 93 HPV9; 106 VPC15; 107 VPC20; 110/117 Influenza alta dosagem. Coluna candidata para o código "42 – Vacina penta" da ficha: **`nu_identificador`** ("Número identificador do imunobiológico"), conferindo `sg_imunobiologico` = `PENTA` — **inferido**; nunca `co_seq_dim_imunobiologico`.

| Seção da doc | Coluna | Descrição (doc oficial) | Marcação |
|---|---|---|---|
| Campo | `co_seq_dim_imunobiologico` | Código de identificação sequencial | PK |
| Campo | `nu_identificador` | Número identificador do imunobiológico |  |
| Campo | `no_imunobiologico` | Nome do imunobiológico |  |
| Campo | `sg_imunobiologico` | Sigla do imunobiológico |  |
| Campo | `ds_filtro` | Concatenação entre o nome e sigla do imunobiológico, sem acentos |  |
| Campo | `st_registro_valido` | Identifica se o respectivo registro está completo na tabela do DW. Isto ocorre durante o processamento inicial dos dados. Caso o identificador não exista na tabela, o mesmo é inserido. Neste momento, apenas o identificador existe no DW, então a coluna "st_registro_valido" é marcada com o valor "null" para que seja reprocessado novamente e adicionadas as informações complementares. Este reprocessamento realiza a busca na base de dados do e-SUS APS PEC e, encontrando o identificador, inclui as demais informações e marca o registro como "válido" (valor 1). |  |

#### `tb_dim_dose_imunobiologico` — Tabela de dimensão de dose de imunobiológico

Fonte: <https://integracao.esusaps.bridge.ufsc.tech/dw/dimensoes/dim_dose_dose_imunobiologico.html> — **Alterado em 10/11/2024**.

Texto da doc (objetivo/regras/notas):

- A tabela `tb_dim_dose_imunobiologico` é utilizada para armazenar as doses dos imunobiológicos. Possui como referência a tabela de Dose de imunobiológico do LEDI.

> Códigos LEDI (`Dose`) mais usados: 1 D1; 2 D2; 3 D3; 4 D4; 5 D5; 6 R1 (1º reforço); 7 R2; 8 D (dose); 9 DU (única); 36 DI (dose inicial); 37 DA (dose adicional); 38 REF (reforço); 57 D0. Candidato: `nu_identificador` (+ `sg_dose_imunobiologico`), inferido. A URL da página tem "dose" duplicado (`dim_dose_dose_imunobiologico`).

| Seção da doc | Coluna | Descrição (doc oficial) | Marcação |
|---|---|---|---|
| Campo | `co_seq_dim_dose_imunobiologico` | Código de identificação sequencial | PK |
| Campo | `nu_identificador` | Número identificador da dose do imunobiológico |  |
| Campo | `sg_dose_imunobiologico` | Sigla da dose do imunobiológico |  |
| Campo | `no_dose_imunobiologico` | Nome da dose do imunobiológico |  |
| Campo | `ds_filtro` | Concatenação entre sigla e nome da dose, sem acentos |  |
| Campo | `nu_ordem` | Código para ordenação da dose do imunobiológico |  |
| Campo | `no_apresentacao_dose` | Nome de apresentação da dose do imunobiológico |  |

#### `tb_dim_estrategia_vacinacao` — Tabela de dimensão de estratégia de vacinação

Fonte: <https://integracao.esusaps.bridge.ufsc.tech/dw/dimensoes/dim_estrategia_vacinacao.html> — **Alterado em 10/11/2024**.

Texto da doc (objetivo/regras/notas):

- A tabela `tb_dim_estrategia_vacinacao` é utilizada para armazenar as estratégias de vacinação. Possui como referência a tabela de Estratégia de vacinação do LEDI.

> Códigos LEDI (`EstrategiaVacinacao`): 1 Rotina; 2 Especial; 3 Bloqueio; 4 Intensificação; 5 Campanha indiscriminada; 6 Campanha seletiva; 7 Soroterapia; 8 Serviço privado; 9 Monitoramento; 11 Pesquisa; 12 Pré-exposição; 13 Pós-exposição; 14 Reexposição; 15 Vacinação escolar. O código **RNDS diverge a partir de 11** (11→10 … 15→14): `nu_identificador` (e-SUS) ≠ `nu_estrategia_vacinacao` (RNDS).

| Seção da doc | Coluna | Descrição (doc oficial) | Marcação |
|---|---|---|---|
| Campo | `co_seq_dim_estrategia_vacinacao` | Código de identificação sequencial | PK |
| Campo | `nu_identificador` | Número identificador da estratégia de vacinação |  |
| Campo | `nu_estrategia_vacinacao` | Código RNDS da estratégia de vacinação |  |
| Campo | `no_estrategia_vacinacao` | Nome da estratégia de vacinação |  |

#### `tb_dim_condicao_maternal` — Tabela de dimensão de condição maternal

Fonte: <https://integracao.esusaps.bridge.ufsc.tech/dw/dimensoes/dim_condicao_maternal.html> — **Alterado em 13/07/2026**.

Texto da doc (objetivo/regras/notas):

- A tabela `tb_dim_condicao_maternal` é utilizada para armazenar as condições maternais da cidadã no momento da vacinação.

> Valores **não publicados** nem no DW nem no dicionário LEDI consultado — listar ao vivo antes de usar.

| Seção da doc | Coluna | Descrição (doc oficial) | Marcação |
|---|---|---|---|
| Campo | `co_seq_dim_condicao_maternal` | Código de identificação sequencial | PK |
| Campo | `nu_identificador` | Número identificador da condição maternal |  |
| Campo | `ds_condicao` | Descrição da condição maternal |  |
| Campo | `co_ordem` | Código para ordenação |  |

### 2.12 Objetos referenciados pelos fatos, mas sem página na doc

Encontrados cruzando todas as FKs citadas nas 39 páginas de fato e na visualização com as 89 páginas
de dimensão.

| Objeto citado | Onde | Coluna FK | O que se sabe | Ação no inventário |
|---|---|---|---|---|
| `tb_dim_tp_particip_cidadao` (FAI) / `tb_dim_tp_participacao_atend` (FAO) | `tb_fat_atendimento_individual`, `tb_fat_atendimento_odonto` | `co_dim_tp_particip_cidadao` | Domínio LEDI `tipoParticipacaoAtendimento` (MI Atendimento Individual, campo #29): **1 Não participou; 2 Presencial; 3 Chamada de vídeo; 4 Chamada de voz; 5 E-mail; 6 Mensagem; 7 Outros** | Achar o nome real e as colunas; listar valores; medir nulos e sentinela em registros antigos ou do CDS |
| `tb_dim_tp_particip_prof_conv` | FAI (e FAO, como `tb_dim_tp_participacao_atend`) | `co_dim_tp_particip_prof_conv` | Mesmo domínio, para o profissional convidado | Idem |
| `tb_dim_tempo_dum` | FAI | `co_dim_tempo_dum` ("Campo `co_seq_dim_tempo_dum`") | Provavelmente é `tb_dim_tempo` (inferido) | Ver FK real no catálogo (`pg_constraint`) |
| `tb_dim_cid10` | Encaminhamentos FAI/FAO (`co_dim_cid10`), cuidado compartilhado (`co_dim_cid`), AD `tb_fat_atend_dom_prob_cond` (`co_dim_cid`), vacina (`co_dim_cid_motivo_indicacao`), elegibilidade (`co_dim_cid_principal`, `co_dim_cid_sec_*`) | ver coluna ao lado | A página existente é `tb_dim_cid` (`co_seq_dim_cid`), usada pelos problemas de FAI/FAO | Confirmar se são tabelas distintas ou erro da doc |
| `tb_dim_ciap2` | Encaminhamentos FAI/FAO (`co_dim_ciap2`), cuidado compartilhado (`co_dim_ciap`) | ver coluna ao lado | A página existente é `tb_dim_ciap` (`co_seq_dim_ciap`), usada pelos problemas de FAI/FAO e pelo AD | Idem |
| `tb_dim_imunobiologico_fabrc` | `tb_fat_vacinacao_vacina` | `co_dim_imunobiologico_fabrc` | Fabricante; desnecessário para C2–C7 | Não ler |
| `tb_dim_prof_finalizador_obs`, `tb_dim_cbo_finalizador_obs`, `tb_dim_ubs_finalizador_obs`, `tb_dim_equipe_finalizador_obs` | FAI | `co_dim_*_finalizador_obs` | Finalizador de atendimento em observação | Não ler |
| `tb_dim_conclusao_modalidade_ad` | `tb_fat_avaliacao_elegibilidade` | — | Fora de C2–C7 | Não ler |
| `tb_fat_cidadao_pec` | Alvo de `co_fat_cidadao_pec` em quase todos os fatos | `co_fat_cidadao_pec` | Sem página; listada na Tabela 1 de `/dw/fatos/index.html` como "Utilizada nos relatórios operacionais" (tabelas que "em breve" serão descontinuadas) | Tratar `co_fat_cidadao_pec` como chave opaca; **não fazer JOIN** com `tb_fat_cidadao_pec` sem inventário |
| `tb_cidadao` | `tb_dim_cidadao_pec_grupo.co_cidadao` e `co_cidadao_master`; `tb_acomp_cidadaos_vinculados.co_cidadao` | — | Tabela **transacional** do PEC, fora do DW | Não ler (usar só os códigos) |
| `tb_fat_rel_op_gestante`, `tb_fat_rel_op_crianca`, `tb_fat_cidadao`, `tb_fat_cidadao_territorio`, `tb_fat_consolidado_cidadao_fci`, `tb_fat_consolidado_cidadao_fvd`, `tb_fat_fichas`, `tb_fat_familia`, `tb_fat_familia_territorio`, `tb_fat_rel_op_risco_cardio` | Tabela 1 de `/dw/fatos/index.html` | — | Só nome e finalidade ("relatórios operacionais", "relatórios consolidados de cadastro" ou "registrar as fichas presentes no DW"); **colunas não documentadas**; as de relatório operacional são candidatas à descontinuação | Não usar como fonte de C2–C7. `tb_fat_rel_op_gestante` pode ser tentadora para a gestação, mas não tem contrato publicado |

### 2.13 Divergências internas da documentação

Elas afetam diretamente a escrita das consultas. Em todos os casos, decide o catálogo da instalação
(`information_schema.columns` e `pg_constraint`), não a doc.

1. **Sexo:** todas as páginas de fato dizem "Campo `co_seq_dim_faixa_sexo` da `tb_dim_sexo`", mas a
   página da dimensão define a PK como `co_seq_dim_sexo`.
2. **CID/CIAP:** os problemas de FAI e FAO apontam para `tb_dim_cid` (`co_seq_dim_cid`) e `tb_dim_ciap`
   (`co_seq_dim_ciap`), que têm página. O CID do AD, dos encaminhamentos, do cuidado compartilhado, da
   vacina e da elegibilidade aponta para `tb_dim_cid10` (`co_seq_dim_cid10`). O CIAP dos encaminhamentos
   e do cuidado compartilhado aponta para `tb_dim_ciap2` (`co_seq_dim_ciap2`); o do AD, para `tb_dim_ciap`.
   `tb_dim_cid10` e `tb_dim_ciap2` não têm página.
3. **Situação do problema:** a PK da dimensão é `co_seq_dim_situacao`. A FK se chama `co_dim_situacao`
   em FAI/FAO e `co_dim_situacao_problema` em AD, onde a doc ainda cita `co_seq_dim_situacao_problema`.
4. **Tipo de participação:** o FAI cita `tb_dim_tp_particip_cidadao` e o FAO cita
   `tb_dim_tp_participacao_atend`. Nenhuma das duas tem página.
5. **Grupo de cidadão:** a página `dim_cidadao_pec_grupo.html` escreve `tb_dim_cidacao_pec_grupo` e
   `tb_fat_cidacao_pec.co_seq_fat_cidacao_pec`.
6. **Procedimentos:** `tb_fat_proced_atend.co_fat_procedimento` cita `tb_fat_procedimentos` (plural), e
   a página é `tb_fat_procedimento`. Em `tb_fat_proced_atend_proced`, o mesmo campo aponta "da
   `tb_fat_procedimentos` e `tb_fat_proced_atend`", e o `nu_atendimento` é "da `tb_fat_proced_atend`",
   de modo que a chave de junção da lista com o atendimento fica implícita.
7. **eMulti:** `st_nasf_*` "substituído pelo `st_emulti_*` a partir da versão 5.5.0 do LEDI", mas as
   colunas `st_emulti_*` não estão na tabela de colunas do FAI.
8. **Cópias erradas:** `tb_fat_consolidado_cidadao_fai.st_risco_cardio` repete a descrição do teste do
   olhinho; `tb_dim_agrupador_filtro.co_dim_unidade_saude` diz "da `tb_dim_municipio`";
   `tb_dim_profissional.ds_filtro` descreve "código do IBGE, nome e UF do município";
   `tb_fat_visita_domiciliar` e `tb_fat_atendimento_domiciliar` listam a justificativa de CPF duas vezes
   (`co_dim_just_nao_possui_cpf` e `codimjustnaopossui_cpf`).
9. **Tempo:** `tb_fat_atd_ind_problemas` lista `co_dim_tempo` duas vezes. `co_dim_tempo_morador_rua`
   (cadastro individual) aponta para `tb_dim_tempo`, embora exista a página `tb_dim_tempo_morador_rua`.
10. **Município do cadastro:** em `tb_fat_cad_individual`, `co_dim_municipio_cidadao` é "município de
    **nascimento**". Em `tb_fat_cad_domiciliar`, a mesma coluna é "município de localização do
    domicílio".
11. **Contagem:** o índice de dimensões lista 89 dimensões, e há 89 páginas (não 90). Doze dimensões
    citadas pelos fatos não têm página (2.12).
12. **Códigos LEDI × DW:** o índice de dimensões avisa que "os códigos identificadores de cada opção
    podem não corresponder aos códigos utilizados no LEDI". O C1 já mostrou isso em
    `tb_dim_tipo_atendimento` (ver a nota em 2.10).

## 3. Mapa ficha → DW

O "dado exigido" segue a lista desta fase e o resumo das fichas na Tech Spec §2.4 (Q02–Q07). As listas
de códigos (CBO, CIAP, CID, SIGTAP, imunobiológicos) continuam sendo as das fichas congeladas; aqui só
se localiza **onde** cada dado está. Evidência: caminho relativo a
`https://integracao.esusaps.bridge.ufsc.tech/dw/`. "LEDI:" indica uma página do LEDI no mesmo site.
FAI = `tb_fat_atendimento_individual`; FP = fatos de procedimentos; FCI = `tb_fat_cad_individual`.

### 3.1 Coorte, pessoa e equipe

| Dado exigido | Ficha(s)/prática(s) | tabela.coluna candidata | Evidência | Confiança | Observação |
|---|---|---|---|---|---|
| Identificador do cidadão entre fatos | todas | `<fato>.co_fat_cidadao_pec` | todas as páginas de fato (ex.: `fatos/vacinacao/tb_fat_vacinacao.html`) | documentado | FK para `tb_fat_cidadao_pec`, que não tem página. As filhas sem a coluna herdam do pai (5.2) |
| Unificação de cadastros do mesmo cidadão | todas | `tb_dim_cidadao_pec_grupo.co_cidadao_master` (via `co_fat_cidadao_pec`) | `dimensoes/dim_cidadao_pec_grupo.html` | coluna documentada; uso como chave de coorte inferido | Várias linhas por cidadão: usar `DISTINCT (co_fat_cidadao_pec, co_cidadao_master)`. Nunca projetar `co_identificacao` |
| Data de nascimento | C2 (dias de vida; 2 anos), C6 (≥ 60), C7 (9–14, 14–69, 25–64, 50–69) | `tb_fat_cad_individual.dt_nascimento` (versão vigente); alternativa `tb_acomp_cidadaos_vinculados.dt_nascimento_cidadao`. Cada fato também repete `dt_nascimento` | `fatos/cadastro_individual/tb_fat_cad_individual.html`; `visualizacoes/acompanhamento_cidadaos_vinculados.html` | documentado | Escolher uma fonte canônica e medir a divergência entre fatos |
| Sexo | C7 (B; elegibilidade) | `tb_fat_cad_individual.co_dim_sexo` → `tb_dim_sexo.nu_identificador`/`sg_sexo` (LEDI 0 M, 1 F, 4 Ignorado, 5 Indeterminado); na visualização, `no_sexo_cidadao` (texto) | FCI; `dimensoes/dim_sexo.html`; LEDI: `referencias/dicionario.html#sexo` | coluna documentada; código inferido | A PK da dimensão diverge (2.13 item 1). Os fatos de evento têm `co_dim_sexo` próprio, que é o sexo no momento do registro |
| Identidade de gênero | C7 | `tb_fat_cad_individual.co_dim_identidade_genero` → `tb_dim_identidade_genero.nu_identificador` (LEDI 149, 150, 156, 200, 201, 203, 151); `st_informar_identidade_genero`; na visualização, `tp_identidade_genero_cidadao` (texto) | FCI; `dimensoes/dim_identidade_genero.html` | coluna documentada; código inferido | Os valores textuais da visualização não estão documentados |
| Óbito | todas (exclusão) | `tb_fat_cad_individual.dt_obito`; `co_dim_tipo_saida_cadastro` → `tb_dim_tipo_saida_cadastro.nu_identificador` = 135 | FCI; `dimensoes/dim_tipo_saida_cadastro.html`; LEDI: `#motivosaida` | documentado; código inferido | A visualização não tem óbito. `nu_obito_do` é PII |
| Saída do cadastro por mudança de território | todas (vínculo) | `co_dim_tipo_saida_cadastro` → `nu_identificador` = 136, com a data no `co_dim_tempo` da versão que registrou a saída. Para a família, `tb_fat_cad_dom_familia.st_mudou` | FCI; `fatos/cadastro_domiciliar/tb_fat_cad_dom_familia.html` | coluna documentada; data e código inferidos | — |
| Vínculo à equipe (INE/CNES) — situação atual | todas (denominadores de "vinculados") | `tb_acomp_cidadaos_vinculados.nu_ine_vinc_equipe`, `nu_cnes_vinc_equipe`, `co_fat_cidadao_pec`, `co_cidadao`, `st_possui_fci`, `st_possui_fcdt`, `dt_ultima_atualizacao_cidadao` | `visualizacoes/acompanhamento_cidadaos_vinculados.html` | documentado | Sem histórico. Segue a regra local do PEC, não o vínculo do Siaps. Não tem código IBGE |
| Vínculo na data de corte (histórico) | todas | `tb_fat_cad_individual.co_dim_equipe` → `tb_dim_equipe.nu_ine` e `co_dim_unidade_saude` → `nu_cnes` da última versão com `co_dim_tempo` ≤ corte, mais `co_dim_tempo_validade`, `st_ficha_inativa`, `st_recusa_cadastro` e `co_dim_tipo_saida_cadastro` | FCI | inferido | A doc chama essas FKs de equipe/unidade "do profissional responsável"; a visualização diz que o vínculo padrão é "a equipe definida no Cadastro Individual" |
| Diabetes autorreferida | C4 | `tb_fat_cad_individual.st_diabete` | FCI | documentado | — |
| Hipertensão autorreferida | C5 | `tb_fat_cad_individual.st_hipertensao_arterial` | FCI | documentado | — |
| Gestante (autorreferida ou no registro) | C3 (apoio) | `tb_fat_cad_individual.st_gestante`; `tb_fat_atendimento_odonto.st_gestante`; `tb_fat_vacinacao.co_dim_condicao_maternal` | FCI; `fatos/atendimento_odontologico/tb_fat_atendimento_odonto.html`; `fatos/vacinacao/tb_fat_vacinacao.html` | documentado | Os valores de `tb_dim_condicao_maternal` não estão publicados |
| Tipo de equipe (eSF 70 / eAP 76) | C2 D, C3 E/J, C4 D, C5 e C6 (visita "não condicionante" na eAP) | **nenhuma** | `dimensoes/dim_equipe.html` (só `nu_ine`, `no_equipe`) | — | **Lacuna L1** |

### 3.2 Atendimento individual

| Dado exigido | Ficha(s)/prática(s) | tabela.coluna candidata | Evidência | Confiança | Observação |
|---|---|---|---|---|---|
| Data do atendimento | C2–C7 | FAI `co_dim_tempo` → `tb_dim_tempo.dt_registro`; `dt_inicial_atendimento` | `fatos/atendimento_individual/tb_fat_atendimento_individual.html`; `dimensoes/dim_tempo.html` | documentado; **validado ao vivo (C1)** | `dt_registro` é a data local de `dt_inicial_atendimento` (C1, 2026-03) |
| CBO (médico/enfermeiro) | C2–C7 | FAI `co_dim_cbo_1` → `tb_dim_cbo.nu_cbo` (`_2` é o segundo participante) | FAI; `dimensoes/dim_cbo.html` | documentado; uso de `_1` validado (C1) | Tratar `nu_cbo` como texto e aplicar a lista da ficha por código |
| Equipe e unidade do atendimento | atribuição | FAI `co_dim_equipe_1` → `nu_ine`; `co_dim_unidade_saude_1` → `nu_cnes` | FAI | documentado; validado (C1) | — |
| Tipo de atendimento | C2–C6 (consultas) | FAI `co_dim_tipo_atendimento` → `tb_dim_tipo_atendimento` | FAI; `dimensoes/dim_tipo_atendimento.html` | documentado; ids 2, 3, 5, 6 e 7 validados (C1) | A escuta inicial de nível superior gera linha de FAI (seção Regras); a ficha decide se ela conta como consulta |
| Local (UBS, domicílio etc.) | C2 A ("presencial") e demais, conforme a ficha | FAI `co_dim_local_atendimento` → `tb_dim_local_atendimento.nu_identificador` (LEDI 4 = Domicílio) | FAI; `dimensoes/dim_local_atendimento.html`; LEDI: `#localdeatendimento` | coluna documentada; código inferido | — |
| Presencial × remoto (teleconsulta) | C2 A (presencial), C2 B (presencial ou remota) | FAI `co_dim_tp_particip_cidadao` → dimensão sem página (LEDI 2 = Presencial; 3–7 = remotos; 1 = Não participou) | FAI; LEDI: `estrutura_arquivos/dicionario-fai.html` #29 | coluna documentada; dimensão e códigos inferidos | **Lacuna L3**: nome da dimensão e nulos em registros antigos ou do CDS |
| Consulta de puericultura / pré-natal | C2 B, C3 B | Não há marcador. A FAI também recebe "atendimento de puericultura" e "de pré-natal" (Regras); identificar pelos CIAP/CID da ficha em `tb_fat_atd_ind_problemas` (ou `ds_filtro_ciaps`/`ds_filtro_cids`) | FAI (Regras) | inferido | `tb_fat_consolidado_cidadao_fai.co_dim_tempo_cnslta_1_prcltra` e `co_dim_tempo_consulta_prcltra` guardam só o estado atual: não usar. **Lacuna L7** |
| CIAP-2/CID-10 avaliados (normalizado) | C3 (gestação, desfecho, aborto), C4/C5 (condição avaliada), C7 C (saúde sexual/reprodutiva) | `tb_fat_atd_ind_problemas.co_dim_ciap` → `tb_dim_ciap.nu_ciap`; `co_dim_cid` → `tb_dim_cid.nu_cid`; `st_avaliado`; `co_fat_atd_ind` | `fatos/atendimento_individual/tb_fat_atd_ind_problemas.html`; `dimensoes/dim_ciap.html`; `dimensoes/dim_cid.html` | documentado | Preferir à agregação textual |
| CIAP/CID agregados no atendimento | idem (atalho) | FAI `ds_filtro_ciaps`, `ds_filtro_cids` (+ `ds_filtro_ciap_motivo_consulta`, `ds_filtro_ciap_plano`) | FAI | documentado; formato do texto não documentado | Não usar sem medir o formato |
| Situação do problema (ativo/latente/resolvido) e datas | C4/C5 (interrupção quando todas as condições estão resolvidas), C3 | `tb_fat_atd_ind_problemas.co_dim_situacao` → `tb_dim_situacao_problema.nu_identificador` (LEDI 0/1/2); `co_dim_data_inicio_problema` e `co_dim_data_fim_problema` → `tb_dim_tempo`; evolução por `nu_uuid_problema` + `co_sequencial_evolucao` | idem; `dimensoes/dim_situacao_problema.html`; LEDI: `#situacaoproblemascondicoes` | documentado; códigos inferidos | Situação vigente = última evolução de cada `nu_uuid_problema` até o corte (inferido) |
| Peso e altura no mesmo dia | C2 C, C3 D, C4 C, C5, C6 | FAI `nu_peso`, `nu_altura`; FP `tb_fat_proced_atend.nu_peso`, `nu_altura`; visita `nu_peso`, `nu_altura`; atividade coletiva `tb_fat_atvdd_coletiva_part.nu_participante_peso`, `nu_participante_altura`; FAO `nu_peso`, `nu_altura` | páginas respectivas | documentado | Quais fontes contam é decisão da ficha. A antropometria da escuta inicial de **nível médio** está em FP, não em FAI |
| PA sistólica e diastólica | C3 C, C4 B, C5 | FAI `nu_medicao_pressao_sistolica`, `nu_medicao_pressao_diastolica`; FP `tb_fat_proced_atend` (mesmas colunas); visita `nu_medicao_pressao_arterial` (coluna única) | FAI; `fatos/procedimentos/tb_fat_proced_atend.html`; `fatos/visita_domiciliar/tb_fat_visita_domiciliar.html` | documentado; formato da visita não documentado | **Lacuna L6** (visita). Não há PA em atividade coletiva nem no FAO |
| DUM | C3 (idade gestacional e marcos) | FAI `co_dim_tempo_dum` → "`tb_dim_tempo_dum`" (sem página) | FAI; LEDI: `dicionario-fai.html` #10 | coluna documentada; dimensão inferida (`tb_dim_tempo`) | — |
| Idade gestacional | C3 (12ª e 20ª semanas; trimestres) | FAI `nu_idade_gestacional_semanas` (LEDI 1–42). USG obstétrica: `tb_fat_atd_ind_exames.nu_resultado_semana`, `nu_resultado_dia`, `dt_resultado_data` (SIGTAP 0205020143, 0205020151 e 0205010059) | FAI; `fatos/atendimento_individual/tb_fat_atd_ind_exames.html` | documentado | — |
| **Data de desfecho da gestação** | C3 (fim da gestação; puerpério de 42 dias; sem desfecho, 294 dias) | **Não documentada.** Candidatos: (a) `tb_fat_atd_ind_problemas.co_dim_data_fim_problema` do problema de gestação com situação Resolvido; (b) `co_dim_tempo` do primeiro atendimento com CIAP/CID de parto ou aborto (o LEDI FAI cita "CID de desfecho de gestação", ex.: O80) | FAI problemas; LEDI: `dicionario-fai.html` | inferido | **Lacuna L2** |
| Exames solicitados / avaliados | C3 G/H (sífilis, HIV, hepatites), C4 E (HbA1c `0202010503`), C7 A (citopatológico; HPV molecular `0202100251`), C7 D (mamografia) | `tb_fat_atd_ind_procedimentos.co_dim_procedimento_solicitado` / `co_dim_procedimento_avaliado` → `tb_dim_procedimento.co_proced`; data por `co_dim_tempo` ou `dt_inicial_atendimento`; agregados FAI `ds_filtro_proced_solicitados` / `ds_filtro_proced_avaliados` | `fatos/atendimento_individual/tb_fat_atd_ind_procedimentos.html`; `dimensoes/dim_procedimento.html` | documentado | Aplicar as listas em SIGTAP **e** AB (`co_seq_dim_proced_ref_ab`) |
| Resultado estruturado (datas de solicitação, realização e resultado) | C3 G/H ("exames avaliados" no trimestre), C4 E | `tb_fat_atd_ind_exames.co_dim_procedimento`, `dt_solicitacao`, `dt_realizacao`, `dt_resultado` | `fatos/atendimento_individual/tb_fat_atd_ind_exames.html` | documentado | Só exames da "Lista de exames com resultado estruturado" (LEDI) |

### 3.3 Procedimentos (MIP)

| Dado exigido | Ficha(s)/prática(s) | tabela.coluna candidata | Evidência | Confiança | Observação |
|---|---|---|---|---|---|
| Procedimento realizado (SIGTAP/AB) | C3 G/H (testes rápidos), C4 F (pé diabético `0301040095`), C7 A (coleta do citopatológico) | `tb_fat_proced_atend_proced.co_dim_procedimento` → `tb_dim_procedimento.co_proced`; atendimento em `tb_fat_proced_atend` | `fatos/procedimentos/tb_fat_proced_atend_proced.html`, `tb_fat_proced_atend.html`, `tb_fat_procedimento.html` | documentado; junção lista↔atendimento inferida | Procedimento SIGTAP/AB lançado no plano ou na finalização de um atendimento do PEC também cai aqui (Regras de `tb_fat_procedimento`) |
| CBO, data, equipe e cidadão do procedimento | idem | `tb_fat_proced_atend` e `tb_fat_proced_atend_proced`: `co_dim_cbo`, `co_dim_tempo`, `co_dim_equipe`, `co_dim_unidade_saude`, `co_fat_cidadao_pec`; `tb_fat_proced_atend.dt_inicial_atendimento` | idem | documentado | — |
| Medições na ficha de procedimentos | ver peso/altura e PA | `tb_fat_proced_atend.nu_peso`, `nu_altura`, `nu_medicao_pressao_sistolica`, `nu_medicao_pressao_diastolica`, `st_escuta_inicial` | `fatos/procedimentos/tb_fat_proced_atend.html` | documentado | — |
| Produção consolidada (sem cidadão) | nenhuma prática por pessoa | `tb_fat_procedimento.nr_proc_consdd_*` | `fatos/procedimentos/tb_fat_procedimento.html` | documentado | Não serve para práticas individuais |

### 3.4 Visita domiciliar (MIVDT)

| Dado exigido | Ficha(s)/prática(s) | tabela.coluna candidata | Evidência | Confiança | Observação |
|---|---|---|---|---|---|
| Data da visita | C2 D, C3 E/J, C4 D, C5, C6 | `tb_fat_visita_domiciliar.co_dim_tempo` → `dt_registro` | `fatos/visita_domiciliar/tb_fat_visita_domiciliar.html` | documentado | Não há `dt_inicial_atendimento` |
| CBO (ACS 515105, TACS 322255) | idem | `co_dim_cbo` → `tb_dim_cbo.nu_cbo` | idem; LEDI: `referencias/cbo_disponiveis.html` | documentado | — |
| Desfecho | idem | `co_dim_desfecho_visita` → `tb_dim_desfecho_visita.nu_identificador` (LEDI 1 = realizada) | idem; `dimensoes/dim_desfecho_visita.html` | coluna documentada; código inferido | — |
| Cidadão visitado | idem | `tb_fat_visita_domiciliar.co_fat_cidadao_pec` | idem | documentado | O comportamento em visita a imóvel sem cidadão não está documentado |
| Motivo (gestante, puérpera, RN, criança, condição, idoso) | se a ficha exigir | `st_acomp_gestante`, `st_acomp_puerpera`, `st_acomp_recem_nascido`, `st_acomp_crianca`, `st_acomp_pessoa_diabetes`, `st_acomp_pessoa_hipertensao`, `st_acomp_pessoa_idosa` etc. | idem | documentado | — |
| Peso, altura e PA na visita | conforme a ficha | `nu_peso`, `nu_altura`, `nu_medicao_pressao_arterial` | idem | documentado | O LEDI proíbe esses campos quando o desfecho é 2 ou 3 |
| Equipe e unidade da visita | atribuição | `co_dim_equipe` → `nu_ine`; `co_dim_unidade_saude` → `nu_cnes` | idem | documentado | — |

### 3.5 Atividade coletiva (MIAC)

| Dado exigido | Ficha(s)/prática(s) | tabela.coluna candidata | Evidência | Confiança | Observação |
|---|---|---|---|---|---|
| Tipo de atividade (códigos 04/05/06) | conforme a ficha (antropometria) | `tb_fat_atividade_coletiva.co_dim_tipo_atividade` → `tb_dim_tipo_atividade.nu_identificador` (LEDI 4 Educação em saúde, 5 Atendimento em grupo, 6 Avaliação/Procedimento coletivo) | `fatos/atividade_coletiva/tb_fat_atividade_coletiva.html`; `dimensoes/dim_tipo_atividade.html` | coluna documentada; código inferido | `tb_fat_atvdd_coletiva_part` também tem `co_dim_tipo_atividade` |
| Participante com peso e altura | C2 C, C3 D, C4 C, C5, C6 (se a ficha aceitar) | `tb_fat_atvdd_coletiva_part.co_fat_cidadao_pec`, `nu_participante_peso`, `nu_participante_altura`, `co_dim_tempo` | `fatos/atividade_coletiva/tb_fat_atvdd_coletiva_part.html` | documentado | **Sem PA** (Lacuna L5) |
| Prática "antropometria" | idem | `tb_fat_atvdd_coletiva_ext.st_prat_saude_antropometria` (por `co_fat_atividade_coletiva`) | `fatos/atividade_coletiva/tb_fat_atvdd_coletiva_ext.html` | documentado | — |
| Profissional, equipe e unidade da atividade | atribuição | `tb_fat_atividade_coletiva.co_dim_cbo`, `co_dim_equipe`, `co_dim_unidade_saude`; outros profissionais em `tb_fat_atvdd_coletiva_propart` | idem | documentado | — |

### 3.6 Vacinação (MIV)

| Dado exigido | Ficha(s)/prática(s) | tabela.coluna candidata | Evidência | Confiança | Observação |
|---|---|---|---|---|---|
| Imunobiológico (código da ficha, ex.: "42 – Vacina penta") | C2 E, C3 F (dTpa), C6 D (influenza), C7 B (HPV) | `tb_fat_vacinacao_vacina.co_dim_imunobiologico` → `tb_dim_imunobiologico.nu_identificador`, conferindo `sg_imunobiologico` | `fatos/vacinacao/tb_fat_vacinacao_vacina.html`; `dimensoes/dim_imunobiologico.html`; LEDI: `#imunobiologico` | coluna documentada; `nu_identificador` = código LEDI é **inferido** | Nunca filtrar por `co_seq_dim_imunobiologico` |
| Dose | C2 E (esquema e doses) | `co_dim_dose_imunobiologico` → `tb_dim_dose_imunobiologico.nu_identificador` / `sg_dose_imunobiologico` | idem; `dimensoes/dim_dose_dose_imunobiologico.html` | coluna documentada; código inferido | — |
| Data de aplicação | todas as práticas vacinais | `co_dim_tempo_vacina_aplicada` → `tb_dim_tempo.dt_registro` | `fatos/vacinacao/tb_fat_vacinacao_vacina.html` | documentado | Na transcrição, difere de `co_dim_tempo` (inferido) |
| Registro anterior / transcrição de caderneta | C2 E ("transcrições") | `st_registro_anterior` (+ `st_aplicado_exterior`) | idem; LEDI: `estrutura_arquivos/dicionario-fv.html` #7/#8 e `regras/validar_regras_vacinacao.html` | documentado | — |
| Estratégia | conforme a ficha | `co_dim_estrategia_vacinacao` → `tb_dim_estrategia_vacinacao.nu_identificador` (e-SUS) / `nu_estrategia_vacinacao` (RNDS) | `dimensoes/dim_estrategia_vacinacao.html` | documentado | Os dois códigos divergem a partir de 11 |
| Cidadão vacinado | todas | `tb_fat_vacinacao.co_fat_cidadao_pec`, via `tb_fat_vacinacao_vacina.co_fat_vacinacao` = `tb_fat_vacinacao.co_seq_fat_vacinacao` | `fatos/vacinacao/tb_fat_vacinacao.html` | documentado | — |
| Condição maternal na vacinação | C3 F | `tb_fat_vacinacao.co_dim_condicao_maternal` → `tb_dim_condicao_maternal` | idem; `dimensoes/dim_condicao_maternal.html` | documentado (valores não publicados) | — |
| Doses registradas fora do PEC (RNDS/RIA) | C2 E, C3 F, C6 D, C7 B | **nenhuma** | Regras de `tb_fat_vacinacao` (não citam RNDS) | — | **Lacuna L4** |

### 3.7 Atendimento odontológico (MIAO)

| Dado exigido | Ficha(s)/prática(s) | tabela.coluna candidata | Evidência | Confiança | Observação |
|---|---|---|---|---|---|
| Atendimento na gestação por dentista ou TSB | C3 K | `tb_fat_atendimento_odonto.co_dim_tempo`, `co_dim_cbo_1` → `nu_cbo` (2232xx; 322405/322425), `st_gestante`, `co_fat_cidadao_pec`, `co_dim_tipo_atendimento`, `co_dim_tipo_consulta` | `fatos/atendimento_odontologico/tb_fat_atendimento_odonto.html` | documentado | O prefixo 3224 também inclui ASB e protético: congelar os códigos |
| Procedimentos odontológicos | C3 K (se a ficha exigir) | `tb_fat_atend_odonto_proced.co_dim_procedimento`, `qt_procedimentos` | `fatos/atendimento_odontologico/tb_fat_atend_odonto_proced.html` | documentado | — |

### 3.8 Recorte e tempo

| Dado exigido | Ficha(s)/prática(s) | tabela.coluna candidata | Evidência | Confiança | Observação |
|---|---|---|---|---|---|
| Município | todas | `<fato>.co_dim_municipio` → `tb_dim_municipio.co_ibge` | todas as páginas de fato; `dimensoes/dim_municipio.html` | documentado; validado só para FAI | Ver 5.1 |
| Competência e janelas | todas | `<fato>.co_dim_tempo` → `tb_dim_tempo.dt_registro`, mais as datas específicas (aplicação, início e fim do problema, exames) | `dimensoes/dim_tempo.html` | documentado | Ver 5.1 |

## 4. Lacunas e riscos

### 4.1 Lacunas (dado exigido sem lugar documentado)

| # | Lacuna | O que a doc mostra | Consequência e saída possível |
|---|---|---|---|
| L1 | **Tipo de equipe** (eSF 70 / eAP 76) | `tb_dim_equipe` tem só `nu_ine`, `no_equipe`, `st_registro_valido` e `ds_filtro`. `tb_dim_vinculacao_equipes` liga equipes entre si. A visualização traz só INE, CNES e nome. A busca no índice do site por "tipo de equipe" e "tipoEquipe" não acha nada | As exceções eAP (C2 D, C3 E/J, visita em C4–C6) não podem ser aplicadas a partir do DW. Saídas a decidir (ADR), sem presumir: (a) o inventário procura o tipo fora do DW, no esquema transacional do PEC, o que muda o modelo de leitura `PEC_DW`; (b) uma fonte externa CNES (INE → tipo) como `EXTERNAL_DATASET`, com `OrganizationSnapshot` datado (Tech Spec §1.7.3); (c) **nunca** inferir pelo texto de `no_equipe`. Enquanto isso, equipe sem tipo comprovado sai com limitação, sem pontuação integral presumida |
| L2 | **Data de desfecho da gestação** (e tipo de desfecho) | Nenhum fato documenta "desfecho", "data do parto" ou "fim da gestação". Existem DUM (`co_dim_tempo_dum`), idade gestacional, problemas com `co_dim_data_fim_problema` e situação, e CIAP/CID por atendimento. O LEDI FAI fala em "CID de desfecho de gestação" (ex.: O80) só numa regra de validação | Candidatos **inferidos**: fim do problema de gestação resolvido, ou data do primeiro registro de CID/CIAP de parto ou aborto. A ficha C3 precisa dizer qual registro conta. Sem isso, usar a data substitutiva de 294 dias e mostrá-la separada (MET-21). `tb_fat_rel_op_gestante` existe só como nome (relatório operacional, sem colunas documentadas) |
| L3 | **Dimensão de tipo de participação** (presencial × remoto) | A coluna `co_dim_tp_particip_cidadao` existe em FAI e FAO, mas a dimensão não tem página e o nome diverge (`tb_dim_tp_particip_cidadao` × `tb_dim_tp_participacao_atend`). Os valores são conhecidos só pelo LEDI (1–7) | C2 A ("presencial") e C2 B ("presencial ou remota") dependem disso. Inventário: nome real, valores e nulos por período e origem (CDS × PEC) |
| L4 | **Doses vindas de RNDS/RIA** | As Regras de `tb_fat_vacinacao` listam só importação (terceiros, outras instalações, app e-SUS Vacinação), CDS e módulo de vacinação do PEC. Não há fato nem coluna de origem RNDS, e "RIA" não aparece em lugar nenhum do site. Só `tb_dim_estrategia_vacinacao.nu_estrategia_vacinacao` usa código RNDS | A situação vacinal pode sair **subestimada** frente ao Siaps (C2 E, C3 F, C6 D, C7 B). O resultado é `LOCAL_ESTIMATE`; não inferir ausência de vacinação quando falta integração (Tech Spec, C6) |
| L5 | **PA de participante de atividade coletiva** | `tb_fat_atvdd_coletiva_part` tem só peso e altura; o LEDI FAC (`ParticipanteRowItem`) também não tem PA | Atividade coletiva não comprova práticas de PA |
| L6 | **Formato da PA na visita** | `nu_medicao_pressao_arterial` (coluna única, sem referência LEDI), enquanto o LEDI FVDT tem `pressaoSistolica` e `pressaoDiastolica` | Medir o formato no inventário (padrões e contagens, sem valores) antes de aceitar visita como fonte de PA |
| L7 | **Puericultura / pré-natal** | A FAI recebe "atendimento de puericultura" e "de pré-natal" (Regras), mas não há coluna que os distinga | Identificar pelos códigos da ficha (CIAP/CID) ou por outra regra dela; não usar as consolidadas |
| L8 | **Histórico do vínculo** | `tb_acomp_cidadaos_vinculados` não tem histórico e segue a regra local do PEC. A reconstrução pelo FCI versionado é inferência | Competências passadas: vínculo reconstruído a partir de `tb_fat_cad_individual` ≤ corte, marcado como estimativa local; não equivale ao vínculo do Siaps |
| L9 | **`tb_fat_cidadao_pec` e consolidadas** | Sem colunas documentadas e listadas como tabelas de relatório operacional a descontinuar (índice de fatos; v5.4.22→5.4.23) | Não fazer JOIN com `tb_fat_cidadao_pec`; usar `co_fat_cidadao_pec` como chave opaca e `tb_dim_cidadao_pec_grupo` para unificar |
| L10 | **Tipos de dado** | Só a visualização publica tipos | Tipos vêm de `information_schema` (alimentam o fingerprint ENG-43) |
| L11 | **Valores de domínio não publicados** | `tb_dim_condicao_maternal`, `tb_dim_tipo_ficha` e a dimensão de tipo de participação | Listar no inventário: são catálogos, sem PII |
| L12 | **Situação vigente do problema** | A doc não diz se há linha para problema não avaliado (embora `st_avaliado` sugira que sim) nem como obter a situação atual | Regra proposta (inferida): última evolução por `nu_uuid_problema` (`co_sequencial_evolucao` máximo, data ≤ corte). Validar com casos reais agregados |
| L13 | **Semântica de `co_dim_municipio`** | Só "Código de identificação do município" | O C1 comprovou empiricamente numa instalação de um município só. Numa instalação `CENTRALIZADOR` isso precisa de nova prova por fato |

### 4.2 Riscos de interpretação

1. **Chaves × códigos.** `co_seq_dim_*` é local e não é o código LEDI (prova: `tb_dim_tipo_atendimento`
   no C1). `nu_identificador` como código LEDI é inferência para todas as dimensões: validar listando
   cada dimensão pequena.
2. **CBO.** `nu_cbo` deve ser comparado como texto (há `2235C3`). Famílias por prefixo trazem códigos
   indesejados (3224 inclui ASB e protético). Usar a lista exata da ficha.
3. **Cadastro versionado.** `tb_fat_cad_individual` tem uma linha por criação ou atualização, inclusive
   reterritorialização e unificação de prontuários. É preciso escolher a versão vigente na data de corte,
   respeitando `st_ficha_inativa`, `st_recusa_cadastro`, `co_dim_tempo_validade` e
   `co_dim_tipo_saida_cadastro`. Pegar "a última" ignora a competência.
4. **Fonte de evento por prática.** Exames S/A ficam numa tabela chamada "procedimentos"
   (`tb_fat_atd_ind_procedimentos`), e procedimentos realizados no PEC caem nos fatos de procedimentos.
   A antropometria e a PA medidas na escuta inicial de nível médio também estão em FP. Usar só FAI
   subconta; somar fontes sem chave de dia pode contar em dobro (para "no mesmo dia", deduplicar por
   pessoa e data).
5. **Agregados textuais.** `ds_filtro_ciaps`, `ds_filtro_cids` e `ds_filtro_proced_*` têm formato
   (separador, truncamento) não documentado. Preferir as tabelas normalizadas.
6. **Sentinela × NULL.** O C1 viu linha-sentinela (id 1 "Não informado"/"SEM EQUIPE") nas dimensões
   dele. Nas demais FKs, isso não foi visto. `st_registro_valido` pode ser `null` (registro incompleto,
   aguardando reprocessamento) ou `0` (CNES não encontrado): não atribuir evento a unidade ou equipe
   inválida em silêncio.
7. **Consolidadas.** `tb_fat_consolidado_cidadao_*` e `tb_fat_cnslddo_ciddo_fai_cid` são instantâneos
   sobrescritos, sem histórico. Servem só para conferência.
8. **Datas.** O significado de `co_dim_tempo` foi validado só no FAI. Para doses, a data relevante é
   `co_dim_tempo_vacina_aplicada`. Para problemas, valem as datas de início e fim. No FCI, é a data da
   versão do cadastro. Visita e atividade coletiva não têm outra data.
9. **Participantes sem cadastro.** O LEDI FAC aceita participante identificado só por CPF ou CNS; se o
   DW não conseguir ligar, `co_fat_cidadao_pec` pode ficar vazio (inferido). Não "consertar" no extrato
   com CPF ou CNS: seria projetar PII. Contar e reportar como limitação.
10. **Divergências da doc** (2.13). Toda FK usada precisa ser confirmada em `pg_constraint` ou por
    junção com contagem de órfãos.

### 4.3 Diferenças entre versões (`/dw/principais_alteracoes.html`, Alterado em 11/09/2026) relevantes para 5.4.37 e 5.5.28

| Mudança | Item da doc | 5.4.37 (CT 133) | 5.5.28 (produção) | Efeito em C2–C7 |
|---|---|---|---|---|
| v5.5.25→5.5.26 | `st_prat_saude_pnct_manutencao` (`tb_fat_atvdd_coletiva_ext`); CPF na avaliação de elegibilidade | ausente | presente (inferido: 5.5.28 > 5.5.26) | Nenhum (não usar) |
| v5.5.23→5.5.25 | `st_nao_possui_cpf`, `co_dim_just_nao_possui_cpf` em `tb_fat_atvdd_coletiva_part` | ausente | presente (inferido) | Não usar: quebraria o 5.4.37 |
| v5.5.21→5.5.23 | Idem em visita e atendimento domiciliar | ausente | presente (inferido) | Idem |
| v5.4.23→5.5.21 | "Toda a documentação do DW foi amplamente revisada" — **sem itens** | ? | ? | Qualquer coluna que não esteja em outro item (M-CHAT, `st_emulti_*`, `st_conduta_agendamento_emulti` etc.) pode ter nascido aqui: inventário obrigatório nas duas versões |
| v5.4.22→5.4.23 | Tabelas de relatórios operacionais "serão descontinuadas" | aviso vigente | aviso vigente | Risco para `tb_fat_cidadao_pec` e as consolidadas (L9) |
| v5.4.7→5.4.8 | `co_fat_cidadao_pec` em `tb_acomp_cidadaos_vinculados` | presente | presente | Liga a coorte atual aos fatos |
| v5.4.0→5.4.1 | `co_dim_faixa_etaria`, `dt_inicial_atendimento`, `co_dim_tipo_atendimento`, `co_dim_turno`, `co_dim_sexo` em `tb_fat_atd_ind_procedimentos`, `tb_fat_atend_odonto_proced` e `tb_fat_atend_odonto_exames` | presente | presente | Exames S/A e procedimentos odontológicos têm data própria |
| v5.3.25→5.3.26 | `co_cds_domicilio`, `ds_tipo_localizacao_domicilio`, `no_raca_cor` na visualização | presente | presente | Nenhum |
| v5.3.14→5.3.15 | `tb_dim_situacao_problema`; colunas de evolução, situação e datas nos problemas (FAI, FAO, AD); renomeação para `tb_fat_atend_dom_prob_cond` | presente | presente | Situação e datas do problema existem; medir nulos por período (registros anteriores à versão) |
| v5.3.6→5.3.7 | PA sistólica/diastólica, peso, altura e demais medições em FAI, `tb_fat_proced_atend` e FAO | presente | presente | Medir a cobertura temporal das medições |
| v5.2.4→5.2.5 | `tb_dim_vinculacao_equipes` | presente | presente | Não resolve L1 |
| v4.1.4→4.2.0 | `st_registro_anterior` e `co_dim_tempo_vacina_aplicada` (vacinação); `tb_fat_atd_ind_exames` e `tb_fat_atend_odonto_exames` | presente | presente | Transcrição e data de aplicação disponíveis |
| v4.0.0→4.1.2 | `nu_medicao_pressao_arterial` na visita; medicamentos e encaminhamentos (FAI/FAO) | presente | presente | PA da visita (L6) |

"presente" e "ausente" vêm da ordem das versões, não de inspeção. O que vale é o inventário.

## 5. Recorte municipal por fato e chave de pessoa entre fatos

### 5.1 Recorte municipal e tempo por fato (todas as 39 páginas de fato)

Padrão validado do C1 (só para `tb_fat_atendimento_individual`):

```sql
JOIN public.tb_dim_municipio m ON m.co_seq_dim_municipio = f.co_dim_municipio
JOIN public.tb_dim_tempo     t ON t.co_seq_dim_tempo     = f.co_dim_tempo
WHERE m.co_ibge = ? AND t.dt_registro >= ? AND t.dt_registro < ?
```

A tabela abaixo foi gerada a partir das tabelas de colunas das páginas: município = colunas com
`municipio`; tempo = FKs `co_dim_tempo*` e `co_dim_data_*`; datas próprias = colunas `dt_*`; pessoa =
`co_fat_cidadao_pec*`; pai = outras colunas `co_fat_*`.

| Grupo | Fato | Município (FK → `tb_dim_municipio`) | Tempo (FK → `tb_dim_tempo`) | Datas próprias | Pessoa | Pai | Observação |
|---|---|---|---|---|---|---|---|
| atendimento_domiciliar | `tb_fat_atend_dom_prob_cond` | `co_dim_municipio` | `co_dim_data_inicio_problema`, `co_dim_data_fim_problema`, `co_dim_tempo` | — | — | `co_fat_atend_domiciliar` | Cidadão só via `co_fat_atend_domiciliar` → cabeçalho. |
| atendimento_domiciliar | `tb_fat_atend_dom_proced` | `co_dim_municipio` | `co_dim_tempo` | — | — | `co_fat_atend_domiciliar` | Cidadão só via `co_fat_atend_domiciliar` → cabeçalho. |
| atendimento_domiciliar | `tb_fat_atendimento_domiciliar` | `co_dim_municipio` | `co_dim_tempo` | `dt_nascimento` | `co_fat_cidadao_pec` | — |  |
| atendimento_individual | `tb_fat_atd_ind_encaminhamentos` | `co_dim_municipio` | `co_dim_tempo` | — | `co_fat_cidadao_pec` | `co_fat_atd_ind` |  |
| atendimento_individual | `tb_fat_atd_ind_exames` | `co_dim_municipio` | `co_dim_tempo` | `dt_solicitacao`, `dt_realizacao`, `dt_resultado`, `dt_resultado_data` | `co_fat_cidadao_pec` | `co_fat_atd_ind` |  |
| atendimento_individual | `tb_fat_atd_ind_medicamentos` | `co_dim_municipio` | `co_dim_tempo` | `dt_inicio_tratamento` | `co_fat_cidadao_pec` | `co_fat_atd_ind` |  |
| atendimento_individual | `tb_fat_atd_ind_problemas` | `co_dim_municipio` | `co_dim_tempo`, `co_dim_data_inicio_problema`, `co_dim_data_fim_problema` | — | `co_fat_cidadao_pec` | `co_fat_atd_ind` |  |
| atendimento_individual | `tb_fat_atd_ind_procedimentos` | `co_dim_municipio` | `co_dim_tempo` | `dt_inicial_atendimento` | `co_fat_cidadao_pec` | `co_fat_atd_ind` |  |
| atendimento_individual | `tb_fat_atendimento_individual` | `co_dim_municipio` | `co_dim_tempo_dum`, `co_dim_tempo` | `dt_nascimento`, `dt_inicial_atendimento`, `dt_final_atendimento` | `co_fat_cidadao_pec` | — | Validado ao vivo (C1): `JOIN tb_dim_municipio m ON m.co_seq_dim_municipio = f.co_dim_municipio WHERE m.co_ibge = ?`. |
| atendimento_individual | `tb_fat_cnslddo_ciddo_fai_cid` | — | — | — | `co_fat_cidadao_pec` | — | Sem município. Só via `co_fat_cidadao_pec` → algum fato com `co_dim_municipio` (inferido). Relatório operacional — não usar. |
| atendimento_individual | `tb_fat_consolidado_cidadao_fai` | — | `co_dim_tempo_ultima_ficha`, `co_dim_tempo_has`, `co_dim_tempo_diabetes`, `co_dim_tempo_tabagismo`, `co_dim_tempo_obesidade`, `co_dim_tempo_avc`, `co_dim_tempo_infarto`, `co_dim_tempo_doenca_cardiaca`, `co_dim_tempo_problema_rins`, `co_dim_tempo_rastr_rsco_crdo`, `co_dim_tempo_consulta_purperio`, `co_dim_tempo_consulta_prcltra`, `co_dim_tempo_cnslta_1_prcltra` | — | `co_fat_cidadao_pec` | — | Sem município: só via `co_fat_cidadao_pec` → fato de evento com `co_dim_municipio` (inferido). Relatório operacional — não usar. |
| atendimento_odontologico | `tb_fat_atend_odonto_encaminham` | `co_dim_municipio` | `co_dim_tempo` | — | `co_fat_cidadao_pec` | `co_fat_atd_odnt` |  |
| atendimento_odontologico | `tb_fat_atend_odonto_exames` | `co_dim_municipio` | `co_dim_tempo` | `dt_solicitacao`, `dt_realizacao`, `dt_resultado`, `dt_resultado_data`, `dt_inicial_atendimento` | `co_fat_cidadao_pec` | `co_fat_atd_odnt` |  |
| atendimento_odontologico | `tb_fat_atend_odonto_medicament` | `co_dim_municipio` | `co_dim_tempo` | `dt_inicio_tratamento` | `co_fat_cidadao_pec` | `co_fat_atd_odnt` |  |
| atendimento_odontologico | `tb_fat_atend_odonto_problemas` | `co_dim_municipio` | `co_dim_tempo`, `co_dim_data_inicio_problema`, `co_dim_data_fim_problema` | — | `co_fat_cidadao_pec` | `co_fat_atd_odnt` |  |
| atendimento_odontologico | `tb_fat_atend_odonto_proced` | `co_dim_municipio` | `co_dim_tempo` | `dt_inicial_atendimento` | `co_fat_cidadao_pec` | `co_fat_atd_odnt` |  |
| atendimento_odontologico | `tb_fat_atendimento_odonto` | `co_dim_municipio` | `co_dim_tempo` | `dt_nascimento`, `dt_inicial_atendimento`, `dt_final_atendimento` | `co_fat_cidadao_pec` | — |  |
| atendimento_odontologico | `tb_fat_consolidado_cidadao_fao` | — | `co_dim_tempo_ultima_ficha`, `co_dim_tempo_ult_aval_multi` | — | `co_fat_cidadao_pec` | — | Sem município: só via `co_fat_cidadao_pec` → fato de evento com `co_dim_municipio` (inferido). Relatório operacional — não usar. |
| atividade_coletiva | `tb_fat_atividade_coletiva` | `co_dim_municipio` | `co_dim_tempo` | — | — | — | Sem cidadão (cabeçalho). |
| atividade_coletiva | `tb_fat_atvdd_coletiva_ext` | `co_dim_municipio` | `co_dim_tempo` | — | — | `co_fat_atividade_coletiva` | Sem cidadão. |
| atividade_coletiva | `tb_fat_atvdd_coletiva_int` | `co_dim_municipio` | `co_dim_tempo` | — | — | `co_fat_atividade_coletiva` | Sem cidadão (reunião). |
| atividade_coletiva | `tb_fat_atvdd_coletiva_part` | `co_dim_municipio` | `co_dim_tempo` | `dt_participante_nascimento` | `co_fat_cidadao_pec` | `co_fat_atividade_coletiva` |  |
| atividade_coletiva | `tb_fat_atvdd_coletiva_propart` | `co_dim_municipio` | `co_dim_tempo` | — | — | `co_fat_atividade_coletiva` | Sem cidadão (profissionais). |
| avaliacao_elegibilidade | `tb_fat_avaliacao_elegibilidade` | `co_dim_municipio`, `co_dim_municipio_cidadao`, `co_dim_municipio_residencia` | `co_dim_tempo` | `dt_nascimento`, `dt_naturalizacao`, `dt_entrada_brasil` | `co_fat_cidadao_pec`, `co_fat_cidadao_pec_cuidador` | — | `co_dim_municipio` = recorte; os outros dois são do cidadão (fora de C2–C7). |
| cadastro_domiciliar | `tb_fat_cad_dom_familia` | `co_dim_municipio` | `co_dim_tempo`, `co_dim_tempo_validade`, `co_dim_tempo_validade_recusa` | `dt_nascimento`, `dt_inicio_residencia` | `co_fat_cidadao_pec` | `co_fat_cad_domiciliar` |  |
| cadastro_domiciliar | `tb_fat_cad_domiciliar` | `co_dim_municipio_cidadao`, `co_dim_municipio_aldeia`, `co_dim_municipio` | `co_dim_tempo`, `co_dim_tempo_validade`, `co_dim_tempo_validade_recusa` | — | — | — | `co_dim_municipio` = recorte; `co_dim_municipio_cidadao` = localização do domicílio; `co_dim_municipio_aldeia` = aldeia. |
| cadastro_individual | `tb_fat_cad_individual` | `co_dim_municipio_cidadao`, `co_dim_municipio` | `co_dim_tempo_morador_rua`, `co_dim_tempo_socioeducativo`, `co_dim_tempo`, `co_dim_tempo_validade`, `co_dim_tempo_validade_recusa` | `dt_nascimento`, `dt_naturalizacao`, `dt_entrada_brasil`, `dt_obito` | `co_fat_cidadao_pec`, `co_fat_cidadao_pec_responsvl` | — | `co_dim_municipio` = recorte. **Não usar `co_dim_municipio_cidadao` (nascimento).** |
| cuidado_compartilhado | `tb_fat_cuidado_compartilhado` | `co_dim_municipio_evolucao`, `co_dim_municipio_solicitante`, `co_dim_municipio_executante` | `co_dim_tempo` | `dt_evolucao`, `dt_evolucao_anterior`, `dt_nascimento_cidadao`, `dt_criacao_cuidado` | `co_fat_cidadao_pec` | — | Sem `co_dim_municipio`; tem três municípios (do profissional da evolução, do solicitante e do executante). Escolher pela regra; fora de C2–C7. |
| ficha_complementar | `tb_fat_complementar` | `co_dim_municipio` | `co_dim_tempo` | `dt_teste_olhinho`, `dt_exame_fundo_olho`, `dt_teste_orelhinha`, `dt_transfontanela`, `dt_tomografia`, `dt_ressonancia` | `co_fat_cidadao_pec`, `co_fat_cidadao_pec_responsvl` | — |  |
| ivcf | `tb_fat_ivcf` | `co_dim_municipio` | `co_dim_tempo` | `dt_resultado` | `co_fat_cidadao_pec` | — |  |
| marcadores_consumo_alimentar | `tb_fat_marca_consumo_alimnt` | `co_dim_municipio` | `co_dim_tempo` | `dt_nascimento` | `co_fat_cidadao_pec` | — |  |
| oci | `tb_fat_solicitacao_oci` | `co_dim_municipio` | `co_dim_tempo` | — | `co_fat_cidadao_pec` | — |  |
| procedimentos | `tb_fat_consolidado_cidadao_fp` | — | `co_dim_tempo_ultima_ficha`, `co_dim_tempo_ult_aval_multi` | — | `co_fat_cidadao_pec` | — | Sem município: só via `co_fat_cidadao_pec` → fato de evento com `co_dim_municipio` (inferido). Relatório operacional — não usar. |
| procedimentos | `tb_fat_proced_atend` | `co_dim_municipio` | `co_dim_tempo` | `dt_nascimento`, `dt_inicial_atendimento`, `dt_final_atendimento` | `co_fat_cidadao_pec` | `co_fat_procedimento` |  |
| procedimentos | `tb_fat_proced_atend_proced` | `co_dim_municipio` | `co_dim_tempo` | `dt_nascimento` | `co_fat_cidadao_pec` | `co_fat_procedimento` |  |
| procedimentos | `tb_fat_procedimento` | `co_dim_municipio` | `co_dim_tempo` | — | — | — | Sem cidadão (cabeçalho individualizado + consolidado). |
| vacinacao | `tb_fat_vacinacao` | `co_dim_municipio` | `co_dim_tempo` | `dt_inicial_atendimento`, `dt_final_atendimento`, `dt_nascimento` | `co_fat_cidadao_pec` | — |  |
| vacinacao | `tb_fat_vacinacao_vacina` | `co_dim_municipio` | `co_dim_tempo_vacina_aplicada`, `co_dim_tempo` | — | — | `co_fat_vacinacao` | Tem `co_dim_municipio` próprio (dimensões comuns da vacinação); cidadão só via cabeçalho. |
| visita_domiciliar | `tb_fat_visita_domiciliar` | `co_dim_municipio` | `co_dim_tempo` | `dt_nascimento` | `co_fat_cidadao_pec` | — |  |


**Onde não há `co_dim_municipio`:**

- **Consolidadas por cidadão** (`tb_fat_consolidado_cidadao_fai`/`fao`/`fp`, `tb_fat_cnslddo_ciddo_fai_cid`):
  só por `co_fat_cidadao_pec` → um fato de evento com `co_dim_municipio` (inferido). Não usar.
- **`tb_fat_cuidado_compartilhado`**: `co_dim_municipio_evolucao`, `co_dim_municipio_solicitante` e
  `co_dim_municipio_executante`. Fora de C2–C7.
- **`tb_acomp_cidadaos_vinculados`**: não tem IBGE (só nomes de município de endereço, que são PII).
  Caminhos inferidos, em ordem de preferência:
  - (a) `co_unico_ultima_ficha` = `tb_fat_cad_individual.nu_uuid_ficha` → `co_dim_municipio` → `co_ibge`.
    Não cobre quem tem só o cadastro simplificado (`st_possui_fci = 0`).
  - (b) `nu_ine_vinc_equipe` / `nu_cnes_vinc_equipe` restritos aos INE/CNES que aparecem em fatos do
    município autorizado (ou em `tb_dim_agrupador_filtro.co_dim_municipio`).
  - (c) `co_fat_cidadao_pec` presente em algum fato do município.

  Qualquer um dos três exige prova de isolamento como a da ADR 0023.
- **Dimensões**: `tb_dim_unidade_saude` e `tb_dim_equipe` não têm município. A única estrutura
  documentada que associa unidade e equipe a município é `tb_dim_agrupador_filtro` (por profissional).
- **Armadilhas**: `tb_fat_cad_individual.co_dim_municipio_cidadao` (nascimento) e
  `tb_fat_cad_domiciliar.co_dim_municipio_cidadao` (localização do domicílio) **não** são o recorte.

**Tabelas-filhas** (problemas, exames S/A, resultados, lista de procedimentos, doses, participantes) têm
`co_dim_municipio` e `co_dim_tempo` próprios, por serem "dimensões comuns" do grupo. O mais seguro é
filtrar na filha e conferir com o pai: contar filhas cujo município difere do município do pai, que
deve dar zero.

### 5.2 Chave de pessoa entre fatos

1. **Chave nos fatos:** `co_fat_cidadao_pec` ("Campo `co_seq_fat_cidadao_pec` da `tb_fat_cidadao_pec`").
   Está em FAI e todas as suas filhas, FAO e filhas, `tb_fat_proced_atend`,
   `tb_fat_proced_atend_proced`, visita, `tb_fat_atvdd_coletiva_part`, `tb_fat_vacinacao`,
   `tb_fat_cad_individual` (mais `co_fat_cidadao_pec_responsvl`), `tb_fat_cad_dom_familia` (só o
   responsável familiar), `tb_fat_atendimento_domiciliar`, IVCF, marcadores, OCI, cuidado compartilhado,
   elegibilidade, ficha complementar e nas consolidadas.
2. **Fatos sem a coluna**, que herdam do pai:
   - `tb_fat_vacinacao_vacina.co_fat_vacinacao` → `tb_fat_vacinacao.co_seq_fat_vacinacao`;
   - `tb_fat_atend_dom_prob_cond` e `tb_fat_atend_dom_proced` → `co_fat_atend_domiciliar`;
   - `tb_fat_atividade_coletiva`, `_ext`, `_int` e `_propart` são do nível da atividade (sem pessoa);
   - `tb_fat_procedimento` é cabeçalho ou consolidado (sem pessoa);
   - `tb_fat_cad_domiciliar` é o domicílio.
3. **Unificação:** `tb_dim_cidadao_pec_grupo` tem `co_fat_cidadao_pec`, `co_cidadao` e
   `co_cidadao_master` ("cidadão unificado"), com uma linha por tipo de identificação (`tp_identificacao`
   0 UUID de origem, 1 CNS, 2 CPF). Proposta (inferida): a chave de pessoa do extrato é
   `co_cidadao_master` (ou `co_cidadao` quando o master for nulo), obtida por
   `SELECT DISTINCT co_fat_cidadao_pec, co_cidadao, co_cidadao_master`. Antes, validar por contagens:
   - (i) cada `co_fat_cidadao_pec` mapeia para um único master;
   - (ii) quantos masters reúnem mais de um `co_fat_cidadao_pec`, que são os duplicados que o próprio
     PEC unificou;
   - (iii) quantos `co_fat_cidadao_pec` dos fatos não aparecem no grupo.

   Se (i) falhar, ficar com `co_fat_cidadao_pec` e reportar a limitação.
4. **Coorte atual:** `tb_acomp_cidadaos_vinculados.co_fat_cidadao_pec` (desde 5.4.8) e `co_cidadao`. A
   doc admite duplicidade "nos casos de cadastro duplicado".
5. **Versões do cadastro:** dentro de `tb_fat_cad_individual`, as versões de um mesmo cadastro
   compartilham `nu_uuid_ficha_origem` (inferido de "ficha de origem do cadastro"), e a versão atual é a
   de `nu_uuid_ficha` = `tb_acomp_cidadaos_vinculados.co_unico_ultima_ficha`.
6. **Nunca** ligar por CPF, CNS, nome ou data de nascimento no extrato. Isso exigiria projetar PII e
   repetir, fora do PEC, uma deduplicação que o PEC já faz. Evento sem `co_fat_cidadao_pec` é contado e
   reportado, não "recuperado".
7. **Referência de origem** (`SourceRef`): usar o `co_seq_fat_*` de cada fato como `source_record_id`.
   Para doses, `co_seq_fat_vacinacao_vacina`.

## 6. Minimização — colunas mínimas por capacidade candidata

"Projetar" = sai do PEC para o extrato. "Só filtro/junção" = usada no SQL, mas não projetada. Em todas
as capacidades, o recorte (`tb_dim_municipio.co_ibge`) e a janela (`tb_dim_tempo.dt_registro`) são
filtros. Códigos de dimensão saem como código LEDI e chave substituta, nunca como descrição livre.

| Capacidade candidata | Tabelas | Projetar | Só filtro/junção | Nunca |
|---|---|---|---|---|
| **cidadão** (coorte) | `tb_fat_cad_individual` (versão vigente), `tb_dim_cidadao_pec_grupo`, `tb_dim_sexo`, `tb_dim_identidade_genero`, `tb_dim_tipo_saida_cadastro` | chave de pessoa (`co_fat_cidadao_pec` e/ou `co_cidadao_master`); `dt_nascimento`; sexo (`nu_identificador`); identidade de gênero (`nu_identificador`); `dt_obito` ou data da saída e motivo (`nu_identificador` 135/136) | `co_dim_municipio`, `co_dim_tempo` (≤ corte), `nu_uuid_ficha`/`nu_uuid_ficha_origem` (versão), `st_ficha_inativa`, `st_recusa_cadastro`, `co_dim_tempo_validade` | `no_*`, `nu_cpf_*`, `nu_cns*`, `nu_nis`, `nu_dnv_cidadao`, `nu_obito_do`, `nu_celular`, `no_email`, textos livres, `co_identificacao`, `co_dim_municipio_cidadao`, raça/cor, escolaridade, benefícios |
| **cadastro** (vínculo e condições autorreferidas) | `tb_fat_cad_individual`; `tb_acomp_cidadaos_vinculados` (só estado atual) | `co_fat_cidadao_pec`; INE e CNES do vínculo (`tb_dim_equipe.nu_ine`/`tb_dim_unidade_saude.nu_cnes` da versão vigente, ou `nu_ine_vinc_equipe`/`nu_cnes_vinc_equipe`); `st_diabete`, `st_hipertensao_arterial`, `st_gestante` (só para C4, C5 e C3, respectivamente); data da versão | `st_possui_fci`, `co_unico_ultima_ficha`, `co_cidadao` | Na visualização, tudo o que é nome, documento, telefone, endereço, microárea, `co_cds_domicilio` e `no_raca_cor` |
| **equipe** | `tb_dim_equipe`, `tb_dim_unidade_saude` | `nu_ine`, `nu_cnes`, `st_registro_valido`; tipo de equipe — **lacuna L1** | — | `tb_dim_profissional` (nome e CNS do profissional) |
| **atendimento individual** | FAI | `co_seq_fat_atd_ind`, `co_fat_cidadao_pec`, `dt_registro`, `nu_cbo` (`_1`), `nu_ine` (`_1`), `nu_cnes` (`_1`), tipo de atendimento (id e código), local (código), tipo de participação (código), `nu_peso`, `nu_altura`, `nu_medicao_pressao_sistolica`, `nu_medicao_pressao_diastolica`, data da DUM, `nu_idade_gestacional_semanas` — cada uma só para a prática que a pede | `co_dim_municipio`, `co_dim_tempo`, `ds_filtro_*` (se usado, só em `WHERE`) | `nu_cpf_cidadao`, `nu_cns`, `nu_prontuario`, `dt_nascimento` (vem do cadastro), condutas, encaminhamentos, M-CHAT |
| **odontológico** | `tb_fat_atendimento_odonto`, `tb_fat_atend_odonto_proced` | `co_seq_fat_atd_odnt`, `co_fat_cidadao_pec`, `dt_registro`, `nu_cbo` (`_1`), `nu_ine` (`_1`), `st_gestante`; `co_proced` ∈ lista da ficha (se exigido) | `co_dim_tipo_atendimento`, `co_dim_tipo_consulta` (se a ficha restringir) | CPF, CNS, prontuário, vigilância, fornecimentos, encaminhamentos |
| **visita** | `tb_fat_visita_domiciliar` | `co_seq_fat_visita_domiciliar`, `co_fat_cidadao_pec`, `dt_registro`, `nu_cbo`, `nu_ine`, desfecho (código); `nu_peso`, `nu_altura` e PA só se a ficha aceitar visita como fonte; motivos só os que a ficha usar | `co_dim_tipo_imovel` (se precisar excluir imóveis sem cidadão) | `nu_latitude`, `nu_longitude`, `nu_micro_area`, `co_uuid_origem_fcd`, CPF, CNS, prontuário |
| **vacinação** | `tb_fat_vacinacao`, `tb_fat_vacinacao_vacina` | `co_seq_fat_vacinacao_vacina`, `co_fat_cidadao_pec` (do cabeçalho), data de aplicação (`co_dim_tempo_vacina_aplicada`), imunobiológico (`nu_identificador`), dose (`nu_identificador`), estratégia (`nu_identificador`), `st_registro_anterior`; condição maternal (só C3 F) | imunobiológico ∈ lista da ficha (em `WHERE`) | `no_lote`, fabricante, `ds_anvisa_*`, `st_pesquisa_clinica`, CPF, CNS, prontuário, `dt_nascimento` |
| **exames S/A** | `tb_fat_atd_ind_procedimentos` (+ `tb_fat_atd_ind_exames` se a ficha exigir as datas do resultado) | `co_seq_fat_atend_ind_proced`, `co_fat_atd_ind`, `co_fat_cidadao_pec`, `dt_registro`, `co_proced` solicitado/avaliado (só ∈ lista da ficha), `nu_cbo` e `nu_ine` (`_1`); opcionalmente `dt_solicitacao`, `dt_realizacao`, `dt_resultado` | lista SIGTAP/AB em `WHERE` | `nu_resultado_valor` (as fichas pedem solicitado/avaliado, não o valor), CPF, CNS |
| **procedimentos realizados** | `tb_fat_proced_atend`, `tb_fat_proced_atend_proced` | `co_seq_fat_proced_atend_proced`, `co_fat_procedimento`, `nu_atendimento`, `co_fat_cidadao_pec`, `dt_registro`, `co_proced` (∈ lista), `nu_cbo`, `nu_ine`; do atendimento: `co_seq_fat_proced_atend`, `nu_peso`, `nu_altura`, PA, `st_escuta_inicial` | lista SIGTAP/AB em `WHERE` | CPF, CNS, prontuário, `dt_nascimento`, `ds_filtro_procedimento` |
| **condições** | `tb_fat_atd_ind_problemas` (+ autorreferidas do FCI) | `co_seq_fat_atend_ind_problemas`, `co_fat_atd_ind`, `co_fat_cidadao_pec`, `dt_registro`, `nu_ciap`/`nu_cid` (só ∈ lista da ficha), situação (código), `st_avaliado`, datas de início e fim, `nu_uuid_problema` + `co_sequencial_evolucao` (pseudônimos internos para a situação vigente) | — | CPF, CNS; códigos fora da lista da ficha |
| **atividade coletiva** | `tb_fat_atividade_coletiva`, `tb_fat_atvdd_coletiva_part`, `tb_fat_atvdd_coletiva_ext` | atividade: `co_seq_fat_atividade_coletiva`, `dt_registro`, tipo (código), `nu_cbo`, `nu_ine`; participante: `co_seq_fat_atvdd_cltv_part`, `co_fat_atividade_coletiva`, `co_fat_cidadao_pec`, `nu_participante_peso`, `nu_participante_altura`; `st_prat_saude_antropometria` | tipo ∈ {4, 5, 6} (ou a lista da ficha) | `nu_participante_cns`, `nu_cpf_participante`, `dt_participante_nascimento`, `ds_outra_localidade`, temas e público-alvo |

## 7. Validação ao vivo sugerida (próxima fase; somente leitura, sem PII)

1. **Catálogo** nas duas versões (5.4.37 e 5.5.28): existência, colunas, tipos, posição e nulidade de
   cada tabela da parte 2 (`information_schema.columns`), e as FKs reais (`pg_constraint`) dos pontos
   de 2.13. Os objetos novos entram na matriz com fingerprint ENG-43, como os 7 do C1.
2. **Domínios** (catálogos, sem pessoa): `co_seq_*`, `nu_identificador` e descrição de
   `tb_dim_tipo_atendimento`, `tb_dim_local_atendimento`, a dimensão de tipo de participação,
   `tb_dim_situacao_problema`, `tb_dim_desfecho_visita`, `tb_dim_tipo_atividade`, `tb_dim_sexo`,
   `tb_dim_identidade_genero`, `tb_dim_tipo_saida_cadastro`, `tb_dim_dose_imunobiologico`,
   `tb_dim_estrategia_vacinacao`, `tb_dim_condicao_maternal`, `tb_dim_tipo_ficha` e `tb_dim_imunobiologico`
   (só `nu_identificador`, `no_imunobiologico`, `sg_imunobiologico`). Confirmar, por exemplo, que
   `nu_identificador = 42` ↔ `sg_imunobiologico = 'PENTA'`.
3. **Cardinalidade** (só contagens):
   - `co_fat_cidadao_pec` → master (5.2 itens i–iii);
   - nulos de `co_fat_cidadao_pec` por fato;
   - unicidade de `(co_fat_procedimento, nu_atendimento)` entre `tb_fat_proced_atend` e a lista;
   - doses com `st_registro_anterior = 1` e `co_dim_tempo` ≠ `co_dim_tempo_vacina_aplicada`;
   - versões por `nu_uuid_ficha_origem` no FCI;
   - filhas com município ≠ pai.
4. **Formatos** (contagem de padrões, sem projetar valores): `nu_medicao_pressao_arterial` da visita e
   `ds_filtro_ciaps`/`ds_filtro_cids`.
5. **Isolamento** por fato novo, no molde da ADR 0023 (contagem por `co_ibge` na competência).

## Apêndice A — Proveniência página a página (acesso em 2026-10-02)

Caminhos relativos a `https://integracao.esusaps.bridge.ufsc.tech`. As últimas 7 linhas são páginas do
LEDI usadas só como apoio (domínios e regras).

| Página | Título | Alterado em |
|---|---|---|
| `/dw/` | Data Warehouse Relatórios e-SUS APS PEC | 11/09/2026 |
| `/dw/principais_alteracoes.html` | Principais alterações entre versões | 11/09/2026 |
| `/dw/fatos/index.html` | Fatos | 09/02/2026 |
| `/dw/fatos/atendimento_domiciliar/index.html` | Atendimento domiciliar | 13/07/2026 |
| `/dw/fatos/atendimento_individual/index.html` | Atendimento individual | 08/11/2024 |
| `/dw/fatos/atendimento_odontologico/index.html` | Atendimento odontológico | 13/07/2026 |
| `/dw/fatos/atividade_coletiva/index.html` | Atividade coletiva | 13/07/2026 |
| `/dw/fatos/avaliacao_elegibilidade/index.html` | Avaliação de elegibilidade | 13/07/2026 |
| `/dw/fatos/cadastro_domiciliar/index.html` | Cadastro domiciliar | 13/07/2026 |
| `/dw/fatos/cadastro_individual/index.html` | Cadastro individual | 13/07/2026 |
| `/dw/fatos/cuidado_compartilhado/index.html` | Cuidado compartilhado | 13/07/2026 |
| `/dw/fatos/ficha_complementar/index.html` | Síndrome neurológica por Zika / Microcefalia | 13/07/2026 |
| `/dw/fatos/ivcf/index.html` | IVCF-20 | 13/07/2026 |
| `/dw/fatos/marcadores_consumo_alimentar/index.html` | Marcadores de consumo alimentar | 13/07/2026 |
| `/dw/fatos/oci/index.html` | Solicitação de Oferta de Cuidado Integrado | 16/09/2025 |
| `/dw/fatos/procedimentos/index.html` | Procedimentos | 13/07/2026 |
| `/dw/fatos/vacinacao/index.html` | Vacinação | 13/07/2026 |
| `/dw/fatos/visita_domiciliar/index.html` | Visita domiciliar | 13/07/2026 |
| `/dw/fatos/atendimento_domiciliar/tb_fat_atend_dom_prob_cond.html` | Tabela fato dos problemas e condições avaliadas no atendimento domiciliar | 13/07/2026 |
| `/dw/fatos/atendimento_domiciliar/tb_fat_atend_dom_proced.html` | Tabela fato dos procedimentos do atendimento domiciliar | 13/07/2026 |
| `/dw/fatos/atendimento_domiciliar/tb_fat_atendimento_domiciliar.html` | Tabela fato do atendimento domiciliar | 11/09/2026 |
| `/dw/fatos/atendimento_individual/tb_fat_atd_ind_encaminhamentos.html` | Tabela fato dos encaminhamentos do atendimento individual | 13/07/2026 |
| `/dw/fatos/atendimento_individual/tb_fat_atd_ind_exames.html` | Tabela fato dos exames do atendimento individual | 13/07/2026 |
| `/dw/fatos/atendimento_individual/tb_fat_atd_ind_medicamentos.html` | Tabela fato dos medicamentos do atendimento individual | 13/07/2026 |
| `/dw/fatos/atendimento_individual/tb_fat_atd_ind_problemas.html` | Tabela fato dos problemas e condições do atendimento individual | 13/07/2026 |
| `/dw/fatos/atendimento_individual/tb_fat_atd_ind_procedimentos.html` | Tabela fato dos procedimentos do atendimento individual | 13/07/2026 |
| `/dw/fatos/atendimento_individual/tb_fat_atendimento_individual.html` | Tabela fato do atendimento individual | 11/09/2026 |
| `/dw/fatos/atendimento_individual/tb_fat_cnslddo_ciddo_fai_cid.html` | Tabela fato com informações consolidadas de CID10 de um cidadão geradas a partir de um atendimento individual | 13/07/2026 |
| `/dw/fatos/atendimento_individual/tb_fat_consolidado_cidadao_fai.html` | Tabela fato com informações consolidadas sobre um cidadão geradas a partir de um atendimento individual | 08/11/2024 |
| `/dw/fatos/atendimento_odontologico/tb_fat_atend_odonto_encaminham.html` | Tabela fato dos encaminhamentos do atendimento odontológico | 13/07/2026 |
| `/dw/fatos/atendimento_odontologico/tb_fat_atend_odonto_exames.html` | Tabela fato dos exames do atendimento odontológico | 13/07/2026 |
| `/dw/fatos/atendimento_odontologico/tb_fat_atend_odonto_medicament.html` | Tabela fato dos medicamentos do atendimento odontológico | 13/07/2026 |
| `/dw/fatos/atendimento_odontologico/tb_fat_atend_odonto_problemas.html` | Tabela fato dos problemas e condições do atendimento odontológico | 13/07/2026 |
| `/dw/fatos/atendimento_odontologico/tb_fat_atend_odonto_proced.html` | Tabela fato dos procedimentos do atendimento odontológico | 13/07/2026 |
| `/dw/fatos/atendimento_odontologico/tb_fat_atendimento_odonto.html` | Tabela fato do atendimento odontológico | 11/09/2026 |
| `/dw/fatos/atendimento_odontologico/tb_fat_consolidado_cidadao_fao.html` | Tabela fato com informações consolidadas sobre um cidadão geradas a partir de um atendimento odontológico | 13/07/2026 |
| `/dw/fatos/atividade_coletiva/tb_fat_atividade_coletiva.html` | Tabela fato da atividade coletiva | 13/07/2026 |
| `/dw/fatos/atividade_coletiva/tb_fat_atvdd_coletiva_ext.html` | Tabela fato das ações de saúde da atividade coletiva | 10/09/2026 |
| `/dw/fatos/atividade_coletiva/tb_fat_atvdd_coletiva_int.html` | Tabela fato dos temas para reunião da atividade coletiva | 10/09/2026 |
| `/dw/fatos/atividade_coletiva/tb_fat_atvdd_coletiva_part.html` | Tabela fato dos participantes da atividade coletiva | 11/09/2026 |
| `/dw/fatos/atividade_coletiva/tb_fat_atvdd_coletiva_propart.html` | Tabela fato dos profissionais participantes da atividade coletiva | 13/07/2026 |
| `/dw/fatos/avaliacao_elegibilidade/tb_fat_avaliacao_elegibilidade.html` | Tabela fato da avaliação de elegibilidade | 11/09/2026 |
| `/dw/fatos/cadastro_domiciliar/tb_fat_cad_dom_familia.html` | Tabela fato das famílias do cadastro domiciliar | 13/07/2026 |
| `/dw/fatos/cadastro_domiciliar/tb_fat_cad_domiciliar.html` | Tabela fato do cadastro domiciliar | 13/07/2026 |
| `/dw/fatos/cadastro_individual/tb_fat_cad_individual.html` | Tabela fato do cadastro individual | 13/07/2026 |
| `/dw/fatos/cuidado_compartilhado/tb_fat_cuidado_compartilhado.html` | Tabela fato do cuidado compartilhado | 13/07/2026 |
| `/dw/fatos/ficha_complementar/tb_fat_complementar.html` | Tabela fato da ficha complementar de Síndrome neurológica por Zika / Microcefalia | 11/09/2026 |
| `/dw/fatos/ivcf/ivcf.html` | Tabela fato dos registro de IVCF-20 | 13/07/2026 |
| `/dw/fatos/marcadores_consumo_alimentar/tb_fat_marca_consumo_alimnt.html` | Tabela fato de marcadores de consumo alimentar | 11/09/2026 |
| `/dw/fatos/oci/tb_fat_solicitacao_oci.html` | Tabela fato das solicitações de Oferta de Cuidado Integrado | 13/07/2026 |
| `/dw/fatos/procedimentos/tb_fat_consolidado_cidadao_fp.html` | Tabela fato com informações consolidadas sobre um cidadão geradas a partir de um procedimento | 13/07/2026 |
| `/dw/fatos/procedimentos/tb_fat_proced_atend.html` | Tabela fato dos atendimentos de procedimento (individualizados) | 11/09/2026 |
| `/dw/fatos/procedimentos/tb_fat_proced_atend_proced.html` | Tabela fato da lista dos procedimentos de um atendimento de procedimentos | 10/11/2024 |
| `/dw/fatos/procedimentos/tb_fat_procedimento.html` | Tabela fato dos procedimentos (individualizados e consolidados) | 13/07/2026 |
| `/dw/fatos/vacinacao/tb_fat_vacinacao.html` | Tabela fato da vacinação | 13/07/2026 |
| `/dw/fatos/vacinacao/tb_fat_vacinacao_vacina.html` | Tabela fato das vacinas aplicadas | 13/07/2026 |
| `/dw/fatos/visita_domiciliar/tb_fat_visita_domiciliar.html` | Tabela fato de visita domiciliar | 11/09/2026 |
| `/dw/dimensoes/index.html` | Dimensões | 13/07/2026 |
| `/dw/dimensoes/dim_agrupador_filtro.html` | Tabela de dimensão de agrupadores de filtro | 10/11/2024 |
| `/dw/dimensoes/dim_aldeia.html` | Tabela de dimensão de aldeia indígena | 13/07/2026 |
| `/dw/dimensoes/dim_aleitamento.html` | Tabela de dimensão de aleitamento materno | 10/11/2024 |
| `/dw/dimensoes/dim_catmat.html` | Tabela de dimensão medicamentos (CATMAT) | 10/11/2024 |
| `/dw/dimensoes/dim_cbo.html` | Tabela de dimensão de CBO | 10/11/2024 |
| `/dw/dimensoes/dim_ciap.html` | Tabela de dimensão de CIAP-2 | 10/11/2024 |
| `/dw/dimensoes/dim_cid.html` | Tabela de dimensão de CID-10 | 10/11/2024 |
| `/dw/dimensoes/dim_cidadao_pec_grupo.html` | Tabela de dimensão de referências de cadastro de um mesmo cidadão | 13/07/2026 |
| `/dw/dimensoes/dim_classificacao_risc_enc.html` | Tabela de dimensão de classificação de risco de encaminhamentos | 10/11/2024 |
| `/dw/dimensoes/dim_condicao_maternal.html` | Tabela de dimensão de condição maternal | 13/07/2026 |
| `/dw/dimensoes/dim_conduta_ad.html` | Tabela de dimensão de condutas do Atendimento Domiciliar | 10/11/2024 |
| `/dw/dimensoes/dim_conduta_cuidado.html` | Tabela de dimensão de condutas do Cuidado Compartilhado | 10/11/2024 |
| `/dw/dimensoes/dim_cuidador.html` | Tabela de dimensão de cuidador | 10/11/2024 |
| `/dw/dimensoes/dim_desfecho_visita.html` | Tabela de dimensão de desfecho de visitas | 10/11/2024 |
| `/dw/dimensoes/dim_dose_dose_imunobiologico.html` | Tabela de dimensão de dose de imunobiológico | 10/11/2024 |
| `/dw/dimensoes/dim_dose_frequencia.html` | Tabela de dimensão de tipos de periodicidade da dose | 10/11/2024 |
| `/dw/dimensoes/dim_dose_frequencia_medida.html` | Tabela de dimensão de periodicidade da dose | 10/11/2024 |
| `/dw/dimensoes/dim_duracao_tratamento_med.html` | Tabela de dimensão de unidade de medida da duração do tratamento | 10/11/2024 |
| `/dw/dimensoes/dim_equipe.html` | Tabela de dimensão de equipes | 10/11/2024 |
| `/dw/dimensoes/dim_especialidade.html` | Tabela de dimensão de especialidades - Atendimento Individual | 10/11/2024 |
| `/dw/dimensoes/dim_estado_civil.html` | Tabela de dimensão de estado civil | 13/07/2026 |
| `/dw/dimensoes/dim_estrategia_vacinacao.html` | Tabela de dimensão de estratégia de vacinação | 10/11/2024 |
| `/dw/dimensoes/dim_etnia.html` | Tabela de dimensão de etnia | 10/11/2024 |
| `/dw/dimensoes/dim_faixa_etaria.html` | Tabela de dimensão de faixa etária | 06/12/2024 |
| `/dw/dimensoes/dim_forma_farmaceutica.html` | Tabela de dimensão de forma farmacêutica | 06/12/2024 |
| `/dw/dimensoes/dim_frequencia_alimentacao.html` | Tabela de dimensão de frequência de alimentação | 06/12/2024 |
| `/dw/dimensoes/dim_grau_vulnerabilidade_ivcf.html` | Tabela de dimensão de graus de vulnerabilidade do IVCF-20 | 18/04/2025 |
| `/dw/dimensoes/dim_grupo_atendimento.html` | Tabela de dimensão de grupo de atendimento | 06/12/2024 |
| `/dw/dimensoes/dim_grupo_cbo.html` | Tabela de dimensão de grupos de CBO | 10/11/2024 |
| `/dw/dimensoes/dim_identidade_genero.html` | Tabela de dimensão de identidade de gênero | 06/12/2024 |
| `/dw/dimensoes/dim_imunobiologico.html` | Tabela de dimensão de imunobiológico | 06/12/2024 |
| `/dw/dimensoes/dim_inep.html` | Tabela de dimensão de estabelecimentos do INEP | 06/12/2024 |
| `/dw/dimensoes/dim_just_nao_possui_cpf.html` | Tabela de dimensão de justificativa de ausência de CPF | 13/07/2026 |
| `/dw/dimensoes/dim_local_apl_vacina.html` | Tabela de dimensão de local de aplicação da vacina | 13/07/2026 |
| `/dw/dimensoes/dim_local_atendimento.html` | Tabela de dimensão de local de atendimento | 06/12/2024 |
| `/dw/dimensoes/dim_modalidade_ad.html` | Tabela de dimensão de modalidade AD | 06/12/2024 |
| `/dw/dimensoes/dim_municipio.html` | Tabela de dimensão de município | 06/12/2024 |
| `/dw/dimensoes/dim_nacionalidade.html` | Tabela de dimensão de nacionalidade | 06/12/2024 |
| `/dw/dimensoes/dim_pais.html` | Tabela de dimensão de país | 31/01/2025 |
| `/dw/dimensoes/dim_pic.html` | Tabela de dimensão de práticas integrativas complementares | 31/01/2025 |
| `/dw/dimensoes/dim_povo_comunidad_trad.html` | Tabela de dimensão de povo ou comunidade tradicional | 31/01/2025 |
| `/dw/dimensoes/dim_prioridade_cuidado.html` | Tabela de dimensão de classificação de prioridade do cuidado compartilhado | 31/01/2025 |
| `/dw/dimensoes/dim_procedencia_origem.html` | Tabela de dimensão de procedências da atenção domiciliar | 31/01/2025 |
| `/dw/dimensoes/dim_procedimento.html` | Tabela de dimensão de procedimentos | 31/01/2025 |
| `/dw/dimensoes/dim_profissional.html` | Tabela de dimensão de profissionais | 31/01/2025 |
| `/dw/dimensoes/dim_raca_cor.html` | Tabela de dimensão de raça e cor | 31/01/2025 |
| `/dw/dimensoes/dim_racionalidade_saude.html` | Tabela de dimensão de racionalidade em saúde | 31/01/2025 |
| `/dw/dimensoes/dim_sexo.html` | Tabela de dimensão de sexo | 31/01/2025 |
| `/dw/dimensoes/dim_situacao_problema.html` | Tabela de dimensão de situação do problema/condição | 13/07/2026 |
| `/dw/dimensoes/dim_situacao_trabalho.html` | Tabela de dimensão de situação no mercado de trabalho | 13/07/2026 |
| `/dw/dimensoes/dim_tempo.html` | Tabela de dimensão de datas | 10/11/2024 |
| `/dw/dimensoes/dim_tempo_morador_rua.html` | Tabela de dimensão de tempo em situação de rua | 13/07/2026 |
| `/dw/dimensoes/dim_tempo_socioeducativo.html` | Tabela de dimensão de tempo de cumprimento de medida socioeducativa | 13/07/2026 |
| `/dw/dimensoes/dim_tipo_abastecimento_agua.html` | Tabela de dimensão de tipo de abastecimento de água | 13/07/2026 |
| `/dw/dimensoes/dim_tipo_acesso_domicilio.html` | Tabela de dimensão de tipo de acesso ao domicílio | 13/07/2026 |
| `/dw/dimensoes/dim_tipo_atendimento.html` | Tabela de dimensão de tipo de atendimento | 13/07/2026 |
| `/dw/dimensoes/dim_tipo_atividade.html` | Tabela de dimensão de tipo de atividade coletiva | 13/07/2026 |
| `/dw/dimensoes/dim_tipo_condicao_peso.html` | Tabela de dimensão de condição de peso | 13/07/2026 |
| `/dw/dimensoes/dim_tipo_consulta_odonto.html` | Tabela de dimensão de tipo de consulta odontológica | 13/07/2026 |
| `/dw/dimensoes/dim_tipo_destino_lixo.html` | Tabela de dimensão de destino do lixo | 13/07/2026 |
| `/dw/dimensoes/dim_tipo_domicilio.html` | Tabela de dimensão de tipo de domicílio | 13/07/2026 |
| `/dw/dimensoes/dim_tipo_elegibilidade.html` | Tabela de dimensão de tipo de elegibilidade para atenção domiciliar | 13/07/2026 |
| `/dw/dimensoes/dim_tipo_endereco.html` | Tabela de dimensão de tipo de endereço | 13/07/2026 |
| `/dw/dimensoes/dim_tipo_escoamento_sanitar.html` | Tabela de dimensão de tipo de escoamento sanitário | 13/07/2026 |
| `/dw/dimensoes/dim_tipo_escolaridade.html` | Tabela de dimensão de tipo de escolaridade | 13/07/2026 |
| `/dw/dimensoes/dim_tipo_ficha.html` | Tabela de dimensão de tipo de ficha | 13/07/2026 |
| `/dw/dimensoes/dim_tipo_glicemia.html` | Tabela de dimensão de tipo de glicemia capilar | 13/07/2026 |
| `/dw/dimensoes/dim_tipo_imovel.html` | Tabela de dimensão de tipo de imóvel | 13/07/2026 |
| `/dw/dimensoes/dim_tipo_localizacao.html` | Tabela de dimensão de tipo de localização da moradia | 13/07/2026 |
| `/dw/dimensoes/dim_tipo_logradouro.html` | Tabela de dimensão de tipo de logradouro | 13/07/2026 |
| `/dw/dimensoes/dim_tipo_material_parede.html` | Tabela de dimensão de material predominante das paredes | 13/07/2026 |
| `/dw/dimensoes/dim_tipo_orientacao_sexual.html` | Tabela de dimensão de orientação sexual | 13/07/2026 |
| `/dw/dimensoes/dim_tipo_origem.html` | Tabela de dimensão de tipo de origem do dado | 13/07/2026 |
| `/dw/dimensoes/dim_tipo_origem_dado_transp.html` | Tabela de dimensão de tipo de origem do dado transportado | 13/07/2026 |
| `/dw/dimensoes/dim_tipo_origem_energ_elet.html` | Tabela de dimensão de tipo de origem de energia elétrica | 13/07/2026 |
| `/dw/dimensoes/dim_tipo_parentesco.html` | Tabela de dimensão de tipo de parentesco | 13/07/2026 |
| `/dw/dimensoes/dim_tipo_posse_terra.html` | Tabela de dimensão de condição de posse e uso da terra | 13/07/2026 |
| `/dw/dimensoes/dim_tipo_renda_familiar.html` | Tabela de dimensão de renda familiar | 13/07/2026 |
| `/dw/dimensoes/dim_tipo_saida_cadastro.html` | Tabela de dimensão de motivo de saída do cadastro | 13/07/2026 |
| `/dw/dimensoes/dim_tipo_sanguineo.html` | Tabela de dimensão de tipo sanguíneo | 13/07/2026 |
| `/dw/dimensoes/dim_tipo_situacao_moradia.html` | Tabela de dimensão de situação de moradia | 13/07/2026 |
| `/dw/dimensoes/dim_tipo_socioeducativo.html` | Tabela de dimensão de tipo de medida socioeducativa | 13/07/2026 |
| `/dw/dimensoes/dim_tipo_tratamento_agua.html` | Tabela de dimensão de tratamento da água para consumo | 13/07/2026 |
| `/dw/dimensoes/dim_turno.html` | Tabela de dimensão de turno | 13/07/2026 |
| `/dw/dimensoes/dim_uf.html` | Tabela de dimensão de UF | 13/07/2026 |
| `/dw/dimensoes/dim_unidade_saude.html` | Tabela de dimensão de unidade de saúde | 13/07/2026 |
| `/dw/dimensoes/dim_via_adm_vacina.html` | Tabela de dimensão de via de administração da vacina | 13/07/2026 |
| `/dw/dimensoes/dim_via_administracao.html` | Tabela de dimensão de via de administração de medicamento | 13/07/2026 |
| `/dw/dimensoes/dim_vinculacao_equipes.html` | Tabela de dimensão de vinculação entre equipes | 13/07/2026 |
| `/dw/visualizacoes/index.html` | Visualizações | 15/08/2024 |
| `/dw/visualizacoes/acompanhamento_cidadaos_vinculados.html` | Tabela de acompanhamento de cidadãos vinculados | 19/06/2026 |
| `/ledi/documentacao/referencias/dicionario.html` | Dicionário de dados | 10/09/2026 |
| `/ledi/documentacao/referencias/cbo_disponiveis.html` | CBOs | 19/06/2026 |
| `/ledi/documentacao/estrutura_arquivos/dicionario-fai.html` | Modelo de Informação de Atendimento Individual | 11/09/2026 |
| `/ledi/documentacao/estrutura_arquivos/dicionario-fvd.html` | Modelo de Informação de Visita Domiciliar e Territorial | 11/09/2026 |
| `/ledi/documentacao/estrutura_arquivos/dicionario-fac.html` | Modelo de Informação de Atividade Coletiva | 11/09/2026 |
| `/ledi/documentacao/estrutura_arquivos/dicionario-fv.html` | Modelo de Informação de Vacinação | 10/09/2026 |
| `/ledi/documentacao/regras/validar_regras_vacinacao.html` | Regras de vacinação | 13/07/2026 |

## Apêndice B — Objetos documentados mas não detalhados

### B.1 Fatos não detalhados (chaves de recorte, tempo e pessoa na tabela 5.1)

| Fato | Página | Alterado em | Por que não foi detalhado |
|---|---|---|---|
| `tb_fat_atend_dom_prob_cond` | `/dw/fatos/atendimento_domiciliar/tb_fat_atend_dom_prob_cond.html` | 13/07/2026 | CIAP/CID e situação na Atenção Domiciliar (FKs para `tb_dim_cid10`/`tb_dim_ciap`); cidadão só via cabeçalho. Só entra se a ficha contar atendimento de AD. |
| `tb_fat_atend_dom_proced` | `/dw/fatos/atendimento_domiciliar/tb_fat_atend_dom_proced.html` | 13/07/2026 | Procedimentos na Atenção Domiciliar; cidadão só via cabeçalho. Só entra se a ficha contar atendimento de AD. |
| `tb_fat_atendimento_domiciliar` | `/dw/fatos/atendimento_domiciliar/tb_fat_atendimento_domiciliar.html` | 11/09/2026 | Atenção Domiciliar (SAD/e-SUS AD). Tem cidadão, local e tipo de atendimento, mas não tem peso, altura ou PA. Só entra se a ficha contar atendimento domiciliar de AD como consulta. |
| `tb_fat_atd_ind_encaminhamentos` | `/dw/fatos/atendimento_individual/tb_fat_atd_ind_encaminhamentos.html` | 13/07/2026 | Encaminhamentos do FAI — fora de C2–C7. |
| `tb_fat_atd_ind_medicamentos` | `/dw/fatos/atendimento_individual/tb_fat_atd_ind_medicamentos.html` | 13/07/2026 | Prescrições do FAI — fora de C2–C7. |
| `tb_fat_atend_odonto_encaminham` | `/dw/fatos/atendimento_odontologico/tb_fat_atend_odonto_encaminham.html` | 13/07/2026 | Fora de C2–C7. |
| `tb_fat_atend_odonto_exames` | `/dw/fatos/atendimento_odontologico/tb_fat_atend_odonto_exames.html` | 13/07/2026 | Exames S/A e resultados no FAO (`co_dim_procedimento_avaliado`/`_solicitado`) — só se a ficha aceitar exame avaliado em atendimento odontológico. |
| `tb_fat_atend_odonto_medicament` | `/dw/fatos/atendimento_odontologico/tb_fat_atend_odonto_medicament.html` | 13/07/2026 | Fora de C2–C7. |
| `tb_fat_atend_odonto_problemas` | `/dw/fatos/atendimento_odontologico/tb_fat_atend_odonto_problemas.html` | 13/07/2026 | Problemas e condições no FAO (mesma estrutura de `tb_fat_atd_ind_problemas`) — só se a ficha aceitar condição avaliada no FAO. |
| `tb_fat_consolidado_cidadao_fao` | `/dw/fatos/atendimento_odontologico/tb_fat_consolidado_cidadao_fao.html` | 13/07/2026 | Relatório operacional consolidado — não usar. |
| `tb_fat_atvdd_coletiva_int` | `/dw/fatos/atividade_coletiva/tb_fat_atvdd_coletiva_int.html` | 10/09/2026 | Reuniões (temas) — fora de C2–C7. |
| `tb_fat_atvdd_coletiva_propart` | `/dw/fatos/atividade_coletiva/tb_fat_atvdd_coletiva_propart.html` | 13/07/2026 | Profissionais participantes — só atribuição secundária. |
| `tb_fat_avaliacao_elegibilidade` | `/dw/fatos/avaliacao_elegibilidade/tb_fat_avaliacao_elegibilidade.html` | 11/09/2026 | Elegibilidade para AD — fora de C2–C7. |
| `tb_fat_cad_domiciliar` | `/dw/fatos/cadastro_domiciliar/tb_fat_cad_domiciliar.html` | 13/07/2026 | Domicílio — quase tudo PII (endereço, telefones, geolocalização). Fora de C2–C7. |
| `tb_fat_cuidado_compartilhado` | `/dw/fatos/cuidado_compartilhado/tb_fat_cuidado_compartilhado.html` | 13/07/2026 | Fora de C2–C7 (três municípios; ver 5.1). |
| `tb_fat_complementar` | `/dw/fatos/ficha_complementar/tb_fat_complementar.html` | 11/09/2026 | Zika/microcefalia — fora de C2–C7. |
| `tb_fat_ivcf` | `/dw/fatos/ivcf/ivcf.html` | 13/07/2026 | IVCF-20 — não é prática de C6 nas fichas resumidas na Tech Spec. |
| `tb_fat_marca_consumo_alimnt` | `/dw/fatos/marcadores_consumo_alimentar/tb_fat_marca_consumo_alimnt.html` | 11/09/2026 | Marcadores de consumo alimentar — fora de C2–C7. |
| `tb_fat_solicitacao_oci` | `/dw/fatos/oci/tb_fat_solicitacao_oci.html` | 13/07/2026 | Oferta de Cuidado Integrado — fora de C2–C7. |

### B.2 Dimensões com página não detalhadas (58 de 89)

Fora de C2–C7 ou só descritivas (cadastro domiciliar, AD, medicamentos, encaminhamentos, eMulti etc.). Entre parênteses: página em `/dw/dimensoes/` e "Alterado em".

`tb_dim_aldeia` (dim_aldeia.html, 13/07/2026); `tb_dim_aleitamento` (dim_aleitamento.html, 10/11/2024); `tb_dim_catmat` (dim_catmat.html, 10/11/2024); `tb_dim_classificacao_risc_enc` (dim_classificacao_risc_enc.html, 10/11/2024); `tb_dim_conduta_ad` (dim_conduta_ad.html, 10/11/2024); `tb_dim_conduta_cuidado` (dim_conduta_cuidado.html, 10/11/2024); `tb_dim_cuidador` (dim_cuidador.html, 10/11/2024); `tb_dim_dose_frequencia` (dim_dose_frequencia.html, 10/11/2024); `tb_dim_dose_frequencia_medida` (dim_dose_frequencia_medida.html, 10/11/2024); `tb_dim_duracao_tratamento_med` (dim_duracao_tratamento_med.html, 10/11/2024); `tb_dim_especialidade` (dim_especialidade.html, 10/11/2024); `tb_dim_estado_civil` (dim_estado_civil.html, 13/07/2026); `tb_dim_etnia` (dim_etnia.html, 10/11/2024); `tb_dim_forma_farmaceutica` (dim_forma_farmaceutica.html, 06/12/2024); `tb_dim_frequencia_alimentacao` (dim_frequencia_alimentacao.html, 06/12/2024); `tb_dim_grau_vulnerabilidade` (dim_grau_vulnerabilidade_ivcf.html, 18/04/2025); `tb_dim_grupo_atendimento` (dim_grupo_atendimento.html, 06/12/2024); `tb_dim_inep` (dim_inep.html, 06/12/2024); `tb_dim_just_nao_possui_cpf` (dim_just_nao_possui_cpf.html, 13/07/2026); `tb_dim_local_apl_vacina` (dim_local_apl_vacina.html, 13/07/2026); `tb_dim_modalidade_ad` (dim_modalidade_ad.html, 06/12/2024); `tb_dim_nacionalidade` (dim_nacionalidade.html, 06/12/2024); `tb_dim_pais` (dim_pais.html, 31/01/2025); `tb_dim_pic` (dim_pic.html, 31/01/2025); `tb_dim_povo_comunidad_trad` (dim_povo_comunidad_trad.html, 31/01/2025); `tb_dim_prioridade_cuidado` (dim_prioridade_cuidado.html, 31/01/2025); `tb_dim_procedencia_origem` (dim_procedencia_origem.html, 31/01/2025); `tb_dim_raca_cor` (dim_raca_cor.html, 31/01/2025); `tb_dim_racionalidade_saude` (dim_racionalidade_saude.html, 31/01/2025); `tb_dim_situacao_trabalho` (dim_situacao_trabalho.html, 13/07/2026); `tb_dim_tempo_morador_rua` (dim_tempo_morador_rua.html, 13/07/2026); `tb_dim_tempo_socioeducativo` (dim_tempo_socioeducativo.html, 13/07/2026); `tb_dim_tipo_abastecimento_agua` (dim_tipo_abastecimento_agua.html, 13/07/2026); `tb_dim_tipo_acesso_domicilio` (dim_tipo_acesso_domicilio.html, 13/07/2026); `tb_dim_tipo_condicao_peso` (dim_tipo_condicao_peso.html, 13/07/2026); `tb_dim_tipo_destino_lixo` (dim_tipo_destino_lixo.html, 13/07/2026); `tb_dim_tipo_domicilio` (dim_tipo_domicilio.html, 13/07/2026); `tb_dim_tipo_elegibilidade` (dim_tipo_elegibilidade.html, 13/07/2026); `tb_dim_tipo_endereco` (dim_tipo_endereco.html, 13/07/2026); `tb_dim_tipo_escoamento_sanitar` (dim_tipo_escoamento_sanitar.html, 13/07/2026); `tb_dim_tipo_escolaridade` (dim_tipo_escolaridade.html, 13/07/2026); `tb_dim_tipo_glicemia` (dim_tipo_glicemia.html, 13/07/2026); `tb_dim_tipo_imovel` (dim_tipo_imovel.html, 13/07/2026); `tb_dim_tipo_localizacao` (dim_tipo_localizacao.html, 13/07/2026); `tb_dim_tipo_logradouro` (dim_tipo_logradouro.html, 13/07/2026); `tb_dim_tipo_material_parede` (dim_tipo_material_parede.html, 13/07/2026); `tb_dim_tipo_orientacao_sexual` (dim_tipo_orientacao_sexual.html, 13/07/2026); `tb_dim_tipo_origem_energ_elet` (dim_tipo_origem_energ_elet.html, 13/07/2026); `tb_dim_tipo_parentesco` (dim_tipo_parentesco.html, 13/07/2026); `tb_dim_tipo_posse_terra` (dim_tipo_posse_terra.html, 13/07/2026); `tb_dim_tipo_renda_familiar` (dim_tipo_renda_familiar.html, 13/07/2026); `tb_dim_tipo_sanguineo` (dim_tipo_sanguineo.html, 13/07/2026); `tb_dim_tipo_situacao_moradia` (dim_tipo_situacao_moradia.html, 13/07/2026); `tb_dim_tipo_socioeducativo` (dim_tipo_socioeducativo.html, 13/07/2026); `tb_dim_tipo_tratamento_agua` (dim_tipo_tratamento_agua.html, 13/07/2026); `tb_dim_turno` (dim_turno.html, 13/07/2026); `tb_dim_via_adm_vacina` (dim_via_adm_vacina.html, 13/07/2026); `tb_dim_via_administracao` (dim_via_administracao.html, 13/07/2026).
