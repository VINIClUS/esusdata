import Box from '@mui/material/Box'
import type { MouseEvent } from 'react'
import { ChevronDown, type LucideIcon } from 'lucide-react'
import { colors } from '@/theme/tokens'

interface SelectorChipProps {
  icon: LucideIcon
  label: string
  attached?: boolean
  onClick?: (event: MouseEvent<HTMLElement>) => void
}

export function SelectorChip({ icon: Icon, label, attached, onClick }: SelectorChipProps) {
  return (
    <Box
      component="button"
      type="button"
      onClick={onClick}
      sx={{
        display: 'flex',
        alignItems: 'center',
        gap: 1.25,
        height: 40,
        px: 1.5,
        bgcolor: '#fff',
        border: `1px solid ${colors.border}`,
        borderRadius: attached ? '0 10px 10px 0' : '10px',
        font: 'inherit',
        fontSize: 14,
        fontWeight: 600,
        color: colors.navy,
        cursor: 'pointer',
        whiteSpace: 'nowrap',
        '&:hover': { borderColor: colors.borderStrong },
      }}
    >
      <Icon size={18} color={colors.primary} />
      <span>{label}</span>
      <ChevronDown size={16} color={colors.textSecondary} />
    </Box>
  )
}
