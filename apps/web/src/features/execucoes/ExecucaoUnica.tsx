import { useRef, useState } from 'react'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Grid from '@mui/material/Grid'
import Paper from '@mui/material/Paper'
import Typography from '@mui/material/Typography'
import { useQueryClient } from '@tanstack/react-query'
import { Ban, Check, Pencil, Play, Settings } from 'lucide-react'
import { ApiError } from '@/api/client'
import {
  cancelarExecucao,
  executarIndicador,
  useExecucao,
  usePacotesIndicadores,
} from '@/api/hooks'
import { indicatorDisplayName, isRunTerminal, normalizeRunResponse } from '@/api/normalizers'
import type { IndicatorPack, RunResponse, RunSourcePeriod, RunSourceResponse } from '@/api/types'
import { formatReferencePeriod } from '@/app/display-context'
import { ExecutionStepper } from '@/components/data/ExecutionStepper'
import { LogList } from '@/components/data/LogList'
import { ProgressBar } from '@/components/data/ProgressBar'
import { StatColumns } from '@/components/data/StatColumns'
import { Callout } from '@/components/ui/Callout'
import { FilterSelect } from '@/components/ui/FilterSelect'
import { SectionCard } from '@/components/ui/SectionCard'
import { formatInt } from '@/lib/format'
import { colors } from '@/theme/tokens'

interface Parametros {
  referencePeriod?: string
  indicatorPack?: string
}

function currentMonth(): string {
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
}

/** The newest closed competência with data and no published result; else the newest with data. */
function defaultPeriod(periods: RunSourcePeriod[]): string | undefined {
  const month = currentMonth()
  return (periods.find((p) => !p.published && p.referencePeriod < month) ?? periods[0])
    ?.referencePeriod
}

function periodLabel(period: RunSourcePeriod): string {
  const published = period.published ? ' · já publicada' : ''
  return `${formatReferencePeriod(period.referencePeriod)} · ${formatInt(period.count)} atendimentos${published}`
}

function runSummary(run: RunResponse): string {
  return [
    indicatorDisplayName(run.indicatorPack),
    formatReferencePeriod(run.referencePeriod),
    run.sourceId ?? 'fonte não informada',
    `tentativa ${run.attempt} de ${run.maxAttempts}`,
  ].join(' · ')
}

function submitError(error: unknown): string {
  if (error instanceof ApiError && error.status === 403) {
    return 'Sem permissão para executar indicadores neste município.'
  }
  if (error instanceof ApiError && error.status === 400) {
    return `A execução foi recusada: ${error.message}`
  }
  return 'Não foi possível registrar a execução. Tente novamente.'
}

/**
 * "Execução única": the followed run (live, with cancel) and the parameters of the next one. A
 * submit carries one Idempotency-Key per intent — the same parameters, until one is accepted — so
 * a double click or a retry never creates a second job.
 */
