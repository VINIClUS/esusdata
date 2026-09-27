import { useState } from 'react'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Typography from '@mui/material/Typography'
import { useQueryClient } from '@tanstack/react-query'
import { Check, CircleAlert, CircleX, RefreshCw } from 'lucide-react'
import { useIsolamento, validarIsolamento } from '@/api/hooks'
import { isolationCheckNotice } from '@/api/normalizers'
import type { IsolamentoStatus, RegraValidacao } from '@/api/types'
import { useScope } from '@/app/scope-context'
import { formatReferencePeriod } from '@/app/display-context'
import { CheckIcon } from '@/components/data/ChecklistCard'
import { DataTable, type Column } from '@/components/data/DataTable'
import { StatColumns } from '@/components/data/StatColumns'
import { PageHeader } from '@/components/layout/PageHeader'
import { Callout } from '@/components/ui/Callout'
import { Field, PasswordField } from '@/components/ui/Inputs'
import { PageUnavailable } from '@/components/ui/PageUnavailable'
import { PageSkeleton } from '@/components/ui/PageSkeleton'
import { SectionCard } from '@/components/ui/SectionCard'
import { StatusChip } from '@/components/ui/StatusChip'
import { formatInt } from '@/lib/format'
import { colors } from '@/theme/tokens'

const TITLE = 'Isolamento Municipal'
const SUBTITLE =
  'Confira, numa competência, se a base do PEC tem atendimentos só do município da fonte.'

function RuleIcon({ resultado }: { resultado: RegraValidacao['resultado'] }) {
  if (resultado === 'conforme') return <CheckIcon size={26} />
  return (
    <Box
      sx={{
        width: 26,
        height: 26,
        borderRadius: '50%',
        bgcolor: resultado === 'atencao' ? colors.warning : colors.primary,
        color: '#fff',
        display: 'grid',
        placeItems: 'center',
        fontSize: 14,
        fontWeight: 700,
        flexShrink: 0,
      }}
    >
      {resultado === 'atencao' ? '!' : 'i'}
    </Box>
  )
}

const columns: Column<RegraValidacao>[] = [
  {
    key: 'regra',
    header: 'Regra de validação',
    render: (r) => (
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
        <RuleIcon resultado={r.resultado} />
        <Typography sx={{ fontSize: 14, fontWeight: 600, color: colors.navy }}>{r.nome}</Typography>
      </Box>
    ),
  },
  {
    key: 'desc',
    header: 'Descrição',
    render: (r) => (
      <Typography
        sx={{ fontSize: 13, color: colors.textSecondary, lineHeight: 1.4, maxWidth: 340 }}
      >
        {r.descricao}
      </Typography>
    ),
  },
  {
    key: 'resultado',
    header: 'Resultado',
    align: 'center',
    render: (r) => <StatusChip status={r.resultado} withIcon={false} />,
  },
  {
    key: 'detalhes',
    header: 'Detalhes',
    render: (r) => (
      <Typography sx={{ fontSize: 13, color: colors.textSecondary }}>{r.detalhes}</Typography>
    ),
  },
]

const bannerTones: Record<
  IsolamentoStatus['situacao'],
  { bg: string; border: string; ring: string; dot: string; icon: React.ReactNode }
> = {
  validado: {
    bg: '#f4fbf6',
    border: colors.successBorder,
    ring: '#dff5e6',
    dot: colors.success,
    icon: <Check size={32} strokeWidth={3.5} />,
  },
  atencao: {
    bg: colors.warningBg,
    border: colors.warning,
    ring: '#fdebd0',
    dot: colors.warning,
    icon: <CircleAlert size={32} strokeWidth={2.5} />,
  },
  falha: {
    bg: colors.errorBg,
    border: colors.error,
    ring: '#fbdada',
    dot: colors.error,
    icon: <CircleX size={32} strokeWidth={2.5} />,
  },
  nunca: {
    bg: colors.primarySoft,
    border: colors.infoBorder,
    ring: '#e4edfb',
    dot: colors.primary,
    icon: <RefreshCw size={30} strokeWidth={2.5} />,
  },
}

