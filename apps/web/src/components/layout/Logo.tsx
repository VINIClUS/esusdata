import Box from '@mui/material/Box'
import Typography from '@mui/material/Typography'

interface LogoProps {
  size?: 'sm' | 'md' | 'lg'
  tone?: 'light' | 'dark'
}

const sizes = {
  sm: { icon: 28, title: 16, sub: 13 },
  md: { icon: 40, title: 22, sub: 18 },
  lg: { icon: 96, title: 56, sub: 44 },
}

export function LogoMark({ size = 40 }: { size?: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 32 32" aria-hidden>
      <rect x="2" y="18" width="7" height="10" rx="2.2" fill="#5aa0ff" />
      <rect x="12" y="11" width="7" height="17" rx="2.2" fill="#2f7cf6" />
      <rect x="22" y="4" width="7" height="24" rx="2.2" fill="#1a6ef5" />
    </svg>
  )
}

export function Logo({ size = 'md', tone = 'light' }: LogoProps) {
  const s = sizes[size]
  const color = tone === 'light' ? '#fff' : '#0f2a5c'
  return (
    <Box sx={{ display: 'flex', alignItems: 'center', gap: size === 'lg' ? 2.5 : 1.25 }}>
      <LogoMark size={s.icon} />
      <Box sx={{ lineHeight: 1 }}>
        <Typography
          component="div"
          sx={{
            color,
            fontWeight: 700,
            fontSize: s.title,
            lineHeight: 1.05,
            letterSpacing: '-0.3px',
          }}
        >
          Esusdata
        </Typography>
        <Typography
          component="div"
          sx={{ color, fontWeight: 400, fontSize: s.sub, lineHeight: 1.1, opacity: 0.92 }}
        >
          Helper
        </Typography>
      </Box>
    </Box>
  )
}
