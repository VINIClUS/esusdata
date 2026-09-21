import Box from '@mui/material/Box'
import type { LucideIcon } from 'lucide-react'
import { colors } from '@/theme/tokens'

export interface TabItem {
  key: string
  label: string
  count?: number
  icon?: LucideIcon
}

interface TabsProps {
  items: TabItem[]
  value: string
  onChange: (key: string) => void
  sx?: object
}

/** Pill-style category tabs (Indicadores). */
export function PillTabs({ items, value, onChange, sx }: TabsProps) {
  return (
    <Box role="tablist" sx={{ display: 'flex', gap: 1.5, overflowX: 'auto', pb: 0.5, ...sx }}>
      {items.map((item) => {
        const active = item.key === value
        return (
          <Box
            key={item.key}
            component="button"
            type="button"
            role="tab"
            aria-selected={active}
            onClick={() => onChange(item.key)}
            sx={{
              px: 2.25,
              height: 48,
              borderRadius: '10px',
              border: `1px solid ${active ? colors.infoBorder : colors.border}`,
              bgcolor: active ? colors.primarySoft : '#fff',
              color: active ? colors.primary : colors.navy,
              font: 'inherit',
              fontSize: 15,
              fontWeight: active ? 700 : 500,
              cursor: 'pointer',
              whiteSpace: 'nowrap',
              '&:hover': { borderColor: colors.borderStrong },
            }}
          >
            {item.label}
            {item.count !== undefined && ` (${item.count})`}
          </Box>
        )
      })}
    </Box>
  )
}

/** Underline tabs; with `boxed` the active tab gets a bordered box (Execução). */
export function UnderlineTabs({ items, value, onChange, boxed, sx }: TabsProps & { boxed?: boolean }) {
  return (
    <Box
      role="tablist"
      sx={{
        display: 'flex',
        gap: boxed ? 0 : 1,
        borderBottom: `1px solid ${colors.border}`,
        overflowX: 'auto',
        ...(boxed ? { border: `1px solid ${colors.border}`, borderBottom: 0, borderRadius: '12px 12px 0 0', width: 'fit-content', bgcolor: '#f7f9fd' } : {}),
        ...sx,
      }}
    >
      {items.map((item) => {
        const active = item.key === value
        const Icon = item.icon
        return (
          <Box
            key={item.key}
            component="button"
            type="button"
            role="tab"
            aria-selected={active}
            onClick={() => onChange(item.key)}
            sx={{
              display: 'flex',
              alignItems: 'center',
              gap: 1.25,
              px: boxed ? 3.5 : 3,
              height: boxed ? 52 : 48,
              border: 0,
              borderBottom: `3px solid ${active ? colors.primary : 'transparent'}`,
              mb: '-1px',
              bgcolor: active && boxed ? '#fff' : 'transparent',
              color: active ? colors.primary : colors.textSecondary,
              font: 'inherit',
              fontSize: 16,
              fontWeight: active ? 600 : 500,
              cursor: 'pointer',
              whiteSpace: 'nowrap',
              '&:hover': { color: colors.primary },
            }}
          >
            {Icon && <Icon size={22} strokeWidth={1.9} />}
            {item.label}
          </Box>
        )
      })}
    </Box>
  )
}
