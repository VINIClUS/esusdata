# ADR 0001 — Monólito modular em um único módulo Maven

## Status
Accepted

## Contexto
A Tech Spec §1.5 propõe um monorepo lógico (`apps/web`, `apps/agent`, `indicator-packs`,
`contracts`, `docs`, `deployment`) mas é explícita: *"São limites lógicos/pacotes com dependências
verificáveis, não dez serviços nem obrigação de dez subprojetos de build."*

## Decisão
`apps/agent` é **um único projeto Maven**. Os módulos arquiteturais (`identity-access`,
`source-connector`, `pec-adapter`, `extraction-store`, `indicator-engine`, `job-runner`,
`result-store`, `external-data`, `quality-rules`, `audit-operations`) são pacotes Java sob
`esusdata.<pacote>` (nomes atuais em ADR 0013), não módulos Maven separados. `indicator-packs` compila como
código-fonte incluído na mesma release (§1.5: *"Pacotes compilados no MVP... incluídos na mesma
release do serviço"*).

O limite `indicator-engine` não depende de JDBC/SQL é garantido por **ArchUnit**, não por separação
de build.

## Consequências
- Um único `mvn verify` compila e testa tudo.
- Refatorar um pacote para módulo Maven separado é possível depois, sem afetar a spec.
- Testes de arquitetura (ArchUnit) tornam-se o mecanismo de imposição, não a estrutura de diretórios.
- Organização interna de cada pacote-módulo em camadas `domain`/`application`/`infrastructure`:
  ver [[0009-camadas-hexagonais-por-modulo]].
