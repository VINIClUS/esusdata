# ADR 0024 — Exportação agregada de resultados em CSV

## Status
Accepted. Primeira exportação exposta pela API (Tech Spec §1.10 L393 e L409). Atualiza a nota do
ADR 0007 sobre exportação.

## Contexto

A tela "Relatórios" só funcionava com mocks (issue #22). Ela prometia relatórios em PDF e XLSX,
com gráficos, séries históricas e um relatório de qualidade. Nada disso existia no backend: nenhuma
rota, nenhuma biblioteca de PDF ou planilha, nenhum armazenamento de arquivos. A Tech Spec prevê
`POST /api/v1/exports` com expiração, auditoria e checagem de escopo no download, e pede que cada
fluxo defina layout, quotas, estados, arquivo expirado e códigos de erro (L409).

## Decisão

- **Só CSV, só agregado.** A exportação traz os resultados já publicados de um município, uma linha
  por pacote de indicador e competência, no intervalo `[fromPeriod, toPeriod]`. Quando há mais de
  uma publicação para o mesmo par, vale a mais recente (`published_at`, desempate por
  `result_id`), a mesma que a tela mostra. Nenhum registro, nenhuma evidência. PDF, XLSX, gráficos
  e o relatório de qualidade saem da tela até terem backend.
- **Permissão: `READ_CLINICAL` no município inteiro**, via `ApiAuthorization.requireObjectScope`,
  como `GET /results`. Um grant por equipe não alcança o agregado, e o admin técnico não tem
  `READ_CLINICAL`. Nenhuma permissão nova, nenhuma migração de `role_permissions`.
- **Sem reautenticação.** A L539 a exige para exportação *individualizada*; esta não tem registro.
- **Geração síncrona, sem estados.** `POST /exports` lê só o SQLite, nunca o PEC (L521), e devolve a
  exportação pronta (201). Ou ela existe completa, ou a requisição falhou.
- **O arquivo fica no SQLite.** `report_exports` (V6) guarda o CSV num BLOB, com o município, o
  intervalo, o pacote (nulo = todos), o autor e `expires_at`. O download nunca recebe caminho nem
  nome do cliente: o nome é montado pelo servidor.

### Layout

UTF-8 com BOM, `;` entre células, CRLF entre linhas, toda célula entre aspas (aspas internas
dobradas). Colunas, nesta ordem:

`municipio_ibge; indicador; versao_regra; competencia; status; numerador; denominador;
valor_percentual; classificacao; data_corte; publicado_em; execucao`

- `numerador` e `denominador` são os textos exatos guardados (ADR 0005).
- `valor_percentual` é o `value_text` guardado (`ExactRatio` em percentual, escala 4), só com o
  ponto trocado por vírgula para a planilha pt-BR. Nada é recalculado. Fica vazio sem denominador.
- `limitations_json` fica de fora: é JSON, não cabe numa célula.
- **Fórmulas (ENG-48, L518):** célula que começa com `=`, `+`, `-`, `@`, tabulação ou CR ganha um
  apóstrofo na frente, para ser lida como texto. As aspas sozinhas não bastam.

### Quotas, retenção e erros

| Fluxo | Regra | Resposta |
|---|---|---|
| Intervalo | `yyyy-MM`, `from <= to`, no máximo 24 competências | 400 `BAD_REQUEST` |
| Pacote | vazio (todos) ou um de `GET /indicator-packs` | 400 `BAD_REQUEST` |
| Quota | 10 exportações por usuário por hora; a contagem e a inserção são um só `INSERT … SELECT … WHERE` | 429 `EXPORT_QUOTA_EXCEEDED` |
| Escopo | `READ_CLINICAL` no município, conferido na criação, na lista e em **todo** download | 404 `NOT_FOUND` |
| Expirado | 7 dias após a criação | 404 `NOT_FOUND` |

O expirado responde 404, não 410: arquivo inexistente, de outro município ou expirado dão a mesma
resposta, como no resto da API (§1.10.1 L403). A lista só mostra os que não expiraram.

A limpeza roda em toda criação e listagem, e também no boot (`expiredExportsPurgedOnBoot`), para
apagar o que expirou com a aplicação parada (L437). Apagar a linha não garante eliminação física
no disco nem nos backups (L475).

### Auditoria

`EXPORT_CREATED` e `EXPORT_DOWNLOADED` em `auth_audit`, com o id da exportação. O detalhe leva só
o município, o intervalo e a contagem de linhas: nenhum valor exportado.

## O que a exportação afirma, e o que não afirma

Ela reproduz os resultados publicados pelo observatório, com a regra e a versão que os produziram.
Ela **não** é o relatório oficial do SISAB, não traz evidência nominal, não calcula séries nem
comparativos, e não diz nada sobre qualidade dos dados além do `status` de cada resultado.

## Consequências

- A lista "Exportações recentes" é do município, não do usuário: quem lê o agregado vê as
  exportações que outros fizeram dele, que têm o mesmo conteúdo que ele já pode ler.
- Instantes são comparados como texto ISO-8601, então todos são gravados em segundos inteiros
  (`JdbcReportExportRepository.iso`).
- Uma exportação individualizada, se vier, precisa de outro ADR: permissão própria (ADR 0007),
  reautenticação (L539) e o volume criptografado da L471.
