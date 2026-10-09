# Runbook: Portão D retrospectivo (captura, diagnóstico, compatibilidade, replay e gate)

Regra e racional: [ADR 0034](../adr/0034-reconciliacao-siaps-retrospectiva.md) e a spec
`docs/superpowers/specs/2026-10-08-reconciliacao-siaps-retrospectiva-design.md`. Este texto só diz como rodar.
Tudo é ferramenta de desenvolvimento na árvore de testes
(`apps/agent/src/test/java/esusdata/indicator/reconciliation`), opt-in por propriedade, e o CI nunca
toca PEC, rede nem arquivos reais.

## As cinco etapas

Cada etapa é um teste vivo com o seu `-Dobservatorio.gate.d.mode`. Uma propriedade que não é da etapa é
recusada (não ignorada), e é por isso que nenhuma etapa escreve o registro de portões.

| Etapa | `mode` | Teste | Lê | Escreve |
|---|---|---|---|---|
| Captura | `capture` | `PortaoDReferenceCaptureLiveTest` | exportações oficiais (CSV) | manifestos e artefatos da referência |
| Diagnóstico | `diagnostic` | `PortaoDDiagnosticLiveTest` | manifestos, PEC | matriz mascarada (local) |
| Compatibilidade | `compatibility` | `PortaoDCompatibilityLiveTest` | manifestos, perfis, PEC | um dossiê por referência |
| Replay | `replay` | `PortaoDEvidenceReplayLiveTest` | manifestos, perfis, cache; sem PEC | nada (compara bytes) |
| Gate | `gate` | `PortaoDGateLiveTest` | política, dossiês e cache do repositório; sem PEC | resumo local |

O gate ainda **não** escreve `release-gates.json`: o veredito do conjunto e a atualização de D chegam com
`ReferenceSetVerdict` (PR D).

## Onde saem os arquivos

| O quê | Onde | No Git? |
|---|---|---|
| Exportações oficiais brutas (CSV) | `~/.local/share/observatorio-aps/siaps/raw/` (`0600`) | nunca |
| Artefatos da captura, cache de extratos, matriz do diagnóstico, detalhe por equipe, registro local | `~/.local/share/observatorio-aps/portao-d/` | nunca |
| Manifestos | `docs/indicadores/portoes/references/` | sim |
| Dossiês (`<reference-id>-<pack>.json` e `.md`) | `docs/indicadores/portoes/compatibilidade/` | sim, só contagens mascaradas (`<10`) |

O diretório de artefatos nunca pode estar sob `docs/` nem dentro de uma árvore Git: os runners recusam. O
detalhe por equipe (INEs) e o registro local (`compatibilidade/registro.txt`, com as sondas que falharam) ficam
em `portao-d/compatibilidade/`. Passe `dossier-dir` como caminho **absoluto**: o Maven roda em `apps/agent`, e
um caminho relativo cairia em `apps/agent/docs/...`.

## A fonte local: PEC por um alias do SSH

A evidência vem de um PEC de teste ou de produção, sempre em modo estritamente somente leitura. O alias é um
**parâmetro**: nada de IP, usuário, chave ou nome de host neste repositório, em comando commitado ou em log. Hoje a
evidência vem do PEC de produção (ADR 0034, seção 2); o `siha` continua como alternativa quando estiver
acessível, e basta trocar o alias.

- **Arquivo de segredos:** `~/.config/observatorio-aps/<alias>.env`, modo `0600` (`chmod 600`), com
  `PEC_DB_HOST=127.0.0.1`, `PEC_DB_PORT=15434`, `PEC_DB_NAME`, `PEC_DB_USER`, `PEC_DB_PASSWORD`,
  `PEC_SOURCE_ID`, `PEC_VERSION` e `PEC_MUNICIPALITY_IBGE`. Credencial de leitura (ADR 0002), nunca `postgres`.
- **Túnel:** socket de controle **próprio** (`~/.ssh/<alias>-portao-d.sock`), porta local **15434**. Não use a
  15433 (é a do túnel do aplicativo) nem o socket de outro túnel.
