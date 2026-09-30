// DEMO DATA — dados de demonstração; não usar como referência clínica ou operacional.
import type {
  Alerta,
  IndicadorPendencia,
  Kpi,
  OverviewCheck,
  OverviewResponse,
  PainelResumo,
} from '../types'

const kpi = (
  id: Kpi['icone'],
  label: string,
  valor: string,
  [chipLabel, chipValor]: [string, string],
  variacao: string,
  tom: 'up' | 'down',
): Kpi => ({
  id,
  icone: id,
  label,
  valor,
  chip: { label: chipLabel, valor: chipValor },
  tendencia: { texto: `${variacao} em relação ao mês anterior`, tom },
})

const alerta = (
  id: string,
  severidade: Alerta['severidade'],
  titulo: string,
  descricao: string,
  data: string,
  hora: string,
): Alerta => ({ id, severidade, titulo, descricao, data, hora })

const pendencia = (
  codigo: string,
  indicador: string,
  pendencias: number,
  situacao: string,
  status: IndicadorPendencia['status'],
): IndicadorPendencia => ({
  codigo,
  indicador,
  motivo: `${String(pendencias)} pendências · ${situacao}`,
  status,
})

const check = (
  code: OverviewCheck['code'],
  sourceId: string | null,
  at: string | null,
  referencePeriod: string | null,
): OverviewCheck => ({ code, sourceId, status: 'OK', at, referencePeriod })

export const painelFixture: PainelResumo = {
  kpis: [
    kpi('indicadores', 'Indicadores calculados', '8 / 27', ['', '30%'], '+2', 'up'),
    kpi('cobertura', 'Cobertura da população', '76,3%', ['Meta', '≥ 70%'], '+1,2 p.p.', 'up'),
    kpi('cadastros', 'Cadastros ativos', '28.452', ['Total', '28.452'], '+2,1%', 'up'),
    {
      ...kpi('pendencias', 'Pendências de dados', '124', ['Meta', '≤ 100'], '-30%', 'down'),
      tomValor: 'error',
    },
  ],
  evolucao: {
    series: [
      { key: 'cobertura', label: 'Cobertura da população', cor: '#1a6ef5' },
      { key: 'cadastros', label: 'Cadastros ativos', cor: '#16a34a' },
      { key: 'hipertensos', label: 'Acompanhamento de hipertensos', cor: '#f59e0b' },
      { key: 'diabeticos', label: 'Acompanhamento de diabéticos', cor: '#8b5cf6' },
    ],
    pontos: [
      { mes: 'Jan', cobertura: 63, cadastros: 42, hipertensos: 30, diabeticos: 20 },
      { mes: 'Fev', cobertura: 67, cadastros: 47, hipertensos: 34, diabeticos: 23 },
      { mes: 'Mar', cobertura: 68, cadastros: 51, hipertensos: 38, diabeticos: 27 },
      { mes: 'Abr', cobertura: 71, cadastros: 56, hipertensos: 41, diabeticos: 30 },
      { mes: 'Mai', cobertura: 74, cadastros: 60, hipertensos: 46, diabeticos: 33 },
      { mes: 'Jun', cobertura: 76, cadastros: 63, hipertensos: 48, diabeticos: 35 },
      { mes: 'Jul', cobertura: 79, cadastros: 66, hipertensos: 51, diabeticos: 36 },
      { mes: 'Ago', cobertura: 82, cadastros: 69, hipertensos: 54, diabeticos: 38 },
    ],
  },
  qualidade: {
    percentual: 92,
    titulo: 'Dados consistentes',
    descricao: 'A qualidade dos dados está em um nível excelente.',
  },
  integridade: [
    { label: 'Cadastros válidos', valor: '98,7%', ok: true },
    { label: 'Equipes completas', valor: '100%', ok: true },
    { label: 'Território configurado', valor: '100%', ok: true },
    { label: 'Sem duplicidades críticas', valor: '99,2%', ok: true },
  ],
  alertas: [
    alerta(
      'a1',
      'error',
      'Pendência de dados identificada',
      '124 fichas com inconsistências no cadastro',
      'Hoje',
      '10:05',
    ),
    alerta(
      'a2',
      'warning',
      'Cobertura abaixo da meta',
      'A cobertura da população está em 76,3% (meta: 80%).',
      '15/08',
      '14:32',
    ),
    alerta(
      'a3',
      'info',
      'Execução de dados concluída',
      'Processamento de Ago/2026 finalizado com sucesso.',
      '15/08',
      '10:18',
    ),
    alerta(
      'a4',
      'warning',
      'Possíveis duplicidades',
      '12 cadastros com possível duplicidade identificados.',
      '14/08',
      '16:45',
    ),
    alerta(
      'a5',
      'info',
      'Nova versão disponível',
      'Versão 0.4.0 com melhorias e correções.',
      '12/08',
      '09:20',
    ),
  ],
  maiorPendencia: [
    pendencia('PB-01', 'Pré-natal adequado', 86, 'Abaixo da meta na competência', 'critico'),
    pendencia(
      'PB-02',
      'Cobertura de exame citopatológico',
      64,
      'Abaixo da meta na competência',
      'critico',
    ),
    pendencia('PB-03', 'Acompanhamento de diabéticos', 42, 'Perto do limite da meta', 'atencao'),
    pendencia('PB-04', 'Acompanhamento de hipertensos', 38, 'Perto do limite da meta', 'atencao'),
    pendencia('PB-05', 'Vacinação em menores de 1 ano', 26, 'Perto do limite da meta', 'atencao'),
    pendencia('PB-06', 'Consulta odontológica', 18, 'Dentro da meta', 'regular'),
    pendencia('PB-07', 'Hipertensão com PA controlada', 12, 'Dentro da meta', 'regular'),
    pendencia('PB-08', 'Diabetes com HbA1c solicitada', 8, 'Dentro da meta', 'regular'),
  ],
  ultimasExecucoes: [
    ['16/08/2026 10:05', 'Ago/2026'],
    ['10/08/2026 16:32', 'Jul/2026'],
    ['12/07/2026 11:26', 'Jun/2026'],
    ['15/06/2026 09:14', 'Mai/2026'],
    ['13/05/2026 14:22', 'Abr/2026'],
    ['14/04/2026 10:03', 'Mar/2026'],
    ['12/03/2026 16:40', 'Fev/2026'],
    ['16/02/2026 11:18', 'Jan/2026'],
  ].map(([dataHora = '', competencia = ''], i) => ({
    jobId: `job-demo-${String(i + 1)}`,
    dataHora,
    competencia,
    status: 'concluida' as const,
  })),
}

