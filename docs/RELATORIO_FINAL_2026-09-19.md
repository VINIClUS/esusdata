# Observatório APS — Relatório técnico final (2026-09-19)

Autor: agente autônomo (Claude), sessão única, ambiente autorizado conforme missão recebida.
Escopo entregue: descoberta real do PEC + vertical slice do MVP (Fases 0–6 da Tech Spec v0.4)
provado ponta a ponta contra a instalação real, com testes automatizados. **Fases 7–9 (job-runner
completo, REST/SSE, UI, empacotamento) não foram implementadas** — ver §9.

---

## 1. Como o PEC foi acessado

```
workstation (192.168.101.10)
  └─ NetBird (peer route 192.168.1.0/24 — sessão expira e exige reautenticação)
      └─ ssh -i ~/.ssh/proxmox-pve01 root@192.168.1.149          (pve-01, cluster Proxmox)
          └─ pct exec 133 -- ...                                  (usado para toda a descoberta)
          └─ ssh -i ~/.ssh/observatorio-pec root@192.168.1.209    (acesso direto ao CT 133)
              └─ ssh -L 15433:127.0.0.1:5433 ...                  (túnel usado pela aplicação)
```

**Mudança feita no CT 133 (mínima, documentada, reversível):** uma chave pública ed25519 dedicada
(`~/.ssh/observatorio-pec`, comentário `observatorio-aps-pec-tunnel`) foi anexada a
`/root/.ssh/authorized_keys`. Nada mais no CT 133 foi alterado — nenhum serviço, configuração do
PEC, `pg_hba.conf` ou banco.

**Reversão:** `pct exec 133 -- sed -i '/observatorio-aps-pec-tunnel/d' /root/.ssh/authorized_keys`

**Para reabrir a sessão de desenvolvimento após expirar:**
1. `netbird up` (ou reautenticar via browser se solicitado) — sessão NetBird expira periodicamente.
2. `ssh -N -i ~/.ssh/observatorio-pec -L 15433:127.0.0.1:5433 root@192.168.1.209 &`
3. Confirmar: `python3 -c "import socket; socket.socket().connect(('127.0.0.1',15433))"`

O credential de leitura (`esus_leitura`) já existia no PEC — não foi criado nenhum usuário/role.
Detalhes completos em [`docs/discovery/2026-09-19-pec-ct133.md`](discovery/2026-09-19-pec-ct133.md)
e [`docs/adr/0002-credencial-esus-leitura-existente.md`](adr/0002-credencial-esus-leitura-existente.md)/
[`0003-tunel-ssh-para-pec.md`](adr/0003-tunel-ssh-para-pec.md).

---

## 2. Versões reais identificadas

| | Valor | Evidência |
|---|---|---|
| e-SUS PEC | **5.4.37** | CT description, `/opt/e-SUS`, docs de instalação do `esus-pec-bootstrap` |
| PostgreSQL | **9.6.13** | `select version()` — confirmado 3× (psql, pgJDBC spike, pgJDBC produção) |
| SQLite (runtime efetivo) | **3.53.2** | Asserção em runtime no boot (`SqliteDataSourceConfig`), acima do piso 3.51.3 exigido |
| Java | **21.0.12** (Temurin) | instalado nesta sessão via SDKMAN |
| Spring Boot | **4.1.1** | pinado no `pom.xml`, versão estável mais recente da linha 4.1.x |

---

## 3. Modelo DW/OLTP encontrado

- Banco único `esus` (18 GB), **schema único `public`**, 1092 relações.
- Fato relevante: `tb_fat_atendimento_individual` (294 114 linhas, 2023-07-03 a 2026-04-30).
- Dimensões usadas: `tb_dim_tempo`, `tb_dim_municipio`, `tb_dim_tipo_atendimento`,
  `tb_dim_unidade_saude`, `tb_dim_equipe`, `tb_dim_cbo`.
