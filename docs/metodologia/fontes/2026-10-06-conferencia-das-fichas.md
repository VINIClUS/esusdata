# Conferência das fichas arquivadas contra as fontes oficiais (Portão A) — 2026-10-06

Evidência para aprovação humana do Portão A (Tech Spec §4.4: "Ficha original recuperada e arquivada pela equipe, edição e seções identificadas, competências aplicáveis confirmadas, alterações/revogações registradas"). **Este documento não aprova nenhum portão.**

## Método

- Baixados em 2026-10-06 os PDFs oficiais vigentes das sete notas metodológicas do Componente III (eSF/eAP) e a NT 8/2026, a partir dos links listados na página pública do SIAPS (`https://sisaps.saude.gov.br/sistemas/siaps/docs/manual/notas-metodologicas/`) e do índice gov.br (`https://www.gov.br/saude/pt-br/composicao/saps/publicacoes/fichas-tecnicas/equipe-de-atencao-primaria-e-saude-da-familia/`). PDFs mantidos só em diretório temporário (não versionados).
- Extração com `pdftotext -layout`; texto normalizado (NFKC para ligaduras, junção de hifenização, colapso de espaço/quebras/cabeçalhos de página) e comparado palavra a palavra com `docs/metodologia/fontes/*.txt`.
- Resultado: o texto extraído é **idêntico byte a byte** ao `.txt` arquivado nos oito documentos (e, portanto, também idêntico após normalização: 0 diferenças). Nenhuma diferença de extração, editorial ou metodológica. Por isso nenhuma transcrição `*-2026-10-06.txt` foi criada.

## Sobre "Atualizado em 02/10/2026"

A data vista na página gov.br é o campo "Atualizado em" do **índice** "Equipe de Atenção Primária e Saúde da Família" (publicado em 23/05/2025 15h43, atualizado em 02/10/2026 11h39), não de uma ficha específica. Os PDFs das notas não trazem data de atualização própria: valem as datas de assinatura SEI abaixo (todas entre 29/05/2026 e 24/06/2026). O texto atual de cada PDF é igual ao arquivado, logo a atualização do índice de 02/10 não alterou nenhum dos oito textos conferidos. Os metadados dos PDFs gov.br indicam geração em 24/06/2026. O servidor gov.br respondeu 403 a requisições HEAD, então não há `Last-Modified` das fichas; a evidência é o conteúdo.

## Tabelas por documento

Todas: resultado **igual** (idêntico ao arquivado). URL dos PDFs gov.br: `<índice>/nota-metodologica-<slug>/@@download/file`. Cada nota declara "Esta nota revoga" a versão anterior indicada.

| Campo | C1 Mais acesso |
|---|---|
| Título | NOTA METODOLÓGICA C1 - MAIS ACESSO |
| SEI (doc / processo) | 0054814890 / 25000.137969/2025-22 |
| Assinaturas | 23/06/2026 (Audrey Fischer, DEAPS), 23/06/2026 (CGSFC), 24/06/2026 (DESF) |
| Revoga | SEI 0050084955 |
| sha256 do PDF | `0f8ea6d7d315952cb7e6986163e57c0d55bf9105a87b67fd4bbcf2f58e997293` |
| URL (slug) | `nota-metodologica-c1-mais-acesso` |
| Resultado | igual |

| Campo | C2 Desenvolvimento infantil |
|---|---|
| Título | NOTA METODOLÓGICA C2 - CUIDADO NO DESENVOLVIMENTO INFANTIL |
| SEI / processo | 0054824593 / 25000.137969/2025-22 |
| Assinaturas | 19/06/2026 (DEAPS), 19/06/2026 (DGCI), 22/06/2026 (CGSCAJ) |
| Revoga | SEI 0049702562 |
| sha256 do PDF | `282030c5a610702f9ae8192515031ab735d549f781193e3489579ea7d4e80985` |
| URL (slug) | `nota-metodologica-c2-cuidado-no-desenvolvimento-infantil` |
| Resultado | igual |

| Campo | C3 Gestação e puerpério |
|---|---|
| Título | NOTA METODOLÓGICA C3 - CUIDADO NA GESTAÇÃO E PUERPÉRIO |
| SEI / processo | 0054619475 / 25000.137969/2025-22 |
| Assinaturas | 19/06/2026 (DEAPS), 22/06/2026 (DGCI), 22/06/2026 (CGSM) |
| Revoga | SEI 0050086461 |
| sha256 do PDF | `1f6a57eeb9832e7bd0af6bde9c8339950dce88d3af552cc2318d1618f8dd4ccd` |
| URL (slug) | `nota-metodologica-c3-cuidado-na-gestacao-e-puerperio` |
| Resultado | igual |

