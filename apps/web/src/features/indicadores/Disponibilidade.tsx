import Box from '@mui/material/Box'
import Typography from '@mui/material/Typography'
import type { Disponibilidade } from '@/api/types'
import { colors } from '@/theme/tokens'

const tons: Record<Disponibilidade['situacao'], string> = {
  disponivel: colors.success,
  sem_suporte: colors.error,
  sem_fonte: colors.textSecondary,
  nao_executavel: colors.textSecondary,
}

/**
 * Whether the municipality's sources can compute a pack (ADR 0030), with the capabilities they
 * lack, as the API names them. A source without support is never a result of zero.
 */
export function DisponibilidadeTexto({
  disponibilidade,
  compacta,
}: {
  disponibilidade: Disponibilidade | null
  /** Only the label: the phone cards have no room for the capabilities. */
  compacta?: boolean
}) {
  if (!disponibilidade) {
    return <Typography sx={{ fontSize: 13.5, color: colors.textSecondary }}>—</Typography>
  }
  const faltam = disponibilidade.capacidadesFaltantes
  return (
    <Box sx={{ minWidth: 0 }}>
      <Typography sx={{ fontSize: 13.5, fontWeight: 600, color: tons[disponibilidade.situacao] }}>
        {disponibilidade.rotulo}
      </Typography>
      {!compacta && faltam.length > 0 && (
        <Typography sx={{ fontSize: 12, color: colors.textSecondary, overflowWrap: 'anywhere' }}>
          Faltam: {faltam.join(', ')}
        </Typography>
      )}
    </Box>
  )
}