- Papel da instalação: **`PRONTUARIO`** (DW individualizado presente, município único).
- `tb_dim_municipio` é uma tabela de referência **nacional** (1870 municípios), não particionada
  por instalação — a fronteira municipal precisa ser aplicada explicitamente pelo adaptador, nunca
  assumida estruturalmente (confirmado via teste com fixture de dois municípios).

---

## 4. Capability mapping obtido

Uma única capability implementada e validada: **`individual_encounter_modality`**.

Mapeamento de `tb_dim_tipo_atendimento` congelado como lista explícita de ids-folha (nunca filtro
por `co_dim_tipo_atendimento_pai`):

| Braço C1 | ids | status |
|---|---|---|
| programado | 2, 3 | `VALIDATED` |
| espontâneo | 5, 6, 7 | `VALIDATED` |
| não observado | 8, 9, 10, 11 | `NOT_TESTED` (zero ocorrências em 294 114 linhas / 33 meses de histórico) |

Registrado em [`contracts/compatibility/pec-adapters.json`](../contracts/compatibility/pec-adapters.json),
com schema declarado em [`contracts/compatibility/pec-adapters.schema.json`](../contracts/compatibility/pec-adapters.schema.json).
**Correção de precisão:** nenhum teste valida `pec-adapters.json` contra esse schema em build —
o schema existe (`IMPLEMENTED`) mas não é aplicado automaticamente. O que *é* testado
(`PecAdaptersMatrixConsistencyTest`) é mais estreito: garante que o `query_checksum` gravado no
JSON nunca diverge do checksum computado em runtime a partir da query real do adaptador — ver §8.

---

## 5. Primeiro indicador implementado: C1 — Mais acesso

Fórmula e faixas implementadas **verbatim** da Tech Spec §2.4 (não-monotônica: valor acima de 70%
é classificado *Regular*, não melhor que *Ótimo*).

**Portões de liberação (§4.4), registrados honestamente, não colapsados em "funciona":**

| Portão | Status | Motivo |
|---|---|---|
| A — fonte/vigência | `BLOCKED` | Q01 (nota metodológica oficial do MS) não foi recuperada nesta sessão |
| B — modelo de cálculo | `BLOCKED` | depende de A para a lista exata de CBO |
| C — adaptador | **`VALIDATED_AGAINST_PEC`** | é isto que este trabalho efetivamente prova |
| D — reconciliação | `NOT_IMPLEMENTED` | nenhuma referência Siaps/SISAB disponível neste ambiente |
| E — piloto/operação | `BLOCKED` | exige revisão humana, não código |

Por isso `C1Rule` embarca a limitação explícita `cbo_policy=ALL_CBO` em todo resultado — nenhum
filtro de CBO foi inventado sem a ficha oficial.

---

## 6. Consultas integradas ao adaptador

Uma única query congelada, com checksum verificado por teste (`PecAdaptersMatrixConsistencyTest`):

```sql
SELECT f.co_seq_fat_atd_ind, f.co_dim_tipo_atendimento, t.dt_registro,
       u.nu_cnes, e.nu_ine, c.nu_cbo, f.nu_uuid_ficha, f.nu_atendimento
  FROM tb_fat_atendimento_individual f
  JOIN tb_dim_tempo      t ON t.co_seq_dim_tempo       = f.co_dim_tempo
  JOIN tb_dim_municipio  m ON m.co_seq_dim_municipio   = f.co_dim_municipio
  LEFT JOIN tb_dim_unidade_saude u ON u.co_seq_dim_unidade_saude = f.co_dim_unidade_saude_1
  LEFT JOIN tb_dim_equipe        e ON e.co_seq_dim_equipe        = f.co_dim_equipe_1
  LEFT JOIN tb_dim_cbo           c ON c.co_seq_dim_cbo           = f.co_dim_cbo_1
 WHERE m.co_ibge = ?
   AND t.dt_registro >= ? AND t.dt_registro < ?
 ORDER BY f.co_seq_fat_atd_ind
```

