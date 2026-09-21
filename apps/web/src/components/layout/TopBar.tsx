import Avatar from '@mui/material/Avatar'
import Box from '@mui/material/Box'
import IconButton from '@mui/material/IconButton'
import Typography from '@mui/material/Typography'
import { Building, Calendar, ChevronDown, Menu } from 'lucide-react'
import { useAuth } from '@/app/auth'
import { demoContext } from '@/api/fixtures/context'
import { colors, layout } from '@/theme/tokens'
import { SelectorChip } from '@/components/ui/SelectorChip'
import { Logo } from './Logo'

interface TopBarProps {
  compact: boolean
  phone: boolean
  onOpenMenu: () => void
}

export function TopBar({ compact, phone, onOpenMenu }: TopBarProps) {
  const { user } = useAuth()
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
            <Menu size={22} />
          </IconButton>
          <Logo size="sm" tone="dark" />
        </>
      )}

      {!phone && (
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, ml: compact ? 1 : 0 }}>
          {!compact && (
            <Typography sx={{ fontSize: 13.5, color: colors.textSecondary }}>Município</Typography>
          )}
          <SelectorChip icon={Building} label={demoContext.municipio} />
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
          <SelectorChip icon={Calendar} label={demoContext.competencia} attached={!compact} />
        </Box>
      )}

      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.25, cursor: 'pointer' }}>
        <Avatar sx={{ bgcolor: colors.primary, width: 40, height: 40, fontSize: 14, fontWeight: 700 }}>
          {user?.iniciais ?? 'US'}
        </Avatar>
        {!phone && (
          <>
            <Box sx={{ lineHeight: 1.15 }}>
              <Typography sx={{ fontSize: 14, fontWeight: 700, color: colors.navy }}>{user?.nome}</Typography>
              <Typography sx={{ fontSize: 12.5, color: colors.textSecondary }}>{user?.papel}</Typography>
            </Box>
            <ChevronDown size={18} color={colors.textSecondary} />
          </>
        )}
      </Box>
    </Box>
  )
}
