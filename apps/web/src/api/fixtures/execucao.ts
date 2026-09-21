// DEMO DATA — dados de demonstração; não usar como referência clínica ou operacional.
import type { ExecucaoAtual } from '../types'

export const execucaoFixture: ExecucaoAtual = {
  etapas: [
    {
      numero: 1,
      titulo: 'Conexão com o banco',
      status: 'concluido',
      hora: '10:12',
      descricao:
        'Estabelecendo conexão com a base de dados do e-SUS PEC (PostgreSQL). Validação de credenciais e permissão de acesso.',
    },
    {
      numero: 2,
      titulo: 'Extração de dados',
      status: 'concluido',
      hora: '10:14',
      descricao:
        'Leitura dos registros do e-SUS PEC conforme o período selecionado (Ago/2026). Dados de cadastros, atendimentos e produção.',
    },
    {
      numero: 3,
      titulo: 'Processamento',
      status: 'em_execucao',
      hora: '10:16',
      descricao:
        'Cálculo e processamento dos indicadores (C1 – C7). Validação de consistência, aplicação de regras de negócio e geração dos resultados.',
    },
    {
      numero: 4,
      titulo: 'Finalização',
      status: 'pendente',
      hora: null,
      descricao:
        'Conclusão da execução, atualização da base de indicadores e liberação dos dados para visualização no painel.',
    },
  ],
  log: [
    { hora: '10:12:01', nivel: 'success', texto: 'Iniciando execução de dados...' },
    { hora: '10:12:03', nivel: 'success', texto: 'Conectando ao servidor PostgreSQL (e-SUS PEC)...' },
    { hora: '10:12:05', nivel: 'success', texto: 'Conexão estabelecida com sucesso.' },
    { hora: '10:13:21', nivel: 'success', texto: 'Lendo dados de cadastros individuais...' },
    { hora: '10:13:48', nivel: 'success', texto: '24.852 registros de cadastros carregados.' },
    { hora: '10:14:12', nivel: 'success', texto: 'Lendo dados de atendimentos...' },
    { hora: '10:14:37', nivel: 'success', texto: '56.341 registros de atendimentos carregados.' },
    { hora: '10:15:02', nivel: 'success', texto: 'Lendo dados de produção e procedimentos...' },
    { hora: '10:15:28', nivel: 'success', texto: '18.420 registros de procedimentos carregados.' },
    { hora: '10:16:01', nivel: 'info', texto: 'Processando indicadores C1 – C7...' },
    { hora: '10:16:15', nivel: 'info', texto: 'Calculando denominadores e numeradores...' },
    { hora: '10:16:32', nivel: 'info', texto: 'Aplicando regras de negócio...' },
    { hora: '10:16:45', nivel: 'info', texto: 'Validando consistência dos resultados...' },
    { hora: '10:16:52', nivel: 'info', texto: 'Processamento em andamento...' },
  ],
  progresso: { label: 'Processando indicadores...', processados: 17, total: 25 },
  parametros: [
    { icone: 'database', label: 'Fonte de dados', valor: 'PostgreSQL - Base e-SUS PEC' },
    { icone: 'calendar', label: 'Período de referência', valor: 'Ago/2026' },
    { icone: 'clock', label: 'Duração estimada', valor: '~ 8 minutos' },
    { icone: 'file', label: 'Indicadores a processar', valor: 'C1, C2, C3, C4, C5, C6, C7' },
  ],
}