- **Fechamento:** em `trap`, pelo socket (`ssh -S <socket> -O exit <alias>`), inclusive em falha. Nunca `pkill`
  nem `kill` por nome: derrubaria o túnel de outra pessoa.
- Nenhum agente abre o túnel. O Java só recebe `PEC_DB_HOST` e `PEC_DB_PORT`.
- **Horário:** rode fora do horário de pico e da janela de processamento do DW: o servidor está em uso clínico.
  A primeira rodada lê quatro meses por pack (e o suplemento do C1); a segunda é servida pelo cache.

### Pré-voo somente leitura

Antes de qualquer cálculo, o diagnóstico e a compatibilidade abrem uma sessão com
`default_transaction_read_only=on`, exigem `SHOW transaction_read_only = on` numa transação `READ ONLY`,
registram a versão do PostgreSQL, confirmam o município do arquivo de segredos contra o dos manifestos, fazem
`ROLLBACK` e fecham. Qualquer divergência aborta. Depois, a cobertura local dos períodos passa pelo plano de
execução, que também lê em `READ ONLY`. Na estação, confira só o túnel, que aceita TCP mesmo com o PostgreSQL
parado: `pg_isready -h 127.0.0.1 -p 15434` deve dizer `accepting connections`.

## Passo a passo

Da raiz do repositório, com o binário do plano de execução compilado
(`cargo build --release --manifest-path apps/execplane/Cargo.toml`).

```bash
ALIAS=<alias do ~/.ssh/config>            # parâmetro: o alias, nunca IP, usuário ou chave
REMOTE_PORT=5433                          # porta do PostgreSQL no loopback do host remoto
LOCAL_PORT=15434
SOCK="$HOME/.ssh/${ALIAS}-portao-d.sock"
ARTEFATOS="$HOME/.local/share/observatorio-aps/portao-d"
RAW="$HOME/.local/share/observatorio-aps/siaps/raw"
IBGE=<7 dígitos do município>
BIN="$PWD/apps/execplane/target/release/observatorio-execplane"
ENV_FILE="$HOME/.config/observatorio-aps/${ALIAS}.env"
MVN=(mvn -B -f apps/agent/pom.xml test -Djacoco.skip=true -Dsurefire.reuseForks=false
     -Dsurefire.failIfNoSpecifiedTests=false)
umask 077 && mkdir -p "$ARTEFATOS"
stat -c '%a' "$ENV_FILE"                  # esperado: 600
```

### 1. Captura (sem PEC)

```bash
"${MVN[@]}" -Dtest=PortaoDReferenceCaptureLiveTest \
  -Dobservatorio.gate.d.mode=capture \
  -Dobservatorio.gate.d.official-export-dir="$RAW" \
  -Dobservatorio.gate.d.official-export-ibge="$IBGE" \
  -Dobservatorio.gate.d.artifact-dir="$ARTEFATOS" \
  -Dobservatorio.gate.d.manifest-output="$PWD/docs/indicadores/portoes/references"
```

Rodar de novo sobre os mesmos arquivos não muda nada; um arquivo que mudou vira a próxima revisão
(`REFERENCE_DRIFT`), nunca uma sobrescrita.

### 2. Túnel, diagnóstico e compatibilidade (com PEC)

```bash
cleanup() { ssh -S "$SOCK" -O exit "$ALIAS" 2>/dev/null || true; }
trap cleanup EXIT INT TERM
ssh -f -N -M -S "$SOCK" -o ExitOnForwardFailure=yes \
  -L "${LOCAL_PORT}:127.0.0.1:${REMOTE_PORT}" "$ALIAS"
pg_isready -h 127.0.0.1 -p "$LOCAL_PORT"             # esperado: accepting connections

COMUM=(-Dobservatorio.gate.d.manifests-dir="$PWD/docs/indicadores/portoes/references"
       -Dobservatorio.gate.d.artifact-dir="$ARTEFATOS"
       -Dobservatorio.execution-plane.live-pec.env-file="$ENV_FILE"
       -Dobservatorio.execution-plane.binary="$BIN")

# diagnóstico: matriz mascarada de todos os períodos capturados
"${MVN[@]}" -Dtest=PortaoDDiagnosticLiveTest -Dobservatorio.gate.d.mode=diagnostic "${COMUM[@]}"

# compatibilidade: um dossiê por referência de C1 a C7 e da Nota Final
"${MVN[@]}" -Dtest=PortaoDCompatibilityLiveTest -Dobservatorio.gate.d.mode=compatibility "${COMUM[@]}" \
  -Dobservatorio.gate.d.dossier-dir="$PWD/docs/indicadores/portoes/compatibilidade"

cleanup; trap - EXIT INT TERM                         # túnel fechado antes do replay
```

