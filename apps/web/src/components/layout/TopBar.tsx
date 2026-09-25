import { useState, type MouseEvent } from 'react'
import Avatar from '@mui/material/Avatar'
import Box from '@mui/material/Box'
import IconButton from '@mui/material/IconButton'
import Menu from '@mui/material/Menu'
import MenuItem from '@mui/material/MenuItem'
import Typography from '@mui/material/Typography'
import { Building, Calendar, ChevronDown, Menu as MenuIcon, type LucideIcon } from 'lucide-react'
import { useAuth } from '@/app/auth-context'
import { formatReferencePeriod, realContextForScope } from '@/app/display-context'
import { useScope } from '@/app/scope-context'
import { USE_MOCKS } from '@/api/client'
import { demoContext } from '@/api/fixtures/context'
import { colors, layout } from '@/theme/tokens'
import { SelectorChip } from '@/components/ui/SelectorChip'
import { Logo } from './Logo'

interface TopBarProps {
  compact: boolean
  phone: boolean
  onOpenMenu: () => void
}

interface ScopeSelectorProps {
  icon: LucideIcon
  label: string
  attached?: boolean
  options: string[]
  selected?: string
  formatOption: (option: string) => string
  onSelect: (option: string) => void
}

/** A SelectorChip that opens a menu only when there is more than one option to choose from. */
function ScopeSelector({
  icon,
  label,
  attached,
  options,
  selected,
  formatOption,
  onSelect,
}: ScopeSelectorProps) {
  const [anchor, setAnchor] = useState<HTMLElement | null>(null)
  const selectable = options.length > 1
  return (
    <>
      <SelectorChip
        icon={icon}
        label={label}
        attached={attached}
        onClick={
          selectable
            ? (event: MouseEvent<HTMLElement>) => setAnchor(event.currentTarget)
            : undefined
        }
      />
      <Menu anchorEl={anchor} open={anchor !== null} onClose={() => setAnchor(null)}>
        {options.map((option) => (
          <MenuItem
            key={option}
            selected={option === selected}
            onClick={() => {
              onSelect(option)
              setAnchor(null)
            }}
          >
            {formatOption(option)}
          </MenuItem>
        ))}
      </Menu>
    </>
  )
}

export function TopBar({ compact, phone, onOpenMenu }: TopBarProps) {
  const { user } = useAuth()
  const scope = useScope()
  const displayContext = USE_MOCKS
    ? { municipio: demoContext.municipio, competencia: demoContext.competencia }
    : realContextForScope({
        municipalityIbge: scope.municipalityIbge,
        referencePeriod: scope.referencePeriod,
      })
  return (
    <Box
      component="header"
      sx={{
        height: layout.topBarHeight,
        flexShrink: 0,
        display: 'flex',
        alignItems: 'center',
        gap: 2,
        px: { xs: 1.5, md: 3 },
        bgcolor: '#f7f9fd',
        borderBottom: `1px solid ${colors.border}`,
      }}
    >
      {compact && (
        <>
          <IconButton aria-label="Abrir menu" onClick={onOpenMenu} sx={{ color: colors.navy }}>
            <MenuIcon size={22} />
          </IconButton>
          <Logo size="sm" tone="dark" />
        </>
      )}

      {!phone && (
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, ml: compact ? 1 : 0 }}>
          {!compact && (
            <Typography sx={{ fontSize: 13.5, color: colors.textSecondary }}>Município</Typography>
          )}
          <ScopeSelector
            icon={Building}
            label={displayContext.municipio}
            options={scope.municipalities}
            selected={scope.municipalityIbge}
            formatOption={(ibge) => `IBGE ${ibge}`}
            onSelect={scope.setMunicipality}
          />
        </Box>
      )}

      <Box sx={{ flex: 1 }} />

      {!phone && (
        <Box sx={{ display: 'flex', alignItems: 'center', mr: { md: 4 } }}>
          {!compact && (
            <Box
              sx={{
                px: 1.75,
                height: 40,
                display: 'flex',
                alignItems: 'center',
                bgcolor: '#eef2f8',
                border: `1px solid ${colors.border}`,
                borderRight: 0,
                borderRadius: '10px 0 0 10px',
                fontSize: 13.5,
                color: colors.textSecondary,
              }}
            >
              Competência
            </Box>
          )}
          <ScopeSelector
            icon={Calendar}
            label={displayContext.competencia}
            attached={!compact}
            options={scope.periods}
            selected={scope.referencePeriod}
            formatOption={formatReferencePeriod}
            onSelect={scope.setPeriod}
          />
        </Box>
      )}

      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.25, cursor: 'pointer' }}>
        <Avatar
          sx={{ bgcolor: colors.primary, width: 40, height: 40, fontSize: 14, fontWeight: 700 }}
        >
          {user?.iniciais ?? 'US'}
        </Avatar>
        {!phone && (
          <>
            <Box sx={{ lineHeight: 1.15 }}>
              <Typography sx={{ fontSize: 14, fontWeight: 700, color: colors.navy }}>
                {user?.nome}
              </Typography>
              <Typography sx={{ fontSize: 12.5, color: colors.textSecondary }}>
                {user?.papel}
              </Typography>
            </Box>
            <ChevronDown size={18} color={colors.textSecondary} />
          </>
        )}
      </Box>
    </Box>
  )
}