export const overviewFixture: OverviewResponse = {
  municipalityIbge: '3538704',
  referencePeriod: '2026-08',
  lastUpdate: '2026-09-19T13:14:10Z',
  indicators: [
    {
      indicatorPack: 'c1-mais-acesso',
      ruleVersion: '1.0.0',
      family: 'C1',
      unit: 'PERCENT',
      executionEnabled: true,
      blockedGates: [],
      resultId: 'r-demo',
      status: 'BLOCKED',
      value: null,
      limitations: ['Portão A (fonte e vigência) incompleto'],
      publishedAt: '2026-09-19T13:14:10Z',
    },
  ],
  history: [
    {
      referencePeriod: '2026-06',
      indicatorPack: 'c1-mais-acesso',
      status: 'COMPUTED',
      value: '58.2',
    },
    {
      referencePeriod: '2026-07',
      indicatorPack: 'c1-mais-acesso',
      status: 'COMPUTED',
      value: '61.4',
    },
    { referencePeriod: '2026-08', indicatorPack: 'c1-mais-acesso', status: 'BLOCKED', value: null },
  ],
  quality: { published: 1, completeSnapshot: 1 },
  checks: [
    check('SOURCE_CONNECTION', 'pec-demo', '2026-09-19T13:00:00Z', null),
    check('MUNICIPAL_ISOLATION', 'pec-demo', '2026-09-19T13:05:00Z', '2026-08'),
    check('PEC_COVERAGE', 'pec-demo', '2026-09-19T13:05:00Z', null),
    check('SCHEDULER', 'pec-demo', '2026-09-19T13:05:00Z', '2026-08'),
    check('RESULTS_PUBLISHED', null, null, '2026-08'),
  ],
  alerts: [
    {
      code: 'RESULT_BLOCKED',
      severity: 'WARNING',
      subject: 'c1-mais-acesso',
      referencePeriod: '2026-08',
      sourceId: null,
      detail: null,
      at: null,
    },
    {
      code: 'PENDING_PERIODS',
      severity: 'INFO',
      subject: null,
      referencePeriod: '2026-09',
      sourceId: 'pec-demo',
      detail: '1',
      at: null,
    },
  ],
  pendingPeriods: [{ sourceId: 'pec-demo', referencePeriod: '2026-09', count: 3120 }],
  recentRuns: [
    {
      jobId: 'job-demo',
      indicatorPack: 'c1-mais-acesso',
      referencePeriod: '2026-08',
      state: 'SUCCEEDED',
      createdAt: '2026-09-19T13:12:00Z',
      finishedAt: '2026-09-19T13:14:10Z',
      failureCode: null,
    },
  ],
}
