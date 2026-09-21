// DEMO DATA — dados de demonstração; não usar como referência clínica ou operacional.
import type { IndicadorDetalhe, IndicadorResumo, IndicadoresLista } from '../types'

const itens: IndicadorResumo[] = [
  { codigo: 'PB-01', nome: 'Pré-natal adequado', categoria: 'Previne Brasil', status: 'concluido', ultimaExecucao: '16/08/2026 10:05', resultado: 78.4 },
  { codigo: 'PB-02', nome: 'Cobertura vacinal (Pentavalente)', categoria: 'Previne Brasil', status: 'concluido', ultimaExecucao: '10/08/2026 14:32', resultado: 92.1 },
  { codigo: 'PB-03', nome: 'Citopatológico em mulheres 25–64 anos', categoria: 'Previne Brasil', status: 'concluido', ultimaExecucao: '16/08/2026 10:13', resultado: 86.7 },
  { codigo: 'PB-04', nome: 'Hipertensos acompanhados', categoria: 'Previne Brasil', status: 'concluido', ultimaExecucao: '12/08/2026 11:26', resultado: 71.3 },
  { codigo: 'PB-05', nome: 'Diabéticos acompanhados', categoria: 'Previne Brasil', status: 'em_execucao', ultimaExecucao: '16/08/2026 09:14', resultado: 68.8 },
  { codigo: 'C1-01', nome: 'Mais acesso à consulta', categoria: 'C1 – C7', status: 'concluido', ultimaExecucao: '10/08/2026 16:10', resultado: null },
  { codigo: 'C2-01', nome: 'Vínculo e continuidade do cuidado', categoria: 'C1 – C7', status: 'pendente', ultimaExecucao: null, resultado: null },
  { codigo: 'C4-01', nome: 'Diabetes acompanhados', categoria: 'C1 – C7', status: 'concluido', ultimaExecucao: '14/08/2026 10:03', resultado: 85.1 },
  { codigo: 'IGM-01', nome: 'Cobertura de cadastros', categoria: 'IGM (Municipal)', status: 'concluido', ultimaExecucao: '16/08/2026 10:05', resultado: 98.7 },
  { codigo: 'IGM-02', nome: 'Qualidade dos dados', categoria: 'IGM (Municipal)', status: 'em_execucao_info', ultimaExecucao: '15/08/2026 16:40', resultado: 91.2 },
  { codigo: 'PB-06', nome: 'Cobertura de exame citopatológico', categoria: 'Previne Brasil', status: 'pendente', ultimaExecucao: null, resultado: null },
  { codigo: 'PB-07', nome: 'Vacinação em menores de 1 ano', categoria: 'Previne Brasil', status: 'concluido', ultimaExecucao: '16/08/2026 10:20', resultado: 58.6 },
  { codigo: 'C3-01', nome: 'Acompanhamento de hipertensos', categoria: 'C1 – C7', status: 'concluido', ultimaExecucao: '16/08/2026 10:22', resultado: 71.2 },
  { codigo: 'C5-01', nome: 'Acompanhamento de diabéticos', categoria: 'C1 – C7', status: 'atencao', ultimaExecucao: '16/08/2026 10:24', resultado: 67.3 },
  { codigo: 'C6-01', nome: 'Consulta odontológica', categoria: 'C1 – C7', status: 'concluido', ultimaExecucao: '16/08/2026 10:25', resultado: 82.1 },
  { codigo: 'C7-01', nome: 'Hipertensão com PA controlada', categoria: 'C1 – C7', status: 'pendente', ultimaExecucao: null, resultado: null },
  { codigo: 'VA-01', nome: 'Gestantes com vínculo de equipe', categoria: 'Vínculo / Acompanhamento', status: 'concluido', ultimaExecucao: '15/08/2026 09:40', resultado: 88.9 },
  { codigo: 'VA-02', nome: 'Crianças até 2 anos acompanhadas', categoria: 'Vínculo / Acompanhamento', status: 'concluido', ultimaExecucao: '15/08/2026 09:42', resultado: 76.5 },
  { codigo: 'VA-03', nome: 'Idosos com visita domiciliar', categoria: 'Vínculo / Acompanhamento', status: 'pendente', ultimaExecucao: null, resultado: null },
  { codigo: 'VA-04', nome: 'Pessoas com deficiência acompanhadas', categoria: 'Vínculo / Acompanhamento', status: 'pendente', ultimaExecucao: null, resultado: null },
  { codigo: 'VA-05', nome: 'Puérperas com consulta em 42 dias', categoria: 'Vínculo / Acompanhamento', status: 'concluido', ultimaExecucao: '15/08/2026 09:45', resultado: 64.2 },
  { codigo: 'IGM-03', nome: 'Territórios sem cobertura', categoria: 'IGM (Municipal)', status: 'concluido', ultimaExecucao: '15/08/2026 16:41', resultado: 3.1 },
  { codigo: 'IGM-04', nome: 'Equipes completas', categoria: 'IGM (Municipal)', status: 'concluido', ultimaExecucao: '15/08/2026 16:42', resultado: 100 },
  { codigo: 'IGM-05', nome: 'Duplicidades de cadastro', categoria: 'IGM (Municipal)', status: 'concluido', ultimaExecucao: '15/08/2026 16:43', resultado: 0.8 },
  { codigo: 'IGM-06', nome: 'Cadastros sem CPF/CNS', categoria: 'IGM (Municipal)', status: 'pendente', ultimaExecucao: null, resultado: null },
  { codigo: 'IGM-07', nome: 'Atendimentos sem CID/CIAP', categoria: 'IGM (Municipal)', status: 'pendente', ultimaExecucao: null, resultado: null },
  { codigo: 'IGM-08', nome: 'Fichas com inconsistência', categoria: 'IGM (Municipal)', status: 'pendente', ultimaExecucao: null, resultado: null },
]