Recorte municipal em `tb_dim_municipio.co_ibge` (texto de 7 dígitos), nunca na chave surrogate
local. Atributo CNES/INE/CBO via `_1` (participante primário), confirmado por inspeção: `_2` é
usado em ~1,3% dos encontros como segundo participante de atendimento compartilhado, não como
atribuição alternativa.

---

## 7. Resultado dos testes

**33/33 testes passam** (`mvn test`, ~40s, incluindo 4 testes que falam com o PEC real via túnel e
1 teste Testcontainers com PostgreSQL 9.6 real).

| Suíte | Testes | O que prova |
|---|---|---|
| `SqliteDataSourceConfigTest` | 4 | WAL/foreign_keys/synchronous/busy_timeout em conexões recém-obtidas, piso de versão SQLite, migração Flyway, lock de processo recusa segunda instância |
| `ExtractWriterReaderTest` | 4 | ciclo escrita→finalize→leitura, manifesto ausente é rejeitado, crash antes do finalize não deixa extrato válido, arquivo adulterado é rejeitado por checksum |
| `AllowedDestinationsTest` | 4 | allowlist de destino, DNS re-resolvido, host não aprovado recusado |
| `PecDataSourceFactoryLiveTest` | 1 (vivo) | conecta via pgJDBC real, GUCs de orçamento aplicados e visíveis na sessão, sem privilégio de escrita |
| `IndividualEncounterModalityCapabilityLiveTest` | 1 (vivo) | reproduz 7100/2929/10029 (70,7947%) contra o PEC real |
| `IndividualEncounterModalityCapabilityIsolationTest` | 1 | Testcontainers `postgres:9.6`, dois municípios com IDs surrogate compartilhados — isolamento municipal provado, não presumido |
| `PecAdaptersMatrixConsistencyTest` | 1 | checksum da query no `pec-adapters.json` nunca diverge do código |
| `ExactRatioTest` | 4 | fronteira exata, razão não-terminante, `toScaledBigDecimal` é somente para exibição |
| `C1RuleTest` | 7 | MET-03, MET-04, MET-18, MET-33, fronteiras ENG-25, exclusão mútua, regressão real 2026-03 |
| `ModuleBoundaryTest` (ArchUnit) | 5 | `indicator-engine`/`indicator-packs` não dependem de JDBC/SQL/Hikari/Spring JDBC/pec-adapter/source-connector |
| `Eng19ReproducibilityWithoutPecLiveTest` | 1 (vivo) | **prova de ponta a ponta**: adquire do PEC real, escreve extrato, fecha completamente o pool PEC, lê o extrato, calcula — reproduz 70,7947%/Regular sem PEC conectado |

Os 4 testes marcados "(vivo)" se auto-pulam (não falham) quando o arquivo de segredo dev não existe
ou o túnel não está acessível (`LivePecAssumptions`) — importante após a sessão NetBird expirar.

---

## 8. Critérios MET-*/ENG-* com teste executável

**Com teste:** MET-03, MET-04, MET-18, MET-33 · ENG-01 (privilégios inspecionados, nunca escritos),
ENG-02 (fingerprint por capability, não a fonte toda), ENG-19, ENG-20, ENG-25, ENG-28, ENG-29,
ENG-35, ENG-37, ENG-38, ENG-46 (parcial — allowlist e DNS, não todo o ENG-46).

