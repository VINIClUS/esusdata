import Box from '@mui/material/Box'
import Typography from '@mui/material/Typography'
import { Check } from 'lucide-react'
import { colors } from '@/theme/tokens'

interface ChecklistItem {
  label: string
  value?: string
  ok?: boolean
}

interface ChecklistProps {
  items: ChecklistItem[]
  size?: 'sm' | 'md' | 'lg'
  divided?: boolean
}

export function CheckIcon({ size = 24, ok = true }: { size?: number; ok?: boolean }) {
  return (
    <Box
      sx={{
        width: size,
        height: size,
        borderRadius: '50%',
        bgcolor: ok ? colors.success : colors.error,
        color: '#fff',
        display: 'grid',
        placeItems: 'center',
        flexShrink: 0,
      }}
    >
      <Check size={size * 0.6} strokeWidth={3} />
    </Box>
  )
}

export function Checklist({ items, size = 'md', divided }: ChecklistProps) {
  const icon = size === 'lg' ? 32 : size === 'md' ? 24 : 20
  const font = size === 'lg' ? 16 : size === 'md' ? 13 : 12.5
  return (
    <Box sx={{ display: 'flex', flexDirection: 'column' }}>
      {items.map((item, i) => (
        <Box
          key={item.label}
          sx={{
            display: 'flex',
            alignItems: 'center',
            gap: 1.75,
            py: size === 'lg' ? 1.5 : size === 'md' ? 1 : 0.75,
            borderBottom: divided && i < items.length - 1 ? `1px solid ${colors.border}` : 0,
          }}
        >
          <CheckIcon size={icon} ok={item.ok ?? true} />
          <Typography sx={{ flex: 1, fontSize: font, color: colors.navy }}>{item.label}</Typography>
          {item.value && (
            <Typography sx={{ fontSize: font, fontWeight: 600, color: colors.navy }}>
              {item.value}
            </Typography>
          )}
        </Box>
      ))}
    </Box>
  )
}