| Campo | C4 Diabetes |
|---|---|
| Título | NOTA METODOLÓGICA C4 - CUIDADO DA PESSOA COM DIABETES |
| SEI / processo | 0055986848 / 25000.137969/2025-22 |
| Assinaturas | 19/06/2026 (DEAPS), 21/06/2026 (DEPROS) |
| Revoga | SEI 0050086549 |
| sha256 do PDF | `fa9a8abbdab8623b9d776d6b730c1bdf7e1e01925205cd1bfb2973c5352e4ddc` |
| URL (slug) | `nota-metodologica-c4-cuidado-da-pessoa-com-diabetes` |
| Resultado | igual |

| Campo | C5 Hipertensão |
|---|---|
| Título | NOTA METODOLÓGICA C5 - CUIDADO DA PESSOA COM HIPERTENSÃO |
| SEI / processo | 0056042518 / 25000.137969/2025-22 |
| Assinaturas | 19/06/2026 (DEAPS), 21/06/2026 (DEPROS) |
| Revoga | SEI 0050086608 |
| sha256 do PDF | `0c5ef0ad7dfaa68245b7f8244d8026c0d48814b1cd02da79111c2c023ba64804` |
| URL (slug) | `nota-metodologica-c5-cuidado-da-pessoa-com-hipertensao` |
| Resultado | igual |

| Campo | C6 Pessoa idosa |
|---|---|
| Título | NOTA METODOLÓGICA C6 - CUIDADO DA PESSOA IDOSA |
| SEI / processo | 0056053813 / 25000.137969/2025-22 |
| Assinaturas | 19/06/2026 (DEAPS), 19/06/2026 (COPID), 19/06/2026 (DGCI) |
| Revoga | SEI 0049702803 |
| sha256 do PDF | `e9443a671627cfe8415c69ab500065cdab5196fe52ec0d87b22ef4cba561102f` |
| URL (slug) | `nota-metodologica-c6-cuidado-da-pessoa-idosa` |
| Resultado | igual |

| Campo | C7 Prevenção do câncer |
|---|---|
| Título | NOTA METODOLÓGICA C7 - CUIDADO DA MULHER NA PREVENÇÃO DO CÂNCER |
| SEI / processo | 0054641718 / 25000.137969/2025-22 |
| Assinaturas | 19/06/2026 (DEAPS, DGCI), 21/06/2026 (DEPROS), 22/06/2026 (CGSM) |
| Revoga | SEI 0049702875 |
| sha256 do PDF | `78687310444b370d25933266458f5a24cb54c30999dd2203ddb2d8e110f633c7` |
| URL (slug) | `nota-metodologica-c7-cuidado-da-mulher-na-prevencao-do-cancer` |
| Resultado | igual |

| Campo | NT 8/2026 (Componentes II e III) |
|---|---|
| Título | NOTA TÉCNICA Nº 8/2026-DEAPS/SAPS/MS |
| SEI / processo | 0055690090 / 25000.216796/2025-16 |
| Assinaturas | 29/05/2026 (DEAPS, DESF, DGCI), 01/06/2026 (DEPROS, CGIAD, SAPS) |
| Revoga | NT 6/2025-DEAPS/SAPS/MS (SEI 0052386354); revoga também dispositivos de normas citadas no texto |
| sha256 do PDF | `7d4ccc95b776608cb356c073e57126f73dbab27b449ddf0bac9dfaf5c36917b0` |
| URL | `https://sisaps.saude.gov.br/sistemas/siaps/assets/files/NT_08-2025_cvat-8638ee08a7310014262c2326c234d35a.pdf` (o nome do arquivo diz "NT_08-2025_cvat", mas o conteúdo é a NT 8/2026; `Last-Modified` do servidor: 04/09/2026, conteúdo igual ao arquivado) |
| Resultado | igual |

## Diferenças metodológicas

**Nenhuma.** Não há diferença entre o texto arquivado e o oficial atual em nenhum dos oito documentos.

## Alterações da edição vigente registradas nas notas de rodapé (já arquivadas)

São alterações da edição vigente em relação à edição revogada, já presentes no `.txt` arquivado (não são diferenças novas). Listadas como evidência de "alterações/revogações registradas" para o Portão A. Resumo por pack:

| Pack | Resumo das notas de rodapé da ficha |
|---|---|
| C1 | CBO 2251-25 (Médico Clínico) e 2252-50 (Ginecologista e Obstetra) incluídos na Seção 3 item 24-c e no quadro 01 da Seção 4. |
| C2 | Item 14 aponta a NT 30/2025-CGESCO/DESCO/SAPS/MS; quadro 2 passa a especificar o problema/condição "Puericultura"; quadro 3 inclui CBO 2232, 2234, 2236, 2238, 2237, 2241, 2239; quadro 5 (vacina) contempla registro ou transcrição de todas as doses. |
| C3 | Data de desfecho da gestação (item 17, 4.1); texto de validação de equipes (24-b); CBO 3224 TSB (24-d); regra do MIAC (24-e); novos CIAP-2/CID-10 de gestação/puerpério (24-f); SIGTAP 02.02.03.030-0 e 02.02.03.031-8 (24-h, quadro 7); novos CBO nos quadros 3, 4 e 7; ajustes de descrição (MIAC, MIVDT, MIV). 15 notas. |
| C4 | Objetivo ampliado (item 6); CBO 3224 (24-d); CID-10 E10, E11, E14 com subcódigos (24-f); quadros 3 e 4 com novos CBO e retirada do CBO 5151-05 (ACS) em aferição de PA; "todas as opções do campo Desfecho" (quadro 5); CBO 2234 (quadro 6); numeração do quadro 07 (prática F, pés). |
| C5 | Objetivo ampliado (item 6); NT 30/2025 (item 14); CBO 3224 (24-d); quadro 3 com novos CBO e retirada do 5151-05 (ACS); quadro 4 com novos CBO. |
| C6 | CBO 3224 retirado (24-d); inclusão do MIV (24-e); telefone da COPID (item 35); novos CBO no quadro 3. |
| C7 | Conceito de detecção precoce (item 5); NT 30/2025 (item 14); SIGTAP 02.02.10.025-1 (24-h e quadro 2). |
| NT 8/2026 | Referência normativa do prazo de envio (2.7); descrições invertidas de B4 e B5 corrigidas (4.3.3, quadro 3; "não alterava a metodologia de cálculo"); faixas de classificação detalhadas nos quadros 5 e 6 da Seção 5.1. |

Citação literal de C7, nota 4, relevante para AMB-C7-08: "A contabilização desse SIGTAP passou a ser realizada a partir da competência janeiro de 2026, considerando-se a janela temporal de 60 meses para fins de composição da boa prática (A)."

## Packs/AMB afetados

Nenhum pack ou AMB muda por esta conferência. As ambiguidades abertas (AMB-C2-03, AMB-C3-02, AMB-C7-06, AMB-C7-08, P07/MET-23) continuam sem resposta na ficha atual; o texto oficial coincide com o que as originou.

## Checklist do Portão A por pack

Legenda: [E] evidenciado por este documento; [T] depende da equipe/decisão humana. Nada abaixo é aprovação.

| Item do Portão A | C1 | C2 | C3 | C4 | C5 | C6 | C7 |
|---|---|---|---|---|---|---|---|
| Ficha original recuperada e arquivada | [E] PDF oficial de 06/10/2026 = `.txt` arquivado (sha256 acima). O PDF em si não é versionado; a equipe deve decidir se arquiva o binário fora do repositório | [E] idem | [E] idem | [E] idem | [E] idem | [E] idem | [E] idem |
| Edição e seções identificadas | [E] SEI 0054814890, rodapé de 2 itens | [E] SEI 0054824593, 4 itens | [E] SEI 0054619475, 15 itens | [E] SEI 0055986848, 8 itens | [E] SEI 0056042518, 5 itens | [E] SEI 0056053813, 4 itens | [E] SEI 0054641718, 4 itens |
| Competências aplicáveis confirmadas | [T] NT 8/2026 define o cálculo quadrimestral; a ficha não declara competência de início | [T] idem | [T] idem | [T] idem | [T] idem | [T] idem | [T] idem; só a regra do SIGTAP 02.02.10.025-1 cita "a partir da competência janeiro de 2026" |
| Alterações/revogações registradas | [E] revoga SEI 0050084955 | [E] revoga 0049702562 | [E] revoga 0050086461 | [E] revoga 0050086549 | [E] revoga 0050086608 | [E] revoga 0049702803 | [E] revoga 0049702875 |
| Vigência metodológica vs efeitos financeiros | [T] nenhuma ficha declara vigência (`grep` por "vigência"/"vigor": 0 ocorrências nas fichas); eficácia financeira exige confirmação separada (Tech Spec Q01 a Q07) | [T] idem | [T] idem | [T] idem (pendência já registrada em `c4-cuidado-diabetes.md`) | [T] idem (`c5-cuidado-hipertensao.md`) | [T] idem (`c6-cuidado-pessoa-idosa.md`) | [T] idem |

Pendências para a equipe: (1) confirmar competência/vigência e efeitos financeiros por indicador, junto à SAPS (fora das fichas); (2) decidir onde guardar os PDFs oficiais (apenas os sha256 e URLs estão no repositório); (3) repetir a conferência se o índice gov.br ou o SIAPS passar a publicar nova edição (SEI diferente). Não houve FAQ ou nota complementar localizada nas duas páginas consultadas.