- `-Dobservatorio.gate.d.periods=2026Q1` (lista separada por vírgula) limita **o que roda**, nunca o que decide.
  Sem ela, todo período capturado roda.
- `-Dobservatorio.gate.d.profiles=<arquivo>` troca os perfis metodológicos; o padrão é
  `contracts/indicators/siaps-methodology-profiles.json`.
- O diagnóstico e a compatibilidade compartilham o cache `"$ARTEFATOS"/extratos`: o que um adquiriu o outro
  não lê de novo.
- Um período a que faltam meses locais não gera dossiê (um dossiê precisa da impressão digital de uma fonte lida):
  vira linha do `registro.txt` com os meses que faltam. Um pack sem perfil também não gera dossiê: é lacuna,
  nunca veredito. Em ambos os casos o teste lista o que faltou e a campanha não termina até isso ser tratado.
- Uma sonda que falha não derruba a rodada: vira linha do `registro.txt` e ausência de resultado, que o
  avaliador lê como não observado.

### 3. Replay (sem PEC, túnel fechado)

Regenera cada dossiê do diretório a partir dos artefatos do cache e compara os bytes (JSON e Markdown). Não
adquire: se faltar uma partição, falha. Fica na máquina local; o CI não roda (as entradas nunca entram no Git).

```bash
"${MVN[@]}" -Dtest=PortaoDEvidenceReplayLiveTest \
  -Dobservatorio.gate.d.mode=replay \
  -Dobservatorio.gate.d.manifests-dir="$PWD/docs/indicadores/portoes/references" \
  -Dobservatorio.gate.d.artifact-dir="$ARTEFATOS" \
  -Dobservatorio.gate.d.dossier-dir="$PWD/docs/indicadores/portoes/compatibilidade"
```

Registre na nota da campanha que todos os dossiês foram reproduzidos.

### 4. Gate (só depois da evidência no `main`)

O gate exige árvore limpa (nada modificado, preparado ou não rastreado) e recusa uma declaração `GATE`
diferente da do `HEAD`: a política com os dossiês e a promoção a `GATE` já está commitada (e, pelo protocolo da
spec §16.2, mergeada no `main` antes). Não há período para dar; as referências são as declarações `GATE` de
`contracts/indicators/siaps-reference-policy.json`.

```bash
"${MVN[@]}" -Dtest=PortaoDGateLiveTest \
  -Dobservatorio.gate.d.mode=gate \
  -Dobservatorio.gate.d.repo-root="$PWD" \
  -Dobservatorio.gate.d.artifact-dir="$ARTEFATOS"
```

Para cada pack ele calcula o `gate_set_sha256` e confere que toda referência `GATE` tem o manifesto fixado, o
dossiê fixado com veredito `EXACT` ou `EQUIVALENT_FOR_REFERENCE`, e `local_source_fingerprint` igual ao dos
extratos do cache. Escreve `"$ARTEFATOS"/gate/resumo.md`. Um conjunto vazio é dito vazio, não é falha. Não
toca `release-gates.json`.

## Se algo sair errado

- **`the cache holds N sources`** no replay ou no gate: o cache tem partições de mais de uma fonte para a mesma
  referência. Nada é adivinhado; apague o cache da fonte errada e rode a compatibilidade de novo.
- **`nothing may be acquired`**: faltou uma partição. O replay e o gate nunca leem o PEC.
- **Divergência de bytes no replay**: o dossiê commitado não é o que os artefatos geram. Não edite o dossiê
  à mão; investigue o que mudou (perfil, sonda, cache) e gere de novo.
- **Túnel preso**: feche pelo socket (`ssh -S "$SOCK" -O exit "$ALIAS"`), nunca por `pkill`.
