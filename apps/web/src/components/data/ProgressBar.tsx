import Box from '@mui/material/Box'
import LinearProgress from '@mui/material/LinearProgress'
import Typography from '@mui/material/Typography'
import { colors } from '@/theme/tokens'

interface ProgressBarProps {
  label: string
  done: number
  total: number | null
}

/** Real counter-based progress; indeterminate when the total is unknown. */
export function ProgressBar({ label, done, total }: ProgressBarProps) {
  const pct = total ? Math.round((done / total) * 100) : null
  return (
    <Box sx={{ flex: 1, minWidth: 0 }}>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', mb: 1 }}>
        <Typography sx={{ fontSize: 16, fontWeight: 600, color: colors.navy }}>{label}</Typography>
        <Typography sx={{ fontSize: 16, fontWeight: 700, color: colors.navy }} aria-live="polite">
          {pct === null ? `${done} processados` : `${pct}%`}
        </Typography>
      </Box>
      <LinearProgress
        variant={pct === null ? 'indeterminate' : 'determinate'}
        value={pct ?? undefined}
      />
    </Box>
  )
}
