import type { ReactNode } from 'react'
import Box from '@mui/material/Box'
import Typography from '@mui/material/Typography'
import { colors } from '@/theme/tokens'

interface IconRowProps {
  icon: ReactNode
  title: ReactNode
  text?: ReactNode
  iconVariant?: 'circle' | 'plain'
  size?: 'sm' | 'md'
}

export function IconRow({ icon, title, text, iconVariant = 'circle', size = 'md' }: IconRowProps) {
  return (
    <Box sx={{ display: 'flex', gap: 1.5, alignItems: 'flex-start' }}>
      <Box
        sx={{
          width: iconVariant === 'circle' ? 28 : 22,
          height: iconVariant === 'circle' ? 28 : 22,
          borderRadius: '50%',
          bgcolor: iconVariant === 'circle' ? colors.primary : 'transparent',
          color: iconVariant === 'circle' ? '#fff' : colors.primary,
          display: 'grid',
          placeItems: 'center',
          flexShrink: 0,
        }}
      >
        {icon}
      </Box>
      <Box sx={{ minWidth: 0 }}>
        <Typography sx={{ fontSize: size === 'sm' ? 12 : 14, fontWeight: 700, color: colors.navy, lineHeight: 1.3 }}>{title}</Typography>
        {text && (
          <Typography sx={{ fontSize: size === 'sm' ? 11.5 : 13, color: colors.textSecondary, lineHeight: 1.4, mt: 0.15 }}>{text}</Typography>
        )}
      </Box>
    </Box>
  )
}
