import type { ReactNode } from 'react'
import Box from '@mui/material/Box'
import Paper from '@mui/material/Paper'
import Typography from '@mui/material/Typography'
import { colors } from '@/theme/tokens'

interface SectionCardProps {
  title?: ReactNode
  subtitle?: ReactNode
  icon?: ReactNode
  action?: ReactNode
  children: ReactNode
  padding?: number
  sx?: object
  headerSx?: object
  bodySx?: object
}

export function SectionCard({ title, subtitle, icon, action, children, padding = 2, sx, headerSx, bodySx }: SectionCardProps) {
  return (
    <Paper sx={{ display: 'flex', flexDirection: 'column', minWidth: 0, ...sx }}>
      {(title || action) && (
        <Box sx={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: 2, px: padding, pt: padding, pb: 1, ...headerSx }}>
          <Box sx={{ display: 'flex', alignItems: 'flex-start', gap: 1.25, minWidth: 0 }}>
            {icon && <Box sx={{ color: colors.primary, display: 'flex', mt: 0.25 }}>{icon}</Box>}
            <Box sx={{ minWidth: 0 }}>
              {title && (
                <Typography variant="h3" component="h2">
                  {title}
                </Typography>
              )}
              {subtitle && (
                <Typography sx={{ fontSize: 13, color: colors.textSecondary, mt: 0.25 }}>{subtitle}</Typography>
              )}
            </Box>
          </Box>
          {action && <Box sx={{ flexShrink: 0 }}>{action}</Box>}
        </Box>
      )}
      <Box sx={{ px: padding, pb: padding, pt: title ? 0 : padding, flex: 1, minWidth: 0, ...bodySx }}>{children}</Box>
    </Paper>
  )
}
