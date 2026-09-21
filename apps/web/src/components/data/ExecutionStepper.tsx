import Box from '@mui/material/Box'
import Typography from '@mui/material/Typography'
import type { EtapaExecucao } from '@/api/types'
import { colors } from '@/theme/tokens'
import { StatusChip } from '@/components/ui/StatusChip'

const circle = {
  concluido: { bg: colors.success, color: '#fff' },
  em_execucao: { bg: colors.primary, color: '#fff' },
  pendente: { bg: '#e6ecf5', color: colors.textSecondary },
}

export function ExecutionStepper({ steps }: { steps: EtapaExecucao[] }) {
  return (
    <Box sx={{ display: 'flex', flexDirection: 'column' }}>
      {steps.map((step, i) => {
        const c = circle[step.status]
        const last = i === steps.length - 1
        const lineColor =
          step.status === 'concluido'
            ? colors.success
            : step.status === 'em_execucao'
              ? colors.border
              : colors.border
        return (
          <Box key={step.numero} sx={{ display: 'flex', gap: 2.5, pb: last ? 0 : 3 }}>
            <Box
              sx={{ display: 'flex', flexDirection: 'column', alignItems: 'center', flexShrink: 0 }}
            >
              <Box
                sx={{
                  width: 42,
                  height: 42,
                  borderRadius: '50%',
                  bgcolor: c.bg,
                  color: c.color,
                  display: 'grid',
                  placeItems: 'center',
                  fontSize: 18,
                  fontWeight: 700,
                  boxShadow:
                    step.status === 'em_execucao' ? `0 0 0 6px ${colors.primarySoft}` : 'none',
                }}
              >
                {step.numero}
              </Box>
              {!last && (
                <Box sx={{ width: 3, flex: 1, bgcolor: lineColor, mt: 1, borderRadius: 2 }} />
              )}
            </Box>
            <Box sx={{ flex: 1, minWidth: 0, pt: 0.75 }}>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, flexWrap: 'wrap' }}>
                <Typography sx={{ fontSize: 17, fontWeight: 700, color: colors.navy }}>
                  {step.titulo}
                </Typography>
                <StatusChip status={step.status} withIcon={false} size="sm" />
                <Box sx={{ flex: 1 }} />
                {step.hora && (
                  <Typography sx={{ fontSize: 15, color: colors.textSecondary }}>
                    {step.hora}
                  </Typography>
                )}
              </Box>
              <Typography
                sx={{ fontSize: 14.5, color: colors.textSecondary, lineHeight: 1.55, mt: 0.75 }}
              >
                {step.descricao}
              </Typography>
            </Box>
          </Box>
        )
      })}
    </Box>
  )
}
