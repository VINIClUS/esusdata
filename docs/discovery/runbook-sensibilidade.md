# Runbook: sensibilidade das ambiguidades (RULE_AMBIGUITY de C2 e C3)

Ferramenta **local**, na árvore de testes (`apps/agent/src/test/java/esusdata/indicator/sensitivity/`),
sem chave de produção. Dado um extrato canônico real, que fica só no disco local, mostra para cada
código AMB quantos sujeitos afeta e qual seria o numerador, o denominador e a pontuação em cada
leitura candidata, por INE e para o município. Serve de base numérica para as decisões do Portão
B/D: veja o plano em `c1-c4-c5-e-rippling-riddle.md`, fatia S3.

## O que ela calcula

A ferramenta avalia cada pack como a produção faz (`evaluate`, só para ler contagens, evidência e
limitações; o valor publicado nunca é lido). A linha de base tem de reproduzir a produção, ou a
execução para com erro: C2 confere denominador, pontos e crianças ambíguas; C3 confere
denominador e pontos pelos componentes.

| Pack | Código | Leituras |
|---|---|---|
| C2 | AMB-C2-03, AMB-C2-02 | Incluir e marcar ambígua (produção), incluir sem marcar, excluir da coorte. A AMB-C2-02 (aniversário `NEXT_DAY` ou `CLAMP_TO_MONTH_END`) vem de `C2Cohort.classify`, o mesmo código da produção. |
| C2 | demais AMB-C2-xx e LACUNA-L3 | **Limites**, não leituras: pontuação com as práticas que dependem do código todas não cumpridas e todas cumpridas. Crianças diferentes viram em sentidos diferentes, então o limite não é o resultado de uma leitura. |
| C3 | AMB-C3-02 | Uma execução por convenção, com `C3Pack.withTrimesterConvention`. Dias contados da DUM (dia 0), limites inclusivos: 13s6d/28s0d = (97, 196); 13s6d/27s0d = (97, 189); 12s6d/28s0d = (90, 196); 14s0d/28s0d = (98, 196). Mostra também o numerador e o denominador de G e H. |
| C3 | demais AMB-C3-xx | Frequência; os que aparecem como ambiguidade de inclusão do episódio na coorte, com as três leituras de inclusão; os de prática, com limites. |
| C7 | — | Sem leituras desde `c7-prevencao-cancer@0.2.0`: as AMB-C7-05, -06 e -08 foram decididas em `docs/indicadores/decisoes/c7-prevencao-cancer.md`. Os números das leituras de 2026-08 ficam em `docs/discovery/2026-10-06-sensibilidade-2026-08.md`. Hoje entra como as demais: só o que dispara. |
| C1, C4, C5, C6 | todos | Só o que dispara: frequência nas evidências e códigos citados nas limitações dinâmicas do resultado. Os caminhos RULE_AMBIGUITY de C4–C6 são só de eAP e hoje inalcançáveis. |

Uma linha com "ainda ambíguos" maior que zero tem valor que é só o limite inferior, com as práticas
certas.

## Entrada

Cada pack lê o seu próprio extrato (partes, janelas e binds diferem). A ferramenta só roda um pack num
extrato que o plano de leitura dele aceita (`ReadPlan.requireCovers`, o mesmo de `runFromExtract`).
Pack sem extrato aceito aparece como `SEM_EXTRATO`.

### Modo extrato

`ESUSDATA_SENSITIVITY_EXTRACT` aponta para um diretório com os pares `<id>.manifest.json` e
`<id>.jsonl.gz`, como os que o execplane publica. Opcional: `ESUSDATA_SENSITIVITY_COMPETENCIA=AAAA-MM`
(sem ela, a competência é o mês anterior ao fim exclusivo de cada manifesto).

```bash
ESUSDATA_SENSITIVITY_EXTRACT=$HOME/extratos-sensibilidade \
ESUSDATA_SENSITIVITY_COMPETENCIA=2026-09 \
mvn -B -f apps/agent/pom.xml test -Djacoco.skip=true -Dsurefire.reuseForks=false \
  -Dtest=SensitivityRunTest -Dsurefire.failIfNoSpecifiedTests=false
```

### Modo ao vivo (lê o PEC de produção, só leitura)

Adquire um extrato por pack pelo execplane, com o plano e o comando que `RunExecutor.runLive` usa,
em `<saída>/extratos`. Exige o opt-in explícito `ESUSDATA_SENSITIVITY_LIVE=true`, a competência, o
binário do execplane e o arquivo de segredo dos outros testes ao vivo (o mesmo de
[`runbook-validacao-capacidades.md`](runbook-validacao-capacidades.md): `PEC_DB_HOST/PORT/NAME/USER/PASSWORD`,
`PEC_SOURCE_ID`, `PEC_VERSION`, `PEC_MUNICIPALITY_IBGE`; túnel no ar). Sem qualquer um deles o teste
é ignorado. Opcional: `ESUSDATA_SENSITIVITY_PACKS=c2-desenvolvimento-infantil,c3-gestacao-puerperio`.

```bash
ESUSDATA_SENSITIVITY_LIVE=true \
ESUSDATA_SENSITIVITY_COMPETENCIA=2026-09 \
mvn -B -f apps/agent/pom.xml test -Djacoco.skip=true -Dsurefire.reuseForks=false \
  -Dtest=SensitivityRunTest -Dsurefire.failIfNoSpecifiedTests=false \
  -Dobservatorio.execution-plane.binary=$PWD/apps/execplane/target/release/observatorio-execplane \
  -Dobservatorio.execution-plane.live-pec.env-file=$HOME/.config/observatorio-aps/pec.env
```

- São até sete aquisições, uma por pack, cada uma com o orçamento do pack. Rode fora do pico, como
  nos outros casos ao vivo.
- O id de cada extrato é `sensibilidade-<pack>-<competência>`; para repetir a aquisição, apague
  `<saída>/extratos` (o extrato já adquirido serve de entrada do modo extrato).

## Saída

`ESUSDATA_SENSITIVITY_OUT` (padrão `apps/agent/target/sensibilidade/`, ignorado pelo git) recebe:

- `sensibilidade-AAAA-MM.md`: resumo para os docs, só agregados por INE e para o município, com toda
  contagem abaixo de 10 como `<10`. Numerador e valor somem quando o denominador é pequeno e, nos
  subgrupos, também quando o numerador é, para o valor não devolver a contagem. Não há coluna de
  diferença entre leituras, que revelaria a contagem mascarada.
- `sensibilidade-AAAA-MM.csv`: contagens cruas, **só local**. Não vai para o git nem para os docs.

Nenhuma saída, nem o log, traz identificador de pessoa, chave de origem ou data de registro. Antes de
copiar o `.md` para um doc, leia-o uma vez.

## Testes

Os testes de unidade (dados sintéticos: agregação, mascaramento, cada leitura mudando as contagens
esperadas, o extrato de ponta a ponta) rodam no `mvn verify` normal. `SensitivityRunTest` é ignorado
sem as variáveis acima.
