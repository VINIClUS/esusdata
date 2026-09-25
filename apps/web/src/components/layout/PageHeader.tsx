import type { ReactNode } from 'react'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Paper from '@mui/material/Paper'
import Typography from '@mui/material/Typography'
import { Clock, RefreshCw } from 'lucide-react'
import { USE_MOCKS } from '@/api/client'
import { demoContext } from '@/api/fixtures/context'
import { colors } from '@/theme/tokens'

interface PageHeaderProps {
  title: ReactNode
  subtitle?: ReactNode
  lastUpdate?: boolean
  actions?: ReactNode
  above?: ReactNode
  chip?: ReactNode
}

export function LastUpdateCard() {
  return (
    <Paper sx={{ display: 'flex', alignItems: 'center', gap: 1.5, pl: 1.5, pr: 1.25, py: 1 }}>
      <Box
        sx={{
          width: 36,
          height: 36,
          borderRadius: '50%',
          bgcolor: colors.primarySoft,
          color: colors.primary,
          display: 'grid',
          placeItems: 'center',
          flexShrink: 0,
        }}
      >
        <Clock size={20} />
      </Box>
      <Box sx={{ lineHeight: 1.2, display: { xs: 'none', sm: 'block' } }}>
        <Typography sx={{ fontSize: 12.5, color: colors.textSecondary }}>
          Última atualização dos dados
        </Typography>
        <Typography sx={{ fontSize: 13, fontWeight: 600, color: colors.navy }}>
          {USE_MOCKS ? demoContext.ultimaAtualizacao : 'Não disponível'}
        </Typography>
      </Box>
      <Button
        variant="outlined"
        color="primary"
        size="small"
        startIcon={<RefreshCw size={16} />}
        sx={{ ml: 1, minHeight: 38 }}
      >
        Atualizar
      </Button>
    </Paper>
  )
}

export function PageHeader({ title, subtitle, lastUpdate, actions, above, chip }: PageHeaderProps) {
  return (
    <Box sx={{ mb: 1.75 }}>
      {above}
      <Box
        sx={{
          display: 'flex',
          alignItems: 'flex-start',
          justifyContent: 'space-between',
          gap: 2,
          flexWrap: 'wrap',
        }}
      >
        <Box sx={{ minWidth: 0 }}>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, flexWrap: 'wrap' }}>
            <Typography variant="h1" component="h1">
              {title}
            </Typography>
            {chip}
          </Box>
          {subtitle && (
            <Typography sx={{ fontSize: { xs: 15, md: 16 }, color: colors.textSecondary, mt: 0.5 }}>
              {subtitle}
            </Typography>
          )}
        </Box>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
          {lastUpdate && <LastUpdateCard />}
          {actions}
        </Box>
      </Box>
    </Box>
  )
}
