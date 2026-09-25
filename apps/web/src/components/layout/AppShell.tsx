import { useState, type ReactNode } from 'react'
import Box from '@mui/material/Box'
import Drawer from '@mui/material/Drawer'
import useMediaQuery from '@mui/material/useMediaQuery'
import { useTheme } from '@mui/material/styles'
import { layout } from '@/theme/tokens'
import { BottomNavBar } from './BottomNavBar'
import { Sidebar } from './Sidebar'
import { TopBar } from './TopBar'

export function AppShell({ children }: { children: ReactNode }) {
  const theme = useTheme()
  const compact = useMediaQuery(theme.breakpoints.down('lg'))
  const phone = useMediaQuery(theme.breakpoints.down('md'))
  const [open, setOpen] = useState(false)

  return (
    <Box sx={{ display: 'flex', height: '100%', minHeight: '100vh' }}>
      {!compact && <Sidebar />}
      {compact && (
        <Drawer
          open={open}
          onClose={() => setOpen(false)}
          slotProps={{ paper: { sx: { border: 0, borderRadius: 0, width: layout.sidebarWidth } } }}
        >
          <Sidebar onNavigate={() => setOpen(false)} />
        </Drawer>
      )}
      <Box sx={{ flex: 1, minWidth: 0, display: 'flex', flexDirection: 'column', height: '100vh' }}>
        <TopBar compact={compact} phone={phone} onOpenMenu={() => setOpen(true)} />
        <Box
          component="main"
          sx={{ flex: 1, overflow: 'auto', px: { xs: 2, md: 3 }, py: { xs: 2, md: 2 } }}
        >
          {children}
        </Box>
        {compact && <BottomNavBar />}
      </Box>
    </Box>
  )
}
