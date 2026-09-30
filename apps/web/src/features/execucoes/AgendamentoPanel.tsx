import { useState } from 'react'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Typography from '@mui/material/Typography'
import { useQueryClient } from '@tanstack/react-query'
import { CalendarClock, Pause, Play, RefreshCw } from 'lucide-react'
import { alterarAgendamento, verificarAgora } from '@/api/hooks'
import { failureReason } from '@/api/normalizers'
import type { RunSchedule, RunSourceResponse, ScheduleOutcome } from '@/api/types'
import { formatReferencePeriod } from '@/app/display-context'
import { StatColumns } from '@/components/data/StatColumns'
import { Callout } from '@/components/ui/Callout'
import { PasswordField } from '@/components/ui/Inputs'
import { SectionCard } from '@/components/ui/SectionCard'

const outcomeLabels: Record<ScheduleOutcome, string> = {
  ENQUEUED: 'Execução enfileirada',
  UP_TO_DATE: 'Tudo em dia',
  JOB_ACTIVE: 'Já havia uma execução ativa',
  NO_MANAGER: 'Nenhum gestor habilitado no município',
  COVERAGE_FAILED: 'Falha ao verificar a cobertura do PEC',
  SOURCE_BUSY: 'Fonte ocupada; nova tentativa no próximo ciclo',
  DISABLED: 'Agendamento pausado nesta fonte',
}

function dateTime(value: string | null): string {
  return value ? new Date(value).toLocaleString('pt-BR') : '—'
}

function lastOutcome(schedule: RunSchedule): string {
  if (!schedule.lastOutcome) return 'Nenhuma verificação ainda'
  const label = outcomeLabels[schedule.lastOutcome]
  return schedule.lastPeriod ? `${label} (${formatReferencePeriod(schedule.lastPeriod)})` : label
}

/**
 * The source's scheduler (ADR 0028): what it last did, when it runs next, and the two actions —
 * pause/resume and "Verificar agora" — both behind a reauthentication, like the source checks.
 */
export function AgendamentoPanel({
  fonte,
  onEnfileirada,
}: {
  fonte: RunSourceResponse
  /** Called with the job a "Verificar agora" enqueued, so the page can follow it. */
  onEnfileirada: (jobId: string) => void
}) {
  const queryClient = useQueryClient()
  const [senhaAtual, setSenhaAtual] = useState('')
  const [enviando, setEnviando] = useState(false)
  const [aviso, setAviso] = useState<string | null>(null)
  const { schedule } = fonte

  async function agir(acao: 'alternar' | 'verificar') {
    if (!senhaAtual) return
    setEnviando(true)
    setAviso(null)
    try {
      const atualizado =
        acao === 'alternar'
          ? await alterarAgendamento(fonte.sourceId, !schedule.enabled, senhaAtual)
          : await verificarAgora(fonte.sourceId, senhaAtual)
      setSenhaAtual('')
      if (acao === 'verificar') setAviso(`Verificação concluída: ${lastOutcome(atualizado)}.`)
      else setAviso(atualizado.enabled ? 'Agendamento ativado.' : 'Agendamento pausado.')
      await queryClient.invalidateQueries({ queryKey: ['execucao', 'fontes'] })
      if (acao === 'verificar' && atualizado.lastOutcome === 'ENQUEUED' && atualizado.lastJobId) {
        onEnfileirada(atualizado.lastJobId)
      }
    } catch {
      setAviso('Senha incorreta ou agendamento indisponível. Tente novamente.')
    } finally {
      setEnviando(false)
    }
  }

  return (
    <SectionCard
      icon={<CalendarClock size={24} />}
      title="Agendamento automático"
      subtitle={`Fonte ${fonte.sourceId} · PEC ${fonte.pecVersion}`}
    >
      {!schedule.schedulerEnabled && (
        <Box sx={{ mb: 2 }}>
          <Callout variant="warning" title="Agendador desligado nesta instalação">
            Nada é executado automaticamente até que o administrador ligue{' '}
            <code>observatorio.scheduler.enabled</code>. &quot;Verificar agora&quot; continua
            funcionando.
          </Callout>
        </Box>
      )}
      <StatColumns
        boxed
        valueSize={15}
        stats={[
          { label: 'Nesta fonte', value: schedule.enabled ? 'Ativo' : 'Pausado' },
          { label: 'Frequência', value: `A cada ${schedule.intervalHours} h` },
          { label: 'Próxima verificação', value: dateTime(schedule.nextTickAt) },
          { label: 'Última verificação', value: dateTime(schedule.lastTickAt) },
          { label: 'Último resultado', value: lastOutcome(schedule) },
        ]}
      />
      {schedule.lastDetail && (
        <Typography sx={{ mt: 1.5, fontSize: 14 }} color="text.secondary">
          {failureReason(schedule.lastDetail)}
        </Typography>
      )}

      <Box
        component="form"
        onSubmit={(e) => {
          e.preventDefault()
          void agir('verificar')
        }}
        sx={{
          display: 'grid',
          gridTemplateColumns: { xs: '1fr', md: '1fr auto auto' },
          gap: 2,
          alignItems: 'end',
          mt: 2.5,
        }}
      >
        <PasswordField
          label="Senha da sua conta Esusdata"
          value={senhaAtual}
          onChange={(e) => setSenhaAtual(e.target.value)}
          autoComplete="current-password"
        />
        <Button
          variant="outlined"
          size="large"
          disabled={enviando || !senhaAtual}
          onClick={() => void agir('alternar')}
          startIcon={schedule.enabled ? <Pause size={20} /> : <Play size={20} />}
          sx={{ minHeight: 50 }}
        >
          {schedule.enabled ? 'Pausar agendamento' : 'Ativar agendamento'}
        </Button>
        <Button
          type="submit"
          variant="contained"
          size="large"
          disabled={enviando || !senhaAtual}
          startIcon={<RefreshCw size={20} />}
          sx={{ minHeight: 50 }}
        >
          Verificar agora
        </Button>
      </Box>
      {aviso && (
        <Typography role="status" sx={{ mt: 1.5 }}>
          {aviso}
        </Typography>
      )}

      <Box sx={{ mt: 2.5 }}>
        <Callout variant="info" title="Como funciona">
          A cada {schedule.intervalHours} horas o agendador confere quais competências a base do PEC
          tem para o município e enfileira no máximo uma execução por vez: a competência fechada
          mais antiga — a partir do dia {schedule.settleDays} do mês seguinte — que tem atendimentos
          e ainda não tem resultado publicado. Uma competência que falhou só é tentada de novo 24
          horas depois. As execuções usam o primeiro gestor habilitado do município.
        </Callout>
      </Box>
    </SectionCard>
  )
}
