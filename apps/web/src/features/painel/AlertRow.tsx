import { Link as RouterLink } from 'react-router'
import Box from '@mui/material/Box'
import Typography from '@mui/material/Typography'
import { ChevronRight, CircleAlert, Info, TriangleAlert } from 'lucide-react'
import type { Alerta } from '@/api/types'
import { colors } from '@/theme/tokens'

const alertIcon = {
  error: { icon: TriangleAlert, color: colors.error },
  warning: { icon: TriangleAlert, color: colors.warning },
  info: { icon: Info, color: colors.primary },
  success: { icon: CircleAlert, color: colors.success },
}

/** One alert; it links to the screen where it is dealt with, when there is one. */
export function AlertRow({ alerta }: { alerta: Alerta }) {
  const def = alertIcon[alerta.severidade]
  const Icon = def.icon
  const link = alerta.to ? { component: RouterLink, to: alerta.to } : {}
  return (
    <Box
      {...link}
      sx={{
        textDecoration: 'none',
        color: 'inherit',
        borderRadius: '8px',
        '&:hover': alerta.to ? { bgcolor: colors.primarySoft } : undefined,
        display: 'flex',
        alignItems: 'center',
        gap: 1.5,
        py: 1,
        borderBottom: `1px solid ${colors.border}`,
        '&:last-of-type': { borderBottom: 0 },
      }}
    >
      <Box sx={{ color: def.color, display: 'flex', flexShrink: 0 }}>
        <Icon size={22} fill={def.color} color="#fff" strokeWidth={2} />
      </Box>
      <Box sx={{ flex: 1, minWidth: 0 }}>
        <Typography sx={{ fontSize: 13, fontWeight: 700, color: colors.navy }}>
          {alerta.titulo}
        </Typography>
        <Typography sx={{ fontSize: 12, color: colors.textSecondary, lineHeight: 1.35 }}>
          {alerta.descricao}
        </Typography>
      </Box>
      <Box
        sx={{
          textAlign: 'right',
          fontSize: 12,
          color: colors.textSecondary,
          lineHeight: 1.35,
          flexShrink: 0,
        }}
      >
        {alerta.data}
        <br />
        {alerta.hora}
      </Box>
      {alerta.to && <ChevronRight size={18} color={colors.primary} />}
    </Box>
  )
}
