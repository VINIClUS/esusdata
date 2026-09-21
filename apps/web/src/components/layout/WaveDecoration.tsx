import Box from '@mui/material/Box'

/** Decorative wave shapes used at the bottom of the sidebar and on the login page. */
export function WaveDecoration({ light = false, height = 180 }: { light?: boolean; height?: number }) {
  const fill1 = light ? 'rgba(26,110,245,0.05)' : 'rgba(255,255,255,0.06)'
  const fill2 = light ? 'rgba(26,110,245,0.09)' : 'rgba(255,255,255,0.10)'
  return (
    <Box
      aria-hidden
      sx={{ position: 'absolute', left: 0, right: 0, bottom: 0, height, pointerEvents: 'none', overflow: 'hidden' }}
    >
      <svg width="100%" height="100%" viewBox="0 0 400 180" preserveAspectRatio="none">
        <path d="M0 110 C 80 60, 140 150, 220 100 S 340 60, 400 110 L400 180 L0 180 Z" fill={fill1} />
        <path d="M0 140 C 90 100, 160 170, 250 130 S 350 110, 400 150 L400 180 L0 180 Z" fill={fill2} />
      </svg>
    </Box>
  )
}
