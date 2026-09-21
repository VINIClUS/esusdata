// DEMO DATA — dados de demonstração; não usar como referência clínica ou operacional.
import type { IsolamentoStatus } from '../types'

export const isolamentoFixture: IsolamentoStatus = {
  municipio: 'Presidente Epitácio',
  municipioUf: 'Presidente Epitácio/SP',
  ibge: '3538704',
  totalCadastros: 28452,
  ultimaValidacao: '19/09/2026 10:05',
  regras: [
    {
      nome: 'Escopo municipal',
      descricao: 'Todos os cadastros pertencem ao município selecionado (código IBGE 3538704).',
      resultado: 'conforme',
      detalhes: '28.452 registros verificados',
    },
    {
      nome: 'Equipes mapeadas',
      descricao: 'Todas as equipes estão vinculadas ao município e ativas no CNES.',
      resultado: 'conforme',
      detalhes: '12 equipes identificadas',
    },
    {
      nome: 'Consistência CNES/INE',
      descricao: 'Unidades e equipes conferidas com bases oficiais (CNES e INE).',
      resultado: 'conforme',
      detalhes: '0 inconsistências encontradas',
    },
    {
      nome: 'Sem registros de outros municípios',
      descricao: 'Não foram identificados cadastros de outros municípios na base atual.',
      resultado: 'conforme',
      detalhes: '0 registros fora do município',
    },
    {
      nome: 'Período de competência',
      descricao: 'Dados referentes à competência selecionada (Ago/2026).',
      resultado: 'verificado',
      detalhes: 'Competência: Ago/2026',
    },
    {
      nome: 'Território de atuação',
      descricao: 'Território municipal configurado conforme parametrização atual.',
      resultado: 'verificado',
      detalhes: 'Território único',
    },
  ],
}
