import Box from '@mui/material/Box'
import MenuItem from '@mui/material/MenuItem'
import Select, { type SelectChangeEvent } from '@mui/material/Select'
import { ChevronDown, type LucideIcon } from 'lucide-react'
import { colors } from '@/theme/tokens'

interface FilterSelectProps {
  label?: string
  value: string
  options: string[]
  onChange?: (value: string) => void
  icon?: LucideIcon
  fullWidth?: boolean
  size?: 'sm' | 'md'
  bold?: boolean
}

function Chevron(props: object) {
  return (
    <ChevronDown
      size={18}
      color={colors.textSecondary}
      {...props}
      style={{ right: 12, position: 'absolute', pointerEvents: 'none' }}
    />
  )
}

/** Select with an inline prefix label ("Status: Todos") or an icon prefix. */
export function FilterSelect({
  label,
  value,
  options,
  onChange,
  icon: Icon,
  fullWidth,
  size = 'md',
  bold,
}: FilterSelectProps) {
  const h = size === 'sm' ? 36 : 50
  return (
    <Select
      value={value}
      onChange={(e: SelectChangeEvent) => onChange?.(e.target.value)}
      IconComponent={Chevron}
      fullWidth={fullWidth}
      renderValue={(v) => (
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.25 }}>
          {Icon && <Icon size={20} color={colors.primary} />}
          {label && (
            <Box component="span" sx={{ color: colors.textSecondary, fontWeight: 400 }}>
              {label}:
            </Box>
          )}
          <Box component="span" sx={{ color: colors.navy, fontWeight: bold ? 600 : 500 }}>
            {v}
          </Box>
        </Box>
      )}
      sx={{
        height: h,
        fontSize: size === 'sm' ? 13 : 15,
        minWidth: fullWidth ? undefined : 150,
        '& .MuiSelect-select': { py: 0, pl: 2, display: 'flex', alignItems: 'center' },
      }}
    >
      {options.map((o) => (
        <MenuItem key={o} value={o}>
          {o}
        </MenuItem>
      ))}
    </Select>
  )
}
