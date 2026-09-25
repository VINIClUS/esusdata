import Box from '@mui/material/Box'
import Menu from '@mui/material/Menu'
import MenuItem from '@mui/material/MenuItem'
import ListItemIcon from '@mui/material/ListItemIcon'
import { Ellipsis } from 'lucide-react'
import { useState, type MouseEvent } from 'react'
import { NavLink, useLocation, useNavigate } from 'react-router'
import { navItems } from '@/app/navigation'
import { useAuth } from '@/app/auth'
import { CircleQuestionMark, LogOut } from 'lucide-react'
import { colors, layout } from '@/theme/tokens'

const primary = navItems.filter((n) =>
  ['/painel', '/indicadores', '/execucao', '/relatorios'].includes(n.to),
)
const secondary = navItems.filter((n) => ['/base-de-dados', '/configuracoes'].includes(n.to))

export function BottomNavBar() {
  const { pathname } = useLocation()
  const [anchor, setAnchor] = useState<HTMLElement | null>(null)
  const navigate = useNavigate()
  const { logout } = useAuth()
  const moreActive = secondary.some((s) => s.match(pathname)) || pathname.startsWith('/ajuda')

  const itemSx = (active: boolean) => ({
    flex: 1,
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 0.5,
    textDecoration: 'none',
    fontSize: 11.5,
    fontWeight: active ? 600 : 500,
    color: active ? colors.primary : colors.textSecondary,
    border: 0,
    bgcolor: 'transparent',
    font: 'inherit',
    cursor: 'pointer',
  })

  const open = (e: MouseEvent<HTMLElement>) => setAnchor(e.currentTarget)
  const close = () => setAnchor(null)

  return (
    <Box
      component="nav"
      aria-label="Navegação inferior"
      sx={{
        height: layout.bottomNavHeight,
        flexShrink: 0,
        display: 'flex',
        bgcolor: '#fff',
        borderTop: `1px solid ${colors.border}`,
      }}
    >
      {primary.map((item) => {
        const active = item.match(pathname)
        const Icon = item.icon
        return (
          <Box key={item.to} component={NavLink} to={item.to} sx={itemSx(active)}>
            <Icon size={22} strokeWidth={active ? 2.2 : 1.8} />
            <span>{item.shortLabel}</span>
          </Box>
        )
      })}
      <Box
        component="button"
        type="button"
        onClick={open}
        sx={itemSx(moreActive)}
        aria-label="Mais opções"
      >
        <Ellipsis size={22} />
        <span>Mais</span>
      </Box>
      <Menu
        anchorEl={anchor}
        open={Boolean(anchor)}
        onClose={close}
        anchorOrigin={{ vertical: 'top', horizontal: 'right' }}
        transformOrigin={{ vertical: 'bottom', horizontal: 'right' }}
      >
        {secondary.map((item) => {
          const Icon = item.icon
          return (
            <MenuItem
              key={item.to}
              onClick={() => {
                close()
                navigate(item.to)
              }}
            >
              <ListItemIcon>
                <Icon size={18} />
              </ListItemIcon>
              {item.label}
            </MenuItem>
          )
        })}
        <MenuItem
          onClick={() => {
            close()
            navigate('/ajuda')
          }}
        >
          <ListItemIcon>
            <CircleQuestionMark size={18} />
          </ListItemIcon>
          Ajuda
        </MenuItem>
        <MenuItem
          onClick={async () => {
            close()
            await logout()
            navigate('/login')
          }}
        >
          <ListItemIcon>
            <LogOut size={18} />
          </ListItemIcon>
          Sair
        </MenuItem>
      </Menu>
    </Box>
  )
}
