import { useState, type FormEvent } from 'react'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Checkbox from '@mui/material/Checkbox'
import Divider from '@mui/material/Divider'
import FormControlLabel from '@mui/material/FormControlLabel'
import Link from '@mui/material/Link'
import Paper from '@mui/material/Paper'
import Typography from '@mui/material/Typography'
import { ChartColumn, FileText, Lock, Settings, User, Users, type LucideIcon } from 'lucide-react'
import { Navigate, useNavigate } from 'react-router'
import { useAuth } from '@/app/auth'
import { demoContext } from '@/api/fixtures/context'
import { Logo } from '@/components/layout/Logo'
import { WaveDecoration } from '@/components/layout/WaveDecoration'
import { Field, PasswordField } from '@/components/ui/Inputs'
import { colors } from '@/theme/tokens'

const features: { icon: LucideIcon; title: string; text: string }[] = [
  { icon: ChartColumn, title: 'Inteligência local ou na rede municipal', text: 'Seus dados, do seu jeito.' },
  { icon: Users, title: 'Indicadores oficiais e metodologicamente robustos', text: 'Baseados nas diretrizes do e-SUS e da APS.' },
  { icon: FileText, title: 'Painel completo e responsivo', text: 'Visão clara para o monitoramento da sua APS.' },
  { icon: Settings, title: 'Seus dados, sob seu controle', text: 'Mais segurança e autonomia para sua equipe.' },
]

export function LoginPage() {
  const { user, isLoading, login } = useAuth()
  const navigate = useNavigate()
  const [usuario, setUsuario] = useState('')
  const [senha, setSenha] = useState('')
  const [lembrar, setLembrar] = useState(true)
  const [erro, setErro] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)

  if (user) return <Navigate to="/painel" replace />

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    setErro(null)
    setLoading(true)
    try {
      await login(usuario, senha)
      navigate('/painel', { replace: true })
    } catch (err) {
      setErro(err instanceof Error ? err.message : 'Falha ao entrar.')
    } finally {
      setLoading(false)
    }
  }

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
        gap: { xs: 4, md: 6 },
        px: { xs: 3, md: 8, lg: 11 },
        py: { xs: 5, md: 6 },
      }}
    >
      <WaveDecoration light height={420} />

      <Box sx={{ position: 'relative', zIndex: 1, display: 'flex', flexDirection: 'column', height: '100%', justifyContent: 'space-between', gap: 5 }}>
        <Box>
          <Logo size="lg" tone="dark" />
          <Typography sx={{ fontSize: { xs: 24, md: 30 }, fontWeight: 500, color: colors.navy, lineHeight: 1.25, mt: 3.5 }}>
            Dados do e-SUS PEC
            <br />
            em indicadores que importam
          </Typography>
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2.5, mt: 4.5 }}>
            {features.map((f) => {
              const Icon = f.icon
              return (
                <Box key={f.title} sx={{ display: 'flex', alignItems: 'center', gap: 2.5 }}>
                  <Box sx={{ width: 62, height: 62, borderRadius: '50%', bgcolor: '#e4edfb', color: colors.primary, display: 'grid', placeItems: 'center', flexShrink: 0 }}>
                    <Icon size={28} strokeWidth={2} />
                  </Box>
                  <Box>
                    <Typography sx={{ fontSize: 19, fontWeight: 500, color: colors.navy, lineHeight: 1.3 }}>{f.title}</Typography>
                    <Typography sx={{ fontSize: 15, color: colors.textSecondary }}>{f.text}</Typography>
                  </Box>
                </Box>
              )
            })}
          </Box>
        </Box>
        <Box sx={{ display: 'flex', alignItems: 'flex-end', gap: 6, mt: { xs: 2, md: 8 } }}>
          <Typography sx={{ fontSize: { xs: 22, md: 28 }, fontWeight: 500, color: colors.navy, lineHeight: 1.35 }}>
            Mais dados.
            <br />
            Melhores decisões.
            <br />
            Uma APS mais forte.
          </Typography>
          <Typography sx={{ fontSize: 17, color: colors.textSecondary, pb: 0.5 }}>{demoContext.versao}</Typography>
        </Box>
      </Box>

      <Paper component="form" onSubmit={onSubmit} sx={{ position: 'relative', zIndex: 1, p: { xs: 4, md: 8 }, borderRadius: '22px', boxShadow: '0 20px 60px rgba(15,42,92,0.10)', maxWidth: 640, width: '100%', justifySelf: 'center' }}>
        <Typography sx={{ fontSize: 44, fontWeight: 700, color: colors.navy, lineHeight: 1.1, letterSpacing: '-1px' }}>Entrar</Typography>
        <Typography sx={{ fontSize: 21, color: colors.textSecondary, mt: 1.5 }}>Acesse o Esusdata Helper</Typography>

        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 3, mt: 5 }}>
          <Field label="Usuário" icon={User} large placeholder="Seu usuário" value={usuario} onChange={(e) => setUsuario(e.target.value)} autoComplete="username" />
          <PasswordField label="Senha" icon={Lock} large placeholder="••••••••" value={senha} onChange={(e) => setSenha(e.target.value)} autoComplete="current-password" />
        </Box>

        <FormControlLabel
          sx={{ mt: 2.5, ml: -0.5, '& .MuiFormControlLabel-label': { fontSize: 19, color: colors.navy } }}
          control={<Checkbox checked={lembrar} onChange={(e) => setLembrar(e.target.checked)} sx={{ '& .MuiSvgIcon-root': { fontSize: 32, borderRadius: 2 } }} />}
          label="Lembrar de mim"
        />

        {erro && (
          <Typography role="alert" sx={{ color: colors.error, fontSize: 14, mt: 1 }}>
            {erro}
          </Typography>
        )}

        <Button type="submit" variant="contained" size="large" fullWidth disabled={loading || isLoading} sx={{ mt: 3.5, minHeight: 64, fontSize: 21, fontWeight: 500, borderRadius: '12px' }}>
          Entrar
        </Button>

        <Box sx={{ textAlign: 'center', mt: 3.5 }}>
          <Link href="#" underline="none" sx={{ fontSize: 19, fontWeight: 500, color: colors.primary }}>
            Esqueci minha senha
          </Link>
        </Box>

        <Divider sx={{ my: 4 }} />

        <Box sx={{ display: 'flex', justifyContent: 'center', gap: 3, alignItems: 'center' }}>
          <Link href="#" sx={{ fontSize: 16, color: colors.textSecondary }}>
            Documentação
          </Link>
          <Typography sx={{ color: colors.borderStrong }}>|</Typography>
          <Link href="#" sx={{ fontSize: 16, color: colors.textSecondary }}>
            Suporte
          </Link>
        </Box>
      </Paper>
    </Box>
  )
}
