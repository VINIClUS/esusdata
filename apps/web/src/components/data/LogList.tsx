import Box from '@mui/material/Box'
import Typography from '@mui/material/Typography'
import type { LinhaLog } from '@/api/types'
import { colors } from '@/theme/tokens'

export function LogList({ lines }: { lines: LinhaLog[] }) {
  return (
    <Box
      component="ol"
      sx={{ listStyle: 'none', m: 0, p: 0, display: 'flex', flexDirection: 'column', gap: 1.25 }}
    >
      {lines.map((l, i) => (
        <Box component="li" key={i} sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
          <Typography
            sx={{
              fontSize: 13.5,
              color: colors.textSecondary,
              width: 62,
              flexShrink: 0,
              fontVariantNumeric: 'tabular-nums',
            }}
          >
            {l.hora}
          </Typography>
          <Box
            sx={{
              width: 9,
              height: 9,
              borderRadius: '50%',
              bgcolor: l.nivel === 'success' ? colors.success : colors.primary,
              flexShrink: 0,
            }}
          />
          <Typography sx={{ fontSize: 13.5, color: colors.navy }}>{l.texto}</Typography>
        </Box>
      ))}
    </Box>
  )
}