export const indicadoresFixture: IndicadoresLista = {
  categorias: [
    { key: 'todos', label: 'Todos', total: 27 },
    { key: 'previne', label: 'Previne Brasil / ISF', total: 7 },
    { key: 'c1c7', label: 'C1 – C7', total: 7 },
    { key: 'vinculo', label: 'Vínculo / Acompanhamento', total: 5 },
    { key: 'igm', label: 'IGM', total: 11 },
  ],
  itens,
  total: 27,
}

export const indicadorDetalheFixture: IndicadorDetalhe = {
  codigo: 'PB-01',
  nome: 'Pré-natal adequado',
  status: 'concluido',
  descricao:
    'Proporção de gestantes com 6 ou mais consultas de pré-natal, sendo a primeira até a 12ª semana.',
  componente: 'Pré-natal (Saúde da Mulher)',
  tipo: 'Indicador do e-SUS PEC',
  ultimaExecucao: '16/08/2026 às 10:05',
  resultado: { valor: 78.4, meta: '≥ 70%', tendencia: '+2,7 p.p. em relação ao mês anterior' },
  numerador: { valor: 312, label: 'Gestantes com 6+ consultas' },
  denominador: { valor: 398, label: 'Total de gestantes identificadas' },
  pendencias: { valor: 86, percentual: 21.6 },
  meta: 70,
  evolucao: [
    { mes: 'Set', valor: 49 },
    { mes: 'Out', valor: 53 },
    { mes: 'Nov', valor: 57 },
    { mes: 'Dez', valor: 60 },
    { mes: 'Jan', valor: 63 },
    { mes: 'Fev', valor: 66 },
    { mes: 'Mar', valor: 69 },
    { mes: 'Abr', valor: 72 },
    { mes: 'Mai', valor: 74 },
    { mes: 'Jun', valor: 76 },
    { mes: 'Jul', valor: 77 },
    { mes: 'Ago', valor: 78.4 },
  ],
  distribuicao: [
    { label: 'Adequado', valor: 312, percentual: 78.4, tom: 'success' },
    { label: 'Em acompanhamento', valor: 58, percentual: 14.6, tom: 'warning' },
    { label: 'Pendências', valor: 86, percentual: 21.6, tom: 'error' },
  ],
  metodologia: [
    {
      icone: 'target',
      titulo: 'O que mede?',
      texto:
        'Proporção de gestantes com 6 ou mais consultas de pré-natal, sendo a primeira até a 12ª semana de gestação.',
    },
    {
      icone: 'sigma',
      titulo: 'Numerador',
      texto:
        'Número de gestantes com 6 ou mais consultas de pré-natal, com a primeira consulta realizada até a 12ª semana.',
    },
    { icone: 'users', titulo: 'Denominador', texto: 'Total de gestantes identificadas no período.' },
    {
      icone: 'database',
      titulo: 'Fonte de dados',
      texto:
        'Registros de atendimento individual (e-SUS PEC): fichas de pré-natal (CDS), atendimentos individuais e cadastro de gestantes.',
    },
    {
      icone: 'file',
      titulo: 'Referência',
      texto: 'Manual de Indicadores da APS (e-SUS PEC) – Ministério da Saúde.',
    },
  ],
  evidencias: [
    { motivo: 'Menos de 6 consultas registradas', quantidade: 52, percentual: 60.5, acao: 'Realizar busca ativa' },
    { motivo: 'Primeira consulta após 12ª semana', quantidade: 18, percentual: 20.9, acao: 'Qualificar captação precoce' },
    { motivo: 'Sem registro de data da 1ª consulta', quantidade: 9, percentual: 10.5, acao: 'Revisar registro no sistema' },
    { motivo: 'Gestante sem encerramento de gravidez', quantidade: 7, percentual: 8.1, acao: 'Atualizar desfecho da gestação' },
  ],
  infoAdicionais: [
    { icone: 'calendar', label: 'Período de análise', valor: 'Agosto/2026' },
    { icone: 'building', label: 'Município', valor: 'Presidente Epitácio/SP' },
    { icone: 'refresh', label: 'Última atualização dos dados', valor: '16/08/2026 10:05' },
    { icone: 'users', label: 'Equipe', valor: 'Todas as equipes' },
    { icone: 'user', label: 'Responsável pela execução', valor: 'Vinícius Santos' },
    { icone: 'file', label: 'Versão da regra', valor: 'v1.2 (2024)' },
  ],
}
