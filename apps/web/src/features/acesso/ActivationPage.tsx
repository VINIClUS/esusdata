import { useState, type SubmitEvent } from 'react'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Link from '@mui/material/Link'
import Typography from '@mui/material/Typography'
import { KeyRound, Lock } from 'lucide-react'
import { Link as RouterLink, Navigate, useNavigate } from 'react-router'
import { useAuth } from '@/app/auth-context'
import { ApiError, apiFetch, ensureApiReady } from '@/api/client'
import { Field, PasswordField } from '@/components/ui/Inputs'
import { colors } from '@/theme/tokens'
import { AuthLayout } from './AuthLayout'

export function ActivationPage() {
  const { user } = useAuth()
  const navigate = useNavigate()
  const [token, setToken] = useState('')
  const [password, setPassword] = useState('')
  const [confirmation, setConfirmation] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)

  if (user) return <Navigate to="/painel" replace />

  async function submit(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault()
    if (password !== confirmation) {
      setError('As senhas não coincidem.')
      return
    }
    setError(null)
    setLoading(true)
    try {
      await ensureApiReady()
      await apiFetch<undefined>('/auth/activate', {
        method: 'POST',
        body: JSON.stringify({ token: token.trim(), password }),
      })
      await navigate('/login?activated=1', { replace: true })
    } catch (err) {
      setError(
        err instanceof ApiError && err.code === 'WEAK_PASSWORD'
          ? 'Senha fraca. Use uma senha mais longa e difícil de adivinhar.'
          : err instanceof ApiError && err.code === 'ACTIVATION_FAILED'
            ? 'Código inválido, vencido ou já utilizado. Peça um novo código ao administrador.'
            : 'Não foi possível ativar o acesso. Tente novamente.',
      )
    } finally {
      setLoading(false)
    }
  }

  return (
    <AuthLayout onSubmit={(event) => void submit(event)}>
      <Typography
        sx={{ fontSize: { xs: 34, md: 44 }, fontWeight: 700, color: colors.navy, lineHeight: 1.1 }}
      >
        Ativar acesso
      </Typography>
      <Typography sx={{ fontSize: 19, color: colors.textSecondary, mt: 1.5 }}>
        Use o código recebido para definir sua senha.
      </Typography>
      <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2.5, mt: 4 }}>
        <Field
          label="Código de ativação"
          icon={KeyRound}
          large
          value={token}
          onChange={(e) => setToken(e.target.value)}
          autoComplete="off"
          required
        />
        <PasswordField
          label="Nova senha"
          icon={Lock}
          large
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          autoComplete="new-password"
          required
        />
        <PasswordField
          label="Confirmar nova senha"
          icon={Lock}
          large
          value={confirmation}
          onChange={(e) => setConfirmation(e.target.value)}
          autoComplete="new-password"
          required
        />
      </Box>
      {error && (
        <Typography role="alert" sx={{ color: colors.error, mt: 2 }}>
          {error}
        </Typography>
      )}
      <Button
        type="submit"
        variant="contained"
        fullWidth
        disabled={loading}
        sx={{ mt: 4, minHeight: 60, fontSize: 19 }}
      >
        Ativar meu acesso
      </Button>
      <Box sx={{ textAlign: 'center', mt: 3 }}>
        <Link component={RouterLink} to="/login">
          Voltar ao login
        </Link>
      </Box>
    </AuthLayout>
  )
}
