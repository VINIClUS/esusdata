import type { ReactNode } from 'react'
import Box from '@mui/material/Box'
import Typography from '@mui/material/Typography'
import { CircleCheck, Info, TriangleAlert, CircleX } from 'lucide-react'
import type { Severity } from '@/api/types'
import { colors } from '@/theme/tokens'

interface CalloutProps {
  variant: Severity
  title?: ReactNode
  children?: ReactNode
  action?: ReactNode
  iconStyle?: 'filled' | 'plain'
  dense?: boolean
}

const defs = {
  success: { color: colors.success, bg: colors.successBg, border: colors.successBorder, icon: CircleCheck },
  info: { color: colors.primary, bg: colors.infoBg, border: colors.infoBorder, icon: Info },
  warning: { color: colors.warning, bg: colors.warningBg, border: '#f6dcae', icon: TriangleAlert },
  error: { color: colors.error, bg: colors.errorBg, border: '#f5c2c2', icon: CircleX },
}

export function Callout({ variant, title, children, action, iconStyle = 'filled', dense }: CalloutProps) {
  const d = defs[variant]
  const Icon = d.icon
  return (
    <Box
      sx={{
        display: 'flex',
        alignItems: 'flex-start',
        gap: 1.5,
        p: dense ? 1.5 : 2,
        borderRadius: '12px',
        bgcolor: d.bg,
        border: `1px solid ${d.border}`,
      }}
    >
      <Box
        sx={{
          width: 28,
          height: 28,
          borderRadius: '50%',
          bgcolor: iconStyle === 'filled' ? d.color : 'transparent',
          color: iconStyle === 'filled' ? '#fff' : d.color,
          display: 'grid',
          placeItems: 'center',
          flexShrink: 0,
        }}
      >
        <Icon size={iconStyle === 'filled' ? 16 : 24} strokeWidth={2.4} />
      </Box>
      <Box sx={{ flex: 1, minWidth: 0 }}>
        {title && (
          <Typography sx={{ fontSize: dense ? 13 : 14, fontWeight: 700, color: variant === 'success' ? colors.success : variant === 'info' ? colors.primary : d.color }}>
            {title}
          </Typography>
        )}
        {children && (
          <Typography component="div" sx={{ fontSize: dense ? 12 : 13, color: colors.textSecondary, mt: title ? 0.25 : 0, lineHeight: 1.5 }}>
            {children}
          </Typography>
        )}
      </Box>
      {action}
    </Box>
  )
}
