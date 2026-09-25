import Box from '@mui/material/Box'
import Typography from '@mui/material/Typography'
import { CircleQuestionMark, LogOut, type LucideIcon } from 'lucide-react'
import { NavLink, useLocation, useNavigate } from 'react-router'
import { navItems } from '@/app/navigation'
import { useAuth } from '@/app/auth'
import { demoContext } from '@/api/fixtures/context'
import { colors, layout } from '@/theme/tokens'
import { Logo } from './Logo'
import { WaveDecoration } from './WaveDecoration'

interface SidebarProps {
  onNavigate?: () => void
}

function NavRow({
  to,
  label,
  icon: Icon,
  active,
  onClick,
}: {
  to?: string
  label: string
  icon: LucideIcon
  active?: boolean
  onClick?: () => void
}) {
  const sx = {
    display: 'flex',
    alignItems: 'center',
    gap: 1.5,
    px: 1.5,
    py: 1.4,
    mx: 1.25,
    whiteSpace: 'nowrap',
    borderRadius: '10px',
    color: '#fff',
    textDecoration: 'none',
    fontSize: 15,
    fontWeight: active ? 600 : 500,
    bgcolor: active ? 'rgba(45, 105, 224, 0.75)' : 'transparent',
    boxShadow: active ? '0 6px 16px rgba(0,0,0,0.18)' : 'none',
    cursor: 'pointer',
    transition: 'background-color .15s',
    '&:hover': { bgcolor: active ? 'rgba(45, 105, 224, 0.85)' : 'rgba(255,255,255,0.08)' },
  } as const
  const content = (
    <>
      <Icon size={21} strokeWidth={1.8} />
      <span>{label}</span>
    </>
  )
  if (to) {
    return (
      <Box component={NavLink} to={to} onClick={onClick} sx={sx}>
        {content}
      </Box>
    )
  }
  return (
    <Box
      component="button"
      type="button"
      onClick={onClick}
      sx={{ ...sx, border: 0, width: 'calc(100% - 24px)', font: 'inherit', textAlign: 'left' }}
    >
      {content}
    </Box>
  )
}

export function Sidebar({ onNavigate }: SidebarProps) {
  const { pathname } = useLocation()
  const { logout } = useAuth()
  const navigate = useNavigate()

  return (
    <Box
      component="nav"
      aria-label="Navegação principal"
      sx={{
        width: layout.sidebarWidth,
        flexShrink: 0,
        height: '100%',
        display: 'flex',
        flexDirection: 'column',
        position: 'relative',
        background: `linear-gradient(180deg, ${colors.navyMid} 0%, ${colors.navyDeep} 100%)`,
        color: '#fff',
        overflow: 'hidden',
      }}
    >
      <Box sx={{ px: 2.25, pt: 2.25, pb: 2 }}>
        <Logo size="md" />
      </Box>

      <Box
        sx={{ display: 'flex', flexDirection: 'column', gap: 0.5, position: 'relative', zIndex: 1 }}
      >
        {navItems.map((item) => (
          <NavRow
            key={item.to}
            to={item.to}
            label={item.label}
            icon={item.icon}
            active={item.match(pathname)}
            onClick={onNavigate}
          />
        ))}
      </Box>

      <Box sx={{ flex: 1 }} />

      <Box
        sx={{
          display: 'flex',
          flexDirection: 'column',
          gap: 0.5,
          position: 'relative',
          zIndex: 1,
          pb: 2,
        }}
      >
        <NavRow
          to="/ajuda"
          label="Ajuda"
          icon={CircleQuestionMark}
          active={pathname.startsWith('/ajuda')}
          onClick={onNavigate}
        />
        <NavRow
          label="Sair"
          icon={LogOut}
          onClick={async () => {
            await logout()
            navigate('/login')
          }}
        />
      </Box>

      <Box sx={{ position: 'relative', zIndex: 1, px: 2.5, pb: 2.5 }}>
        <Typography sx={{ fontSize: 15, lineHeight: 1.45, color: '#fff', fontWeight: 500 }}>
          Mais dados.
          <br />
          Melhores decisões.
          <br />
          Uma APS mais forte.
        </Typography>
        <Typography
          sx={{ fontSize: 12.5, color: 'rgba(255,255,255,0.7)', textAlign: 'right', mt: 0.5 }}
        >
          {demoContext.versao}
        </Typography>
      </Box>

      <WaveDecoration height={220} />
    </Box>
  )
}
