# Portão D: Nota Final do Componente III contra o SIAPS (`siaps-nota-final-por-classe@2`)

Esta é a regra do Portão D da entrada `componente-iii-nota-final` do registro de portões
(`contracts/indicators/release-gates.json`). Ela espelha `siaps-distribuicao-por-classe@2`
(`docs/indicadores/portoes/portao-d-conciliacao-siaps.md`) e foi fixada **antes** de existir qualquer
dado para a comparação. Qualquer mudança de métrica, limiar ou conjunto de equipes exige uma nova versão
do check (`@3`, ...) e um motivo que não seja "o resultado reprovou". O `@1` (o quadrimestre mais recente
publicado e elegível pela data das fichas) foi aposentado
([ADR 0034](../../adr/0034-reconciliacao-siaps-retrospectiva.md)) e não é mais aceito como evidência nova.

O produto nunca chama o SIAPS. A conferência é a mesma ferramenta de desenvolvimento do Portão D de
C1–C7 (árvore de testes, sob demanda); a saída dela (o resumo do conjunto, em JSON, mais o sha256) vira
a evidência de `gates.D` da entrada do Componente III. FAILED mantém a Nota Final bloqueada, e isso é o
comportamento correto.

## O que o SIAPS anônimo oferece

A mesma resposta de `indicador-quadrimestre/filtro` que serve a C1–C7 traz `classificacaoFinalComponente`
(ver `docs/discovery/2026-10-06-siaps-publico-componente-qualidade.md`): por município, quadrimestre e
tipo de equipe (`sgEquipe`), e por `tipoOrigem` (`CVAT` ou `QUALIDADE`), a **quantidade de equipes** em
cada classe final (`qtdClassificacaoOtimo/Bom/Suficiente/Regular`) e `totalEquipesValidasParaComponente`.
Esta regra usa só as linhas `tipoOrigem = QUALIDADE` de `sgEquipe` eSF e eAP. As linhas CVAT, eSB e
eMulti ficam fora (outros quadros da NT 8/2026, fora do escopo do Componente III de eSF/eAP). Não há
nota nem classe por equipe.

## Referências: o conjunto pré-registrado

Como em C1–C7, nenhuma data escolhe ou exclui uma referência. As referências da Nota Final são as
declarações da entrada `componente-iii-nota-final` de `contracts/indicators/siaps-reference-policy.json`
(`reference_id` com `ciii`), cada uma de um export oficial por equipe e com o dossiê de compatibilidade
metodológica que a fixa. A seleção é `ALL_REQUIRED` e o hash do conjunto (`gate_set_sha256`) é o que o D cita;
as regras de conjunto vazio, de referência apta e de FAILED que nunca se amacia são as de
`portao-d-conciliacao-siaps.md`. A Nota Final de uma referência é calculada sobre os mesmos sete conjuntos de
partições que o dossiê usou, e o `local_source_fingerprint` dela é derivado das partições dos sete packs: se
for diferente do que consta no dossiê, a referência fica PENDING.

## Conjunto de comparação

Por tipo de equipe (eSF e eAP), os totais do SIAPS são as quatro contagens da linha QUALIDADE, e N_S é
a soma delas.

Lado local: a **classe da Nota Final de cada equipe** no quadrimestre, pela consolidação da NT 8/2026
do produto (`Nt08Consolidation`: média exata dos meses de cada indicador, faixa da ficha, fator
0,25/0,50/0,75/1,00, Nota Final = soma de fator vezes peso com pesos 1/2/2/1/1/1/2, classe pelo
Quadro 6; é a classe metodológica, não a financeira da Portaria 10.994/2026). As entradas são os
resultados mensais por equipe **sem o bloqueio dos portões** dos sete packs (um mês bloqueado deixaria
toda equipe sem nota e transformaria a comparação em ruído), nos quatro meses do quadrimestre.

Equipes comparadas: com o arquivo oficial por equipe, as que têm a linha `Total` ("Nota final e
classificação final") no arquivo, por tipo; todo INE eSF ou eAP do arquivo precisa ter essa linha, senão o
resultado é PENDING. Com o agregado público (só diagnóstico), a lista usada é a **interseção** das listas
atuais de C1–C7 (mesmo INE e mesmo tipo nas sete, porque a Nota Final só existe para equipe com os sete
indicadores), apenas como chave de separação: o agregado não traz o universo histórico e não vira
evidência do gate (ver "O que decide o gate e o que é só diagnóstico" em `siaps-distribuicao-por-classe@2`). Se a resposta do SIAPS
não trouxer alguma das sete listas, o resultado é PENDING.

- INE do universo sem Nota Final local (algum indicador bloqueado, ambíguo, sem denominador ou ausente em
  algum mês): reportado como "sem classe local" e fora das contagens locais.
- INE local que não está no universo: reportado e excluído.
- N_L é o número de equipes locais efetivamente contadas.

## Métrica e limiar

Os mesmos de `siaps-distribuicao-por-classe@2`. Classes ordenadas REGULAR < SUFICIENTE < BOM < ÓTIMO; com as contagens acumuladas
cumL(k) e cumS(k) para k = 1..4,

    D = Σ_{k=1..4} |cumL(k) − cumS(k)|

A linha passa se, e somente se, D ≤ T, com **T = max(2, ⌈0,15 · N_S⌉)**. Linhas com N_S = 0 e N_L = 0
são ignoradas; um lado com zeros provados por um universo oficial completo é avaliado com a mesma
fórmula, e uma linha oficial ausente, ou uma linha de zeros do agregado público, não é zero (o resultado é
PENDING).

## Veredito

- **PASSED** se há ao menos uma linha avaliada e toda linha avaliada (eSF e eAP) passa.
- **FAILED** se alguma linha reprova.
- **PENDING** se a referência não está apta, se faltam as entradas locais
  (algum dos sete packs, em algum dos quatro meses), se o SIAPS não devolveu a linha QUALIDADE de
  `classificacaoFinalComponente` ou alguma das sete listas de equipes, se alguma equipe do arquivo
  oficial não tem a linha `Total`, se o universo é desconhecido numa rodada de gate (agregado público), ou
  se nenhuma linha é avaliável ("sem linhas avaliáveis").

## Mascaramento e privacidade

Como em `siaps-distribuicao-por-classe@2`: a evidência versionada mostra, por linha, tipo de equipe, N_S e N_L (escritos `<10`
quando menores que 10), "sem classe local", D, T e veredito, mais quadrimestre, versão da regra, id do
check e data. Contagens por classe e classes por INE ficam só no diretório local ignorado pelo git.
Nunca há dado de paciente.

## Como o D é gravado e a relação com C1–C7

A ferramenta grava `gates.D` da entrada `componente-iii-nota-final` da `rule_version` vigente, com o
check `siaps-nota-final-por-classe@2`, a data e a evidência (`kind` `conciliacao-siaps`, `ref` e
`sha256` do resumo do conjunto em JSON, `docs/indicadores/portoes/resultado-d/<rule_version>.json`). O D da Nota Final é independente dos D dos sete packs: a Nota Final só aparece
quando os portões dela (A e D) passam **e** os resultados mensais de C1–C7 que ela lê não chegam
bloqueados pelos portões de cada pack (`QualityComponentService`, `Nt08Consolidation`). O modo
diagnóstico nunca é gravado no registro.
