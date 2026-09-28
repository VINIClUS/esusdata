# Discovery — isolamento municipal e telas de #22 no PEC 5.5.28 de produção (2026-09-28)

Evidence for marking the `municipal_isolation` entry of `contracts/compatibility/pec-adapters.json`
`VALIDATED` (ADR 0023) and for the end-to-end run of what #42–#45 added. Read-only throughout: the
same access path and `esus_leitura` secret as [2026-09-24](2026-09-24-pec-5528.md); **nothing in the
PEC or on the host was modified**. Only counts, hashes and aggregate results are recorded here.

## Procedure (ADR 0023, "Para validar")

1. The entry was marked `VALIDATED` locally and the execution plane rebuilt (`include_str!`).
2. `ExecPlaneLivePecTest` ran with `-Dobservatorio.execution-plane.live-pec=true` over the tunnel,
   only the cases that exercise what is new: fingerprints, one-month acquisition, isolation and the
   diagnostic. The full-history cancel and the wrong-password cases were not run again: they load
   the production server or leave failed logins in its log without covering anything new.
3. The `-Pweb` JAR ran locally with a scratch data directory, the same binary and
   `allowed-destinations: 127.0.0.1:15434` (loopback, so plaintext is allowed — ADR 0022).

## Live test (4/4, 0 skipped)

| Case | Result |
|---|---|
| fingerprints | 7/7 identical to the matrix, over JDBC |
| one month through the Rust child | 2026-03: 10029 rows, 0 exclusions |
| isolation check, 2026-03 | `CHECKED`, `[3541307=10029]`, identical to the frozen query over JDBC, 2.5 s |
| diagnostic | identical to the JDBC reference |

## End to end through the app

| Step | Result |
|---|---|
| bootstrap activation, a MANAGER on 3541307 | ok |
| `POST /sources` (admin) · `GET /sources` | 201 · listed for the admin; empty for the MANAGER (no `MANAGE_SOURCE`) |
| `POST /sources/{id}/test` | `CONNECTED`, stored as `lastDiagnostic`; requires reauth (401 without it) |
| `GET /sources/{id}/requirements` | `READ_CONNECTION` false before the test, true after; the other three true |
| `POST /sources/{id}/isolation-check` 2026-03 | `CHECKED`, registered 10029, other municipalities 0, without IBGE 0 |
| same, 2099-01 · 2026-13 | `CHECKED` with 0 (the screen shows "Nenhum atendimento do município") · 400 |
| run C1 2026-03 | `SUCCEEDED` in 6 s: numerator 7100, denominator 10029, `BLOCKED` (gates A/B/D/E) |
| `POST /exports` 2026-01..03 · list · content | 201, 1 row · listed · CSV with BOM, `;`, 7100/10029 |
| export as TECHNICAL_ADMIN · other municipality · 76 competências | 404 · 404 · 400 (limit 24) |
| screens (Chromium): fonte, isolamento, relatórios, painel, indicadores | render the API data |

Found and fixed: the sidebar and the login page showed the demo fixture's `v0.4.0` instead of the
build's version; the `-Pweb` build now passes the Maven version to Vite (`VITE_APP_VERSION`), and
`login-e2e.mjs` asserts it.