/** The month before the current one, as yyyy-MM: the latest competência likely to be closed. */
function previousMonth(): string {
  const now = new Date()
  const d = new Date(now.getFullYear(), now.getMonth() - 1, 1)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`
}

export function IsolamentoPage() {
  const { data, error, isError, isPending } = useIsolamento()
  const { referencePeriod } = useScope()
  const queryClient = useQueryClient()
  const [competencia, setCompetencia] = useState<string | null>(null)
  const [senhaAtual, setSenhaAtual] = useState('')
  const [validando, setValidando] = useState(false)
  const [erroValidacao, setErroValidacao] = useState<string | null>(null)

  if (isPending) return <PageSkeleton title={TITLE} />
  if (isError) return <PageUnavailable title={TITLE} subtitle={SUBTITLE} error={error} />

  const status = data
  const selecionada = competencia ?? status.competencia ?? referencePeriod ?? previousMonth()
  const tone = bannerTones[status.situacao]

  async function validar() {
    if (!senhaAtual || !selecionada) return
    setValidando(true)
    setErroValidacao(null)
    try {
      const resultado = await validarIsolamento(status.sourceId, senhaAtual, selecionada)
      setSenhaAtual('')
      setErroValidacao(isolationCheckNotice(resultado))
      await queryClient.invalidateQueries({ queryKey: ['fonte'] })
    } catch {
      setErroValidacao('Senha incorreta ou validação indisponível. Tente novamente.')
    } finally {
      setValidando(false)
    }
  }

  return (
    <>
      <PageHeader title={TITLE} subtitle={SUBTITLE} />

      <Box
        sx={{
          bgcolor: tone.bg,
          border: `1px solid ${tone.border}`,
          borderRadius: '14px',
          p: 2.5,
          mb: 2,
        }}
      >
        <Box sx={{ display: 'flex', gap: 3, alignItems: 'flex-start' }}>
          <Box
            sx={{
              width: 100,
              height: 100,
              borderRadius: '50%',
              bgcolor: tone.ring,
              display: 'grid',
              placeItems: 'center',
              flexShrink: 0,
            }}
          >
            <Box
              sx={{
                width: 60,
                height: 60,
                borderRadius: '50%',
                bgcolor: tone.dot,
                color: '#fff',
                display: 'grid',
                placeItems: 'center',
              }}
            >
              {tone.icon}
            </Box>
          </Box>
          <Box sx={{ flex: 1 }}>
            <Typography sx={{ fontSize: 22, fontWeight: 700, color: colors.navy }}>
              {status.titulo}
            </Typography>
            <Typography sx={{ fontSize: 15, color: colors.textSecondary, mt: 0.5 }}>
              {status.mensagem}
            </Typography>
            <Box sx={{ mt: 2 }}>
              <StatColumns
                stats={[
                  { label: 'IBGE da fonte', value: status.ibge },
                  {
                    label: 'Competência validada',
                    value: status.competencia ? formatReferencePeriod(status.competencia) : '—',
                  },
                  {
                    label: 'Atendimentos individuais do município',
                    value:
                      status.atendimentosMunicipio === null
                        ? '—'
                        : formatInt(status.atendimentosMunicipio),
                  },
                  {
                    label: 'Última validação',
                    value: status.ultimaValidacao
                      ? new Date(status.ultimaValidacao).toLocaleString('pt-BR')
                      : '—',
                  },
                ]}
              />
            </Box>
          </Box>
        </Box>
        <Box
          component="form"
          onSubmit={(e) => {
            e.preventDefault()
            void validar()
          }}
          sx={{
            display: 'grid',
            gridTemplateColumns: { xs: '1fr', md: '180px 1fr auto' },
            gap: 2.5,
            alignItems: 'end',
            mt: 2.5,
          }}
        >
          <Field
            label="Competência"
            type="month"
            value={selecionada}
            onChange={(e) => setCompetencia(e.target.value)}
          />
          <PasswordField
            label="Senha da sua conta Esusdata"
            value={senhaAtual}
            onChange={(e) => setSenhaAtual(e.target.value)}
            autoComplete="current-password"
          />
          <Button
            type="submit"
            variant="outlined"
            size="large"
            disabled={validando || !senhaAtual || !selecionada}
            startIcon={<RefreshCw size={22} />}
            sx={{ minHeight: 50, fontSize: 17, fontWeight: 500, borderWidth: 1.5 }}
          >
            {status.situacao === 'nunca' ? 'Validar' : 'Validar novamente'}
          </Button>
        </Box>
        {erroValidacao && (
          <Typography role="alert" color="error" sx={{ mt: 1.5 }}>
            {erroValidacao}
          </Typography>
        )}
        <Box sx={{ mt: 2.5 }}>
          <Callout variant="info" title="Importante">
            A validação conta os atendimentos individuais da competência por código IBGE, sem ler
            nenhum registro. Não avalia a qualidade, a completude ou a consistência clínica dos
            dados, nem equipes, CNES ou território.
          </Callout>
        </Box>
      </Box>

      <SectionCard
        title="Regras de validação do recorte"
        subtitle="O que a última validação mostrou sobre a competência validada."
        sx={{ mb: 2 }}
      >
        <DataTable
          columns={columns}
          rows={status.regras}
          getRowKey={(r) => r.nome}
          bordered
          sx={{ '& th': { fontSize: 13.5, py: 1 }, '& td': { py: 0.9 } }}
        />
      </SectionCard>

      <Callout variant="info" iconStyle="filled" title="Sobre o isolamento municipal">
        Os indicadores usam apenas os atendimentos cujo código IBGE é o da fonte: a consulta de
        extração filtra por ele. Esta validação mostra o que mais existe na base do PEC na
        competência escolhida.
      </Callout>
    </>
  )
}
