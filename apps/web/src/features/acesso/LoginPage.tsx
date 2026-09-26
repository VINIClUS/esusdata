import { useState, type SubmitEvent } from 'react'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Checkbox from '@mui/material/Checkbox'
import Divider from '@mui/material/Divider'
import FormControlLabel from '@mui/material/FormControlLabel'
import Link from '@mui/material/Link'
import Typography from '@mui/material/Typography'
import { Lock, User } from 'lucide-react'
import { Link as RouterLink, Navigate, useNavigate, useSearchParams } from 'react-router'
import { useAuth } from '@/app/auth-context'
import { ApiError } from '@/api/client'
import { Field, PasswordField } from '@/components/ui/Inputs'
import { colors } from '@/theme/tokens'
import { AuthLayout } from './AuthLayout'

const USERNAME_KEY = 'esusdata.remembered-username'

export function LoginPage() {
  const { user, isLoading, login } = useAuth()
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const [usuario, setUsuario] = useState(() => {
    try {
      return localStorage.getItem(USERNAME_KEY) ?? ''
    } catch {
      return ''
    }
  })
  const [senha, setSenha] = useState('')
  const [lembrar, setLembrar] = useState(false)
  const [erro, setErro] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)
  const [showSupport, setShowSupport] = useState(false)

  if (user) return <Navigate to="/painel" replace />

  async function onSubmit(e: SubmitEvent<HTMLFormElement>) {
    e.preventDefault()
    setErro(null)
    setLoading(true)
    try {
      await login(usuario, senha)
      try {
        if (lembrar) localStorage.setItem(USERNAME_KEY, usuario.trim())
        else localStorage.removeItem(USERNAME_KEY)
      } catch {
        // A disabled preference store must not prevent an authenticated login.
      }
      await navigate('/painel', { replace: true })
    } catch (err) {
      setErro(
        err instanceof ApiError && (err.status === 429 || err.code === 'LOGIN_THROTTLED')
          ? 'Muitas tentativas. Aguarde antes de tentar novamente.'
          : err instanceof ApiError && err.status === 401
            ? 'Usuário ou senha incorretos.'
            : err instanceof Error
              ? err.message
              : 'Falha ao entrar.',
      )
    } finally {
      setLoading(false)
    }
  }

  return (
    <AuthLayout onSubmit={(e) => void onSubmit(e)}>
      <Typography
        sx={{
          fontSize: { xs: 36, md: 44 },
          fontWeight: 700,
          color: colors.navy,
          lineHeight: 1.1,
          letterSpacing: '-1px',
        }}
      >
        Entrar
      </Typography>
      <Typography sx={{ fontSize: 21, color: colors.textSecondary, mt: 1.5 }}>
        Acesse o Esusdata Helper
      </Typography>
      {searchParams.get('activated') === '1' && (
        <Typography role="status" sx={{ color: colors.success, mt: 2 }}>
          Acesso ativado. Entre com sua nova senha.
        </Typography>
      )}
      <Box sx={{ display: 'flex', flexDirection: 'column', gap: 3, mt: 5 }}>
        <Field
          label="Usuário"
          icon={User}
          large
          placeholder="Seu usuário"
          value={usuario}
          onChange={(e) => setUsuario(e.target.value)}
          autoComplete="username"
        />
        <PasswordField
          label="Senha"
          icon={Lock}
          large
          placeholder="••••••••"
          value={senha}
          onChange={(e) => setSenha(e.target.value)}
          autoComplete="current-password"
        />
      </Box>
      <FormControlLabel
        sx={{
          mt: 2.5,
          ml: -0.5,
          '& .MuiFormControlLabel-label': { fontSize: 17, color: colors.navy },
        }}
        control={<Checkbox checked={lembrar} onChange={(e) => setLembrar(e.target.checked)} />}
        label="Lembrar somente o usuário neste navegador"
      />
      {erro && (
        <Typography role="alert" sx={{ color: colors.error, fontSize: 14, mt: 1 }}>
          {erro}
        </Typography>
      )}
      <Button
        type="submit"
        variant="contained"
        size="large"
        fullWidth
        disabled={loading || isLoading}
        sx={{ mt: 3.5, minHeight: 64, fontSize: 21, fontWeight: 500, borderRadius: '12px' }}
      >
        Entrar
      </Button>
      <Box sx={{ textAlign: 'center', mt: 3.5, display: 'flex', flexDirection: 'column', gap: 1 }}>
        <Link
          component={RouterLink}
          to="/ativar-acesso"
          underline="none"
          sx={{ fontSize: 19, fontWeight: 500, color: colors.primary }}
        >
          Ativar meu acesso
        </Link>
        <Link
          component="button"
          type="button"
          onClick={() => setShowSupport(true)}
          underline="none"
          sx={{ fontSize: 17, color: colors.primary }}
        >
          Esqueci minha senha
        </Link>
      </Box>
      {showSupport && (
        <Typography role="status" sx={{ mt: 2, color: colors.textSecondary }}>
          Solicite ajuda ao administrador da instalação. A recuperação de senha ainda não é
          automática.
        </Typography>
      )}
      <Divider sx={{ my: 4 }} />
      <Box sx={{ display: 'flex', justifyContent: 'center', gap: 3, alignItems: 'center' }}>
        <Link
          component={RouterLink}
          to="/ajuda-acesso"
          sx={{ fontSize: 16, color: colors.textSecondary }}
        >
          Ajuda e suporte
        </Link>
      </Box>
    </AuthLayout>
  )
}
