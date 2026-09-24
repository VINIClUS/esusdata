# ADR 0018 — Portão de análise estática

## Status
Accepted.

## Contexto

Nada verificava formatação nem análise estática: o único workflow (`package.yml`) empacota, e só
roda quando mudam caminhos de empacotamento. Estilo e classes inteiras de defeito (causa de exceção
perdida, recurso não fechado, cast que trunca) dependiam da revisão.

## Decisão

Cinco ferramentas, todas bloqueantes, com versões fixadas (§1.12.8):

- **Spotless + palantir-java-format** (`spotless:check` no `verify`): o formato é verificado, nunca
  aplicado em silêncio; `mvn spotless:apply` reescreve a árvore.
- **Error Prone** com `javac -Xlint:all -Werror`: todo aviso do javac ou do Error Prone quebra a
  compilação, de código principal e de teste, mais checks desligados por padrão promovidos a erro
  (lista no `pom.xml`). Os flags de acesso ao compilador ficam em `apps/agent/.mvn/jvm.config`.
- **PMD** (`pmd:check` no `verify`, código principal e de teste): todas as categorias Java, e
  `apps/agent/config/pmd/ruleset.xml` diz por que cada regra excluída ou ajustada não serve aqui.
- **rustfmt** (`cargo fmt --check`), com `rustfmt.toml` só de opções estáveis.
- **Clippy** com `clippy::all` negado, `clippy::pedantic` e algumas regras de restrição avisando, e
  `-D warnings` no CI; `unwrap`/`expect` só em testes (`clippy.toml`), `unsafe` proibido.

Exceção pontual é explícita e com motivo no próprio lugar: `@SuppressWarnings` com comentário ou
`// NOPMD - motivo` em Java, `#[expect(…, reason = "…")]` em Rust — `#[allow]` é recusado pelo
Clippy, e um `expect` que deixa de valer vira erro.

O workflow `ci.yml` roda os cinco em todo push para `main` e todo pull request, sem filtro de
caminho, para poder ser check obrigatório. Os testes continuam no `package.yml`, que os roda contra
o binário empacotado.

## Consequências

- A primeira aplicação reformatou quase todo o Java e o Rust num commit só, listado em
  `.git-blame-ignore-revs` (`git config blame.ignoreRevsFile .git-blame-ignore-revs`).
- Como os checks Java estão no `verify`, o `package.yml` também os roda, inclusive no Windows; o
  `.gitattributes` fixa LF em `*.java` e `*.rs` para o checkout do Windows não quebrar o formato.
- Branch aberta antes deste ADR precisa de `spotless:apply`/`cargo fmt` e dos achados do Error
  Prone, PMD e Clippy antes de mesclar.
