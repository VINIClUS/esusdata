import type { ReactNode } from 'react'
import Box from '@mui/material/Box'
import Typography from '@mui/material/Typography'
import { colors } from '@/theme/tokens'

interface Stat {
  icon?: ReactNode
  label: string
  value: ReactNode
}

interface StatColumnsProps {
  stats: Stat[]
  boxed?: boolean
  valueSize?: number
}

/** Row of label/value columns separated by vertical dividers. */
export function StatColumns({ stats, boxed, valueSize = 20 }: StatColumnsProps) {
  return (
    <Box
      sx={{
        display: 'grid',
        gridTemplateColumns: { xs: '1fr 1fr', md: `repeat(${stats.length}, 1fr)` },
        gap: { xs: 2, md: 0 },
        ...(boxed
          ? { border: `1px solid ${colors.border}`, borderRadius: '12px', p: 2, bgcolor: '#fff' }
          : {}),
      }}
    >
      {stats.map((s, i) => (
        <Box
          key={s.label}
          sx={{
            display: 'flex',
            gap: 1.5,
            alignItems: 'flex-start',
            px: { xs: 0, md: i === 0 ? 0 : 3 },
            borderLeft: { xs: 0, md: i === 0 ? 0 : `1px solid ${colors.border}` },
          }}
        >
          {s.icon && <Box sx={{ color: colors.primary, display: 'flex', mt: 0.25 }}>{s.icon}</Box>}
          <Box>
            <Typography sx={{ fontSize: 13, color: colors.textSecondary }}>{s.label}</Typography>
            <Typography
              sx={{ fontSize: valueSize, fontWeight: 700, color: colors.navy, lineHeight: 1.3 }}
            >
              {s.value}
            </Typography>
          </Box>
        </Box>
      ))}
    </Box>
  )
}
