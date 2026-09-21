import Box from '@mui/material/Box'
import CircularProgress from '@mui/material/CircularProgress'
import { Navigate, Outlet, useLocation } from 'react-router'
import { AppShell } from '@/components/layout/AppShell'
import { useAuth } from './auth'

export function RequireAuth() {
  const { user, isLoading } = useAuth()
  const location = useLocation()
  if (isLoading) {
    return (
      <Box sx={{ minHeight: '100vh', display: 'grid', placeItems: 'center' }}>
        <CircularProgress aria-label="Carregando sessão" />
      </Box>
    )
  }
  if (!user) return <Navigate to="/login" replace state={{ from: location.pathname }} />
  return (
    <AppShell>
      <Outlet />
    </AppShell>
  )
}
