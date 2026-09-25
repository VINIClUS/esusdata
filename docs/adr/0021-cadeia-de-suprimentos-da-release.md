# ADR 0021 — Cadeia de suprimentos da release

## Status
Accepted.

## Contexto

O ADR 0019 deixou de fora quatro verificações da Tech Spec §1.12.8:
- CVEs do runtime Java: o Trivy não conhece o JDK, que no SBOM é `pkg:generic`;
- os plugins de build do Maven, que não estavam inventariados;
- o toolchain Rust;
- a assinatura do manifesto e a proveniência.

A §1.12.8 pede, na release, "manifesto, assinatura verificável desse manifesto, SBOM,
proveniência". O manifesto autentica por digest todos os arquivos, "incluindo inventários e
atestados". A confiança fica numa trust store local de fingerprints, com verificação offline, e os
segredos de assinatura ficam fora do alcance de builds não confiáveis.

## Decisão

- **JDK atualizado, no lugar de casamento de CVE.**
  - `deployment/sbom/check-jdk-current.sh` falha se o JDK do qual o jlink corta o runtime não for
    o update Temurin mais recente da mesma versão, consultado em `api.adoptium.net`.
  - A comparação é numérica sobre o `JAVA_RUNTIME_VERSION` do `release` (`21.0.12.1+1-LTS`).
  - Os jobs `linux` e `windows` do `package.yml` rodam esse check, com `check-latest: true` no
    `setup-java`.
  - Isto é um substituto, não uma varredura. Toda correção de CVE do OpenJDK sai num update
    trimestral, então "último update" equivale a "nenhuma CVE corrigível conhecida". O Grype, que
    casa o JDK por CPE do NVD, ficou de fora: é ruidoso, e seria mais uma ferramenta para fixar.
- **SBOM `backend-build`**, o oitavo inventário.
  - Contém os plugins que o `dependency:resolve-plugins -Pweb` resolve, com as dependências de
    cada um em `pkg:maven`, escopo `optional` e o grafo plugin → artefato.
  - Os plugins `clean`, `install`, `deploy` e `site` ficam de fora: a listagem os nomeia, mas os
    builds da release só rodam `package`/`verify`.
  - O processor path do compilador (Error Prone) e o próprio Maven não aparecem na listagem.
  - O job `sbom` o valida e o varre como os demais. A varredura diária do `security.yml` não o
    vê, porque ele não é empacotado.
  - Os plugins já estavam na versão mais recente e mesmo assim traziam HIGH. O
    `pluginManagement` do `apps/agent/pom.xml` fixa a correção mais próxima de cada biblioteca:
    - `plexus-utils` 3.6.1 e 4.0.3;
    - `commons-io` 2.22.0;
    - `commons-beanutils` 1.11.0;
    - `jackson` 2.21.7;
    - `cyclonedx-core-java` 11.0.1, uma major acima da do plugin. O SBOM do backend que o plugin
      gera com ela foi conferido idêntico e válido.
- **Proveniência.**
  - O `actions/attest-build-provenance` atesta cada `.deb`, `.msi` e `.cdx.json`: commit, tag,
    workflow e runner, assinados via Sigstore e registrados no GitHub.
  - O bundle é publicado como `observatorio-aps-<versão>.intoto.jsonl`, para verificação offline.
  - Não se alega nível SLSA.
- **Toolchains.** O rustc/cargo e o JDK de build entram na proveniência pelo workflow atestado,
  que os fixa (`RUST_TOOLCHAIN`, `setup-java`), e não como componentes de SBOM. Os SBOMs do plano
  de execução continuam listando as crates que compõem o binário.
- **Manifesto assinado.**
  - O `SHA256SUMS` cobre instaladores, inventários e o atestado.
  - `ssh-keygen -Y sign -n file` gera o `SHA256SUMS.sig` com uma chave ed25519. A privada fica só
    no secret `RELEASE_SIGNING_KEY` do Environment `release`, que admite apenas tags `v*`.
  - A trust store é `deployment/release/allowed_signers` (principal `release@observatorio-aps`,
    namespace `file`).
  - O job verifica a assinatura contra ela antes de criar a release.
  - O `ssh-keygen` vem no Linux e no Windows 10+, então a verificação offline não exige
    ferramenta extra.

## Consequências

- Um update trimestral do JDK bloqueia o empacotamento até o runner e o `setup-java` o
  alcançarem. Isso é o comportamento desejado.
- Cada piso do `pluginManagement` só existe enquanto o plugin não trouxer a versão corrigida.
  Revisar os pisos quando um plugin subir.
- A chave de assinatura tem custódia do mantenedor, separada das chaves de TLS e de dados
  (§1.12.7). Na rotação, gera-se um par novo, atualizam-se o secret e o `allowed_signers`, e o
  fingerprint novo é anunciado por um canal independente da chave antiga.
- Continuam de fora a assinatura do MSI, que é integração própria do `jpackage` (§1.12.8), e o
  inventário do Maven e do processor path do compilador.
