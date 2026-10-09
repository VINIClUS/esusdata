# Runbook: conferência do Portão D com o SIAPS

O Portão D deixou de esperar o quadrimestre 2026Q2 do SIAPS: ele reconcilia retrospectivamente, com a
exportação oficial por equipe e um dossiê de compatibilidade metodológica por referência
([ADR 0034](../adr/0034-reconciliacao-siaps-retrospectiva.md)). **Como rodar (captura, diagnóstico,
compatibilidade, replay e gate, o túnel somente leitura e onde saem os arquivos):
[`runbook-portao-d-retrospectivo.md`](runbook-portao-d-retrospectivo.md).**

O `PortaoDLiveTest` (o fluxo único do `siaps-distribuicao-por-classe@1`, que consultava a API pública do SIAPS
e escolhia o "último quadrimestre elegível") foi removido. Regra e limiar: C1–C7 em
`docs/indicadores/portoes/portao-d-conciliacao-siaps.md`, Nota Final do Componente III em
`docs/indicadores/portoes/portao-d-nota-final-siaps.md`.

O produto nunca chama o SIAPS. A ferramenta vive na árvore de testes
(`apps/agent/src/test/java/esusdata/indicator/reconciliation`) e só roda por opt-in.

## Arquivo oficial por equipe

O arquivo "Avaliação do quadrimestre" do Componente de Qualidade, baixado à mão do SIAPS, é lido por
`OfficialTeamExportCsvParser` (layout `siaps-team-export@1`; o bruto nunca entra no Git). O município e o
quadrimestre vêm do cabeçalho do arquivo e das linhas, nunca do nome do arquivo, e têm de ser os esperados.
O leitor recusa exportação com coluna de pessoa (CPF, CNS, nome, nascimento, telefone, endereço; só `NOME DA
EQUIPE` passa), cabeçalho desconhecido, INE e indicador repetidos, tipo, indicador, classe ou número que não
conhece e arquivo sem a linha "Fonte" final. eSB e eMulti são contadas e não lidas. Nenhuma mensagem de recusa
traz INE, CNES, nome de estabelecimento ou de equipe.

Conferência do leitor sobre os arquivos reais (opt-in, local; um diretório só com as exportações
"Qualidade", e o IBGE de 7 dígitos do município):

```
mvn -f apps/agent/pom.xml test -Dtest=OfficialTeamExportRealFilesLiveTest -Dsurefire.failIfNoSpecifiedTests=false \
  -Dobservatorio.gate.d.official-export-dir=<diretório> -Dobservatorio.gate.d.official-export-ibge=<IBGE>
```

O teste espera 4 arquivos com 14 equipes eSF e eAP cada (os da primeira captura);
`-Dobservatorio.gate.d.official-export-files=<n>` e `-Dobservatorio.gate.d.official-export-teams=<n>` mudam
esses números. Cada arquivo tem de ler por inteiro (todo INE com os sete indicadores e a linha `Total`) e dar
o mesmo hash normalizado numa segunda leitura. O log mostra só a posição do arquivo, o quadrimestre, o estado
(preliminar ou final), as contagens por tipo, o que foi pulado e o hash normalizado de cada pack.

## Depois de registrar o D

Com o D registrado em `release-gates.json` e a versão liberada, não é preciso recalcular nada à mão: o
agendador trata como não cobertas as competências cujo resultado foi gravado com o D em outro estado
(ADR 0032, nota de 2026-10-06) e as recalcula sozinho, a mais antiga primeiro, um job por tick. Para
acelerar, dispare "Verificar agora" na fonte (`POST /api/v1/sources/{id}/schedule/run-now`), uma vez
por competência pendente.
