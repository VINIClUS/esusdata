# Screenshots da UI (apps/web) e comparação com as referências

Geradas com `cd apps/web && npm run dev` + `npm run screenshot` (Playwright headless, viewport 1448×1086 no desktop, 1024×768 no tablet, 390×844 no celular). Os dados exibidos vêm de fixtures de demonstração (`VITE_USE_MOCKS`, padrão ligado).

| Referência (`docs/ChatGPT Image … (N).png`) | Screenshot gerada | Rota |
|---|---|---|
| (1) Painel Principal | `01-painel.png` | `/painel` |
| (2) Login | `02-login.png` | `/login` |
| (3) Indicadores | `03-indicadores.png` | `/indicadores` |
| (4) Detalhe PB-01 | `04-indicador-detalhe.png` | `/indicadores/PB-01` |
| (5) Execução de Dados | `05-execucao.png` | `/execucao` |
| (6) Configuração da Fonte de Dados | `06-fonte-de-dados.png` | `/configuracoes` (alias `/base-de-dados`) |
| (7) Relatórios | `07-relatorios.png` | `/relatorios` |
| (8) Isolamento Municipal | `08-isolamento-municipal.png` | `/configuracoes/isolamento-municipal` |
| (9) Versão responsiva | `09-tablet-painel.png`, `09-phone-indicadores.png`, `09-phone-detalhe.png` | `/painel`, `/indicadores`, `/indicadores/PB-01` |

## Diferenças conhecidas em relação às referências

- **Tipografia e ícones**: as referências foram geradas por IA com fontes e glifos não identificáveis. A implementação usa Inter e ícones Lucide, então pesos e proporções de letras diferem levemente.
- **Painel (1)**: a terceira linha (alertas, pendências, execuções) fica alguns pixels mais alta que na referência e o final do card "Alertas recentes" sai da dobra em 1086 px. A página rola normalmente.
- **Detalhe (4)**: o card "Informações adicionais" fica abaixo da dobra em 1086 px; na referência ele cabe porque o texto é menor.
- **Fonte de Dados (6)**: a terceira aba "Isolamento Municipal" navega para a tela 8 em vez de trocar o conteúdo na mesma página; os cards "Requisitos" e "Segurança" são ligeiramente mais compactos.
- **Relatórios (7)**: a ilustração do card "Sobre o Relatório" é um SVG simples inline, não a ilustração da referência.
- **Responsivo (9)**: no tablet, o card "Verificações de integridade" vai para a linha seguinte (a referência não o mostra). No celular, a lista de indicadores usa cards com código, nome, valor, chip de status e seta, como na referência.
- **Barra de progresso (5)**: o percentual (68%) é calculado de `processados/total` da fixture, não é decorativo.