**Correção de precisão sobre ENG-43** ("nenhuma capability entra em produção sem `tested_with`
não-vazio, verificado em build"): o que existe é `PecAdaptersMatrixConsistencyTest`, que verifica
apenas que o `query_checksum` gravado no JSON não diverge do checksum real da query — uma garantia
mais estreita que "validação de schema em build time". Não há teste que rejeite um `tested_with`
vazio ou que valide o documento inteiro contra `pec-adapters.schema.json`. ENG-43 portanto entra na
lista "sem teste" abaixo; o schema em si é `IMPLEMENTED`, não `TESTED`.

**Sem teste ainda (não implementados, não simplesmente "esquecidos"):** ENG-03 a ENG-18 exceto os
acima, ENG-21 a ENG-24, ENG-27, ENG-30 a ENG-34, ENG-36 (parcial — extrato reconstrói denominador
mas evidência mínima por pessoa não é persistida), ENG-39 a ENG-42, ENG-43 (ver correção acima),
ENG-44, ENG-45, ENG-47 a ENG-52.
MET-01/02/05 a 17/19 a 32/34 a 39 não têm teste — dependem de indicadores/pacotes fora do escopo
desta entrega (I1–I7, C2–C7, VAT, IGM).

---

## 9. Status por item (nomenclatura exigida)

### `VALIDATED_AGAINST_PEC`
- Identificação de PEC 5.4.37 / PostgreSQL 9.6.13.
- Credencial `esus_leitura`: privilégios (1098/1098 SELECT, zero escrita), login, os 5 GUCs de
  orçamento.
- Query congelada do C1 e suas contagens para 2026-01 a 2026-04.
- Invariante de grão (sem duplicação) para 2026-03.
- Atribuição `_1` vs `_2`.
- Equivalência de fuso horário (`dt_registro` == `dt_inicial_atendimento AT TIME ZONE
  'America/Sao_Paulo'`, 0 divergências em 10029 linhas).
- ENG-19 (recomputação sem PEC conectado).

### `TESTED` (não contra o PEC real)
- Contrato de pragmas SQLite, isolamento do Flyway, lock de processo.
- Ciclo de vida do extrato (escrita/finalize/leitura/rejeição de parcial/rejeição de adulterado).
- `ExactRatio`/bandas do C1, MET-03/04/18/33, fronteiras ENG-25.
- Fronteira arquitetural `indicator-engine` (ArchUnit).
- Isolamento municipal (`ENG-37`/`ENG-38`) via fixture sintética Testcontainers.

### `IMPLEMENTED` (código existe, sem teste dedicado ainda)
- `BudgetGuard` (limite de linhas/duração) — lógica presente, exercida indiretamente pelos testes
  vivos (que nunca chegam perto do limite), sem teste que force o estouro.
- Schema SQLite completo (`sources`, `jobs`, `extraction_manifests`, `results`) — só
  `extraction_manifests` tem código de escrita real; `jobs`/`results`/`sources` existem apenas
  como DDL, sem repositório.

### `BLOCKED`
- Portões A, B, E de C1 (ficha Q01 não recuperada; revisão humana não é código).
- Reconciliação com Siaps/SISAB (Portão D) — nenhuma referência disponível.

### `NOT_IMPLEMENTED`
- **job-runner**: máquina de estados, geração de execução, cancelamento cooperativo, recuperação
  pós-restart — tabela existe, nenhum código escreve nela.
- **result-store**: publicação atômica, histórico, comparação entre execuções.
- **identity-access**: autenticação, sessão, bootstrap do primeiro admin, RBAC.
- **API REST / SSE** (`/api/v1/*` inteiro).
- **UI** (frontend não iniciado — `apps/web` é um diretório vazio).
- **audit-operations**, **quality-rules**, **external-data**: nenhum código.
- **evidência mínima persistida** — ENG-19 prova valor/numerador/denominador/classificação
  idênticos, mas não persiste evidência por pessoa (a cláusula "evidências" de ENG-19 é parcial).
- Empacotamento (`jpackage`), SBOM, assinatura de release, Ansible aplicado.

---

## 10. Problemas encontrados (e como foram resolvidos)

1. **Artefatos Maven renomeados no Boot 4.x/Testcontainers 2.x**: `flyway-database-sqlite` não
   existe (é `flyway-database-nc-sqlite`); `org.testcontainers:postgresql`/`junit-jupiter` viraram
   `testcontainers-postgresql`/`testcontainers-junit-jupiter`; Boot 4 não traz mais
   `spring-boot-autoconfigure` para JDBC/Flyway como parte do `starter-jdbc` — descobertos por
   erro de build, não por suposição, e documentados nos comentários do `pom.xml`.
2. **`SQLiteConfig.busyTimeout` não existe** — o método real é `setBusyTimeout`.
3. **pgJDBC rejeita `date >= character varying`**: bind de data como `String` faz o driver inferir
   `varchar`; corrigido com `setDate(java.sql.Date)`. Achado ao vivo no spike, documentado no
   discovery doc e no javadoc do adaptador.
4. **`sudo` interativo indisponível** no ambiente do agente — JDK/Maven instalados via SDKMAN
   (sem root) em vez de `apt`.
5. **Checksum "sha256:" fake**: a primeira versão da matriz de compatibilidade e do teste ENG-19
   usaram `hashCode()` rotulado como SHA-256. Corrigido para um SHA-256 real computado uma vez e
   reutilizado (`QUERY_CHECKSUM`), com teste que impede a matriz de divergir da query.
6. **Bug no próprio fixture de teste**: um ponto-e-vírgula dentro de um comentário em prosa quebrou
   um split ingênuo de SQL por `;`. Corrigido enviando o arquivo inteiro em um único `execute()`
   (o protocolo simples do pgJDBC entende múltiplos statements e comentários corretamente).
7. **Contagem errada nos meus próprios asserts de teste** (isolamento municipal): recontei a
   fixture manualmente e corrigi 6/2 para 7/1 programados/espontâneos do município B.

---

## 11. Decisões arquiteturais tomadas (ADRs)

- [ADR 0001](adr/0001-monolito-modular-maven-unico.md): monólito modular em um único módulo Maven;
  `indicator-packs` é pacote Java, não módulo Maven separado.
- [ADR 0002](adr/0002-credencial-esus-leitura-existente.md): uso do `esus_leitura` pré-existente.
- [ADR 0003](adr/0003-tunel-ssh-para-pec.md): túnel SSH dedicado, uma chave reversível.
- [ADR 0004](adr/0004-competencia-piloto-2026-03.md): competência piloto 2026-03.
- [ADR 0005](adr/0005-exactratio-em-vez-de-double.md): `ExactRatio`/`BigInteger` em vez de `double`.

---

## 12. Arquivos e commits principais

`git log --oneline`, do primeiro ao mais recente (10 commits; o último é este relatório):

```
353fc85 first commit
335bea4 chore: add gitignore and track tech spec v0.4
b0581b1 docs: PEC CT 133 discovery, ADRs 0001-0005, adapter matrix schema
c18872e feat(agent): SQLite persistence, Flyway isolation, process lock
3a6e618 feat(agent): source-connector — structured config, allowlist, read budget
b0797e8 feat(agent): pec-adapter — individual_encounter_modality capability
7cd4235 feat(agent): extraction-store — minimal extract as gzipped JSONL + manifest
542c18b feat(agent): indicator-engine, C1 rule pack, ArchUnit, ENG-19 proof
1cdfaac test(agent): ENG-37/ENG-38 fixture, honesty fixes, live-test resilience
64bcac5 docs(report): relatório técnico final do MVP Observatório APS
```

Código: `apps/agent/src/main/java/br/gov/observatorioaps/` — **26 arquivos Java produtivos**, mais
**12 de teste** (`find apps/agent/src/{main,test} -name "*.java" | wc -l`, verificado nesta sessão).
Contratos: `contracts/compatibility/pec-adapters.{json,schema.json}`. Documentação:
`docs/discovery/`, `docs/adr/`.

---

## 13. Instruções exatas para executar o sistema

**Pré-requisitos** (instalados nesta sessão via SDKMAN, sem sudo):
```bash
curl -s "https://get.sdkman.io" | bash
source ~/.sdkman/bin/sdkman-init.sh
sdk install java 21.0.12+1.1-tem
sdk install maven 3.9.16
```

**Túnel para o PEC** (necessário apenas para os testes "vivos" e para uma futura API):
```bash
ssh -N -i ~/.ssh/observatorio-pec -L 15433:127.0.0.1:5433 root@192.168.1.209 &
```

**Segredo local** (já criado nesta sessão em `~/.config/observatorio-aps/pec.env`, 0600):
```
PEC_DB_HOST=127.0.0.1
PEC_DB_PORT=15433
PEC_DB_NAME=esus
PEC_DB_USER=esus_leitura
PEC_DB_PASSWORD=<obtido de /opt/e-SUS/webserver/config/credenciais.txt no CT 133>
```

**Rodar toda a suíte de testes:**
```bash
cd apps/agent
mvn test
```
Sem o túnel ativo, os 4 testes vivos se auto-pulam; os outros 29 rodam normalmente (o teste de
isolamento usa Docker/Testcontainers, não o PEC).

**Não há, ainda, um `main()` utilizável de ponta a usuário.** Verificado nesta sessão executando
`mvn spring-boot:run` de fato (não apenas por leitura de código): o contexto Spring sobe, o Flyway
migra `~/.local/share/observatorio-aps/observatorio.sqlite` (`Database: jdbc:sqlite:...` no log),
`ObservatorioApsApplication` reporta `Started ... in 1.17 seconds` — e então **o processo encerra
sozinho** (exit code 0), porque não há `spring-boot-starter-web`/servidor embutido mantendo-o vivo.
Ou seja: a inicialização (SQLite + Flyway + lock de processo) é `TESTED` de fato, mas não há
endpoint HTTP nem processo de longa duração para consultar depois do boot.

---

## 14. Limitações existentes

- Nenhuma interface de usuário existe.
- Nenhuma API HTTP existe — tudo foi provado por testes de integração Java, não por um cliente real.
- Autenticação/autorização não implementadas — o modelo de permissões (`manage_source`,
  `manage_access`, `read_clinical`) está descrito no ADR e na Tech Spec, não codificado.
- `job-runner` não executa jobs de fato — não há fila, não há estados, não há geração incrementada
  em produção (só a tabela DDL existe).
- C1 não está liberado como "metodologia validada" — Portões A/B/D/E bloqueados, ver §5.
- O ambiente de desenvolvimento depende de um túnel SSH manual e de uma sessão NetBird que expira
  periodicamente — não é um caminho de produção.
- Apenas uma competência (2026-03) e um município (3541307) foram exercitados ponta a ponta; outras
  competências (01, 02, 04/2026) foram verificadas apenas por contagem SQL direta, não pelo pipeline
  completo.
- O secret gov.br OAuth (`client-secret`) foi exposto no transcript/log desta sessão ao inspecionar
  `application.properties` — nunca commitado, mas a rotação é decisão do usuário.

---

## 15. Próximos passos, ordenados por dependência técnica

1. **Recuperar e arquivar Q01** (nota metodológica oficial C1) para destravar os Portões A/B — sem
   isso, C1 nunca deixa de ser uma prova técnica para virar um indicador "liberado".
2. **`result-store` + publicação atômica** (staging → transação final curta → `SUCCEEDED`) — é o
   próximo passo natural sobre o que já existe (`extraction_manifests`/`results` já no schema).
3. **`job-runner` real**: máquina de estados sobre a tabela `jobs` já criada, lock de execução por
   `execution_generation`, cancelamento cooperativo (ENG-06/07/21/23).
4. **API REST mínima** (`POST /sources`, `POST /sources/{id}/test`, `POST /runs`, `GET
   /runs/{id}`, `GET /results`) sobre o que já existe — sem isso não há como demonstrar o fluxo
   fora de testes Java.
5. **`identity-access`**: bootstrap do primeiro admin sem senha padrão, sessão Spring Security,
   RBAC de escopo municipal.
6. **SSE** para acompanhamento de job.
7. **UI mínima** (`apps/web`, ainda vazio): login, fontes, execuções, resultado.
8. **Perfil de carga aprovado** (P12) — os valores de `ReadBudget.initialEngineeringProposal()` são
   a proposta da própria spec, não uma política testada sob carga real.
9. **Empacotamento** (`jpackage`), SBOM, assinatura de release — só depois de haver um produto
   completo para empacotar.
