import type { ReactNode } from 'react'
import Box from '@mui/material/Box'
import Paper from '@mui/material/Paper'
import Typography from '@mui/material/Typography'
import { ChartColumn, FileText, Settings, Users, type LucideIcon } from 'lucide-react'
import { demoContext } from '@/api/fixtures/context'
import { Logo } from '@/components/layout/Logo'
import { WaveDecoration } from '@/components/layout/WaveDecoration'
import { colors } from '@/theme/tokens'

const features: { icon: LucideIcon; title: string; text: string }[] = [
  {
    icon: ChartColumn,
    title: 'Inteligência local ou na rede municipal',
    text: 'Seus dados, do seu jeito.',
  },
  {
    icon: Users,
    title: 'Indicadores oficiais e metodologicamente robustos',
    text: 'Baseados nas diretrizes do e-SUS e da APS.',
  },
  {
    icon: FileText,
    title: 'Painel completo e responsivo',
    text: 'Visão clara para o monitoramento da sua APS.',
  },
  {
    icon: Settings,
    title: 'Seus dados, sob seu controle',
    text: 'Mais segurança e autonomia para sua equipe.',
  },
]

export function AuthLayout({
  children,
  onSubmit,
}: {
  children: ReactNode
  onSubmit: (event: React.SubmitEvent<HTMLFormElement>) => void
}) {
  return (
    <Box
      sx={{
        minHeight: '100vh',
        position: 'relative',
        overflow: 'hidden',
        background: 'linear-gradient(135deg, #f6f9fe 0%, #eef3fb 55%, #e6eefb 100%)',
        display: 'grid',
        gridTemplateColumns: { xs: '1fr', md: '1fr 1fr' },
        alignItems: 'center',
        gap: { xs: 3, md: 6 },
        px: { xs: 2, sm: 3, md: 8, lg: 11 },
        py: { xs: 3, md: 6 },
      }}
    >
      <WaveDecoration light height={420} />
      <Box
        sx={{
          position: 'relative',
          zIndex: 1,
          display: 'flex',
          flexDirection: 'column',
          height: '100%',
          justifyContent: 'space-between',
          gap: 5,
        }}
      >
        <Box>
          <Logo size="lg" tone="dark" />
          <Typography
            sx={{
              fontSize: { xs: 24, md: 30 },
              fontWeight: 500,
              color: colors.navy,
              lineHeight: 1.25,
              mt: 3.5,
            }}
          >
            Dados do e-SUS PEC
            <br />
            em indicadores que importam
          </Typography>
          <Box
            sx={{ display: { xs: 'none', md: 'flex' }, flexDirection: 'column', gap: 2.5, mt: 4.5 }}
          >
            {features.map((f) => {
              const Icon = f.icon
              return (
                <Box key={f.title} sx={{ display: 'flex', alignItems: 'center', gap: 2.5 }}>
                  <Box
                    sx={{
                      width: 62,
                      height: 62,
                      borderRadius: '50%',
                      bgcolor: '#e4edfb',
                      color: colors.primary,
                      display: 'grid',
                      placeItems: 'center',
                      flexShrink: 0,
                    }}
                  >
                    <Icon size={28} strokeWidth={2} />
                  </Box>
                  <Box>
                    <Typography
                      sx={{ fontSize: 19, fontWeight: 500, color: colors.navy, lineHeight: 1.3 }}
                    >
                      {f.title}
                    </Typography>
                    <Typography sx={{ fontSize: 15, color: colors.textSecondary }}>
                      {f.text}
                    </Typography>
                  </Box>
                </Box>
              )
            })}
          </Box>
        </Box>
        <Box sx={{ display: { xs: 'none', md: 'flex' }, alignItems: 'flex-end', gap: 6, mt: 8 }}>
          <Typography sx={{ fontSize: 28, fontWeight: 500, color: colors.navy, lineHeight: 1.35 }}>
            Mais dados.
            <br />
            Melhores decisões.
            <br />
            Uma APS mais forte.
          </Typography>
          <Typography sx={{ fontSize: 17, color: colors.textSecondary, pb: 0.5 }}>
            {demoContext.versao}
          </Typography>
        </Box>
      </Box>
      <Paper
        component="form"
        onSubmit={onSubmit}
        sx={{
          position: 'relative',
          zIndex: 1,
          p: { xs: 3, sm: 4, md: 8 },
          borderRadius: '22px',
          boxShadow: '0 20px 60px rgba(15,42,92,0.10)',
          maxWidth: 640,
          width: '100%',
          minWidth: 0,
          justifySelf: 'center',
        }}
      >
        {children}
      </Paper>
    </Box>
  )
}