export function ExecucaoUnica({
  municipalityIbge,
  fonte,
  ultima,
  podeExecutar,
  acompanhando,
  onAcompanhar,
  onVerCobertura,
}: {
  municipalityIbge: string
  fonte: RunSourceResponse
  ultima: RunResponse | null
  podeExecutar: boolean
  acompanhando: string | undefined
  onAcompanhar: (jobId: string) => void
  onVerCobertura: () => void
}) {
  const queryClient = useQueryClient()
  const pacotes = usePacotesIndicadores(podeExecutar)
  const { data: run } = useExecucao(acompanhando, ultima)
  const [parametros, setParametros] = useState<Parametros>({})
  const [editando, setEditando] = useState(false)
  const [enviando, setEnviando] = useState(false)
  const [aviso, setAviso] = useState<string | null>(null)
  const [limpas, setLimpas] = useState<{ jobId: string; linhas: number } | null>(null)
  const intencao = useRef<{ chave: string; idempotencyKey: string } | null>(null)

  // Every listed pack runs, as it does for the scheduler; one without an approved rule (ENG-34)
  // publishes a BLOCKED result with its reasons, never a value, and the form says so.
  const disponiveis: IndicatorPack[] = pacotes.data ?? []
  const referencePeriod = parametros.referencePeriod ?? defaultPeriod(fonte.periods)
  const periodo = fonte.periods.find((p) => p.referencePeriod === referencePeriod)
  const pacote = disponiveis.find((p) => p.id === parametros.indicatorPack) ?? disponiveis[0]
  const ativa = run !== undefined && !isRunTerminal(run)

  async function executar() {
    if (!referencePeriod || !pacote) return
    const request = {
      municipalityIbge,
      indicatorPack: pacote.id,
      ruleVersion: pacote.ruleVersion,
      referencePeriod,
      sourceId: fonte.sourceId,
      extractionId: null,
    }
    const chave = JSON.stringify(request)
    if (intencao.current?.chave !== chave) {
      intencao.current = { chave, idempotencyKey: crypto.randomUUID() }
    }
    setEnviando(true)
    setAviso(null)
    try {
      const { run: aceita, existente } = await executarIndicador(
        request,
        intencao.current.idempotencyKey,
      )
      intencao.current = null
      queryClient.setQueryData(['execucao', 'run', aceita.jobId], aceita)
      onAcompanhar(aceita.jobId)
      setEditando(false)
      if (existente) {
        setAviso('Esta competência já tinha uma execução em andamento; ela é mostrada acima.')
      }
      void queryClient.invalidateQueries({ queryKey: ['execucao', 'ultima'] })
    } catch (error) {
      setAviso(submitError(error))
    } finally {
      setEnviando(false)
    }
  }

  async function cancelar() {
    if (!run) return
    setAviso(null)
    try {
      queryClient.setQueryData(['execucao', 'run', run.jobId], await cancelarExecucao(run))
    } catch {
      setAviso('Não foi possível cancelar a execução. Tente novamente.')
    }
  }

  const detalhe = run ? normalizeRunResponse(run) : null
  const limpasDaExecucao = limpas && limpas.jobId === run?.jobId ? limpas.linhas : 0
  const linhas = detalhe ? detalhe.log.slice(limpasDaExecucao) : []

  return (
    <>
      <Paper sx={{ borderTopLeftRadius: 0, p: 2, mb: 2 }}>
        {run && detalhe ? (
          <Grid container spacing={2}>
            <Grid size={12}>
              <Typography sx={{ fontSize: 15, color: colors.textSecondary }}>
                {runSummary(run)}
              </Typography>
            </Grid>
            <Grid size={{ xs: 12, lg: 6.6 }}>
              <SectionCard
                title="Etapas da execução"
                sx={{ height: '100%' }}
                headerSx={{ pb: 2.5 }}
              >
                <ExecutionStepper steps={detalhe.etapas} />
              </SectionCard>
            </Grid>
            <Grid size={{ xs: 12, lg: 5.4 }}>
              <SectionCard
                title="Log da execução"
                action={
                  <Button
                    variant="outlined"
                    size="small"
                    onClick={() => setLimpas({ jobId: run.jobId, linhas: detalhe.log.length })}
                    sx={{ color: colors.navy, borderColor: colors.border, fontSize: 14 }}
                  >
                    Limpar
                  </Button>
                }
                sx={{ height: '100%' }}
                headerSx={{ pb: 2 }}
              >
                <LogList lines={linhas} />
              </SectionCard>
            </Grid>
            <Grid size={12}>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 4, pt: 0.5 }}>
                <ProgressBar
                  label={detalhe.progresso.label}
                  done={ativa ? 0 : 1}
                  total={ativa ? null : 1}
                />
                <Button
                  variant="outlined"
                  color="error"
                  size="large"
                  disabled={!ativa || run.state === 'CANCEL_REQUESTED' || !podeExecutar}
                  onClick={() => void cancelar()}
                  startIcon={<Ban size={20} />}
                  sx={{
                    borderColor: colors.error,
                    minHeight: 50,
                    px: 2.5,
                    fontSize: 15,
                    flexShrink: 0,
                  }}
                >
                  Cancelar execução
                </Button>
              </Box>
            </Grid>
          </Grid>
        ) : (
          <Typography sx={{ color: colors.textSecondary }}>
            {podeExecutar
              ? 'Nenhuma execução registrada para este município.'
              : 'As execuções do município só são visíveis para gestores com permissão de execução.'}
          </Typography>
        )}
      </Paper>

      <SectionCard
        icon={
          <Box
            sx={{
              width: 40,
              height: 40,
              borderRadius: '50%',
              bgcolor: colors.primarySoft,
              display: 'grid',
              placeItems: 'center',
            }}
          >
            <Settings size={22} strokeWidth={2.2} fill={colors.primary} color={colors.primary} />
          </Box>
        }
        title="Parâmetros da execução"
        subtitle="A próxima execução lê esta fonte, nesta competência, para este indicador."
        action={
          podeExecutar &&
          fonte.periods.length > 0 && (
            <Box sx={{ display: 'flex', gap: 1 }}>
              <Button
                variant="outlined"
                onClick={() => setEditando((e) => !e)}
                startIcon={editando ? <Check size={16} /> : <Pencil size={16} />}
                sx={{ fontSize: 14 }}
              >
                {editando ? 'Concluir' : 'Editar'}
              </Button>
              <Button
                variant="contained"
                disabled={enviando || !pacote || !referencePeriod}
                onClick={() => void executar()}
                startIcon={<Play size={16} />}
                sx={{ fontSize: 14 }}
              >
                Executar
              </Button>
            </Box>
          )
        }
      >
        {!podeExecutar ? (
          <Callout variant="info" title="Execução manual só para gestores">
            Só um gestor com permissão de execução no município pode executar indicadores. O
            agendamento automático continua funcionando na aba Agendamento.
          </Callout>
        ) : fonte.periods.length === 0 ? (
          <Callout
            variant="warning"
            title="Nenhuma competência detectada no PEC"
            action={
              <Button variant="outlined" onClick={onVerCobertura}>
                Ir para Agendamento
              </Button>
            }
          >
            {fonte.coverageOutcome === null
              ? 'A cobertura desta fonte ainda não foi verificada. Use "Verificar agora" na aba Agendamento para descobrir quais competências a base do PEC tem.'
              : 'A última verificação de cobertura não encontrou atendimentos do município nos últimos 24 meses, ou falhou. Verifique de novo na aba Agendamento.'}
          </Callout>
        ) : editando ? (
          <Box
            sx={{
              display: 'grid',
              gridTemplateColumns: { xs: '1fr', md: '1fr 1fr' },
              gap: 2,
            }}
          >
            <FilterSelect
              label="Competência"
              fullWidth
              value={periodo ? periodLabel(periodo) : ''}
              options={fonte.periods.map(periodLabel)}
              onChange={(label) =>
                setParametros((p) => ({
                  ...p,
                  referencePeriod: fonte.periods.find((x) => periodLabel(x) === label)
                    ?.referencePeriod,
                }))
              }
            />
            <FilterSelect
              label="Indicador"
              fullWidth
              value={pacote ? indicatorDisplayName(pacote.id) : ''}
              options={disponiveis.map((p) => indicatorDisplayName(p.id))}
              onChange={(label) =>
                setParametros((p) => ({
                  ...p,
                  indicatorPack: disponiveis.find((x) => indicatorDisplayName(x.id) === label)?.id,
                }))
              }
            />
          </Box>
        ) : (
          <StatColumns
            boxed
            valueSize={15}
            stats={[
              { label: 'Fonte de dados', value: `${fonte.sourceId} · PEC ${fonte.pecVersion}` },
              { label: 'Competência', value: periodo ? periodLabel(periodo) : '—' },
              { label: 'Indicador', value: pacote ? indicatorDisplayName(pacote.id) : '—' },
            ]}
          />
        )}
        {podeExecutar && fonte.periods.length > 0 && pacote && !pacote.executionEnabled && (
          <Box sx={{ mt: 1.5 }}>
            <Callout variant="warning" title="O resultado sai bloqueado">
              {indicatorDisplayName(pacote.id)} ainda não tem a regra aprovada para publicar valor
              {pacote.blockedGates.length > 0 ? ` (${pacote.blockedGates.join('; ')})` : ''}. A
              execução lê o PEC e publica o resultado como bloqueado, com esses motivos.
            </Callout>
          </Box>
        )}
        {aviso && (
          <Typography role="status" sx={{ mt: 1.5 }}>
            {aviso}
          </Typography>
        )}
      </SectionCard>
    </>
  )
}
