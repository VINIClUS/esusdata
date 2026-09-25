import Box from '@mui/material/Box'
import { CircleCheck, CircleDot, CircleX, Info, type LucideIcon } from 'lucide-react'
import type { StatusKey } from '@/api/types'
import { colors } from '@/theme/tokens'

interface StatusDef {
  label: string
  color: string
  bg: string
  icon?: LucideIcon
}

const map: Record<StatusKey, StatusDef> = {
  concluido: { label: 'Concluído', color: colors.success, bg: colors.successBg, icon: CircleCheck },
  calculado: { label: 'Calculado', color: colors.success, bg: colors.successBg },
  em_execucao: { label: 'Em execução', color: '#e0850a', bg: colors.warningBg, icon: CircleDot },
  em_execucao_info: { label: 'Em execução', color: colors.primary, bg: colors.infoBg, icon: Info },
  pendente: { label: 'Pendente', color: colors.error, bg: colors.errorBg, icon: CircleX },
  critico: { label: 'Crítico', color: colors.error, bg: colors.errorBg },
  atencao: { label: 'Atenção', color: '#e0850a', bg: colors.warningBg },
  regular: { label: 'Regular', color: colors.primary, bg: colors.infoBg },
  conforme: { label: 'Conforme', color: colors.success, bg: colors.successBg },
  verificado: { label: 'Verificado', color: colors.primary, bg: colors.infoBg },
}

interface StatusChipProps {
  status: StatusKey
  label?: string
  size?: 'sm' | 'md'
  withIcon?: boolean
}

export function StatusChip({ status, label, size = 'md', withIcon = true }: StatusChipProps) {
  const def = map[status]
  const Icon = def.icon
  return (
    <Box
      component="span"
      role="status"
      sx={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: 0.75,
        px: size === 'sm' ? 1.25 : 1.75,
        height: size === 'sm' ? 24 : 30,
        borderRadius: '8px',
        bgcolor: def.bg,
        color: def.color,
        fontSize: size === 'sm' ? 12 : 13,
        fontWeight: 600,
        whiteSpace: 'nowrap',
      }}
    >
      {withIcon && Icon && (
        <Icon size={size === 'sm' ? 13 : 15} strokeWidth={2.2} fill={def.color} color="#fff" />
      )}
      {label ?? def.label}
    </Box>
  )
}
