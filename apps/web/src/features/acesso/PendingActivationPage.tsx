import { useEffect, useState } from 'react'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Paper from '@mui/material/Paper'
import Typography from '@mui/material/Typography'
import { Navigate } from 'react-router'
import { useAuth } from '@/app/auth-context'
import { apiFetch, ensureApiReady } from '@/api/client'
import { PasswordField } from '@/components/ui/Inputs'
import { PageHeader } from '@/components/layout/PageHeader'

interface PendingUser {
  userId: string
  username: string
  displayName: string
  expiresAt: string | null
}
interface IssuedToken {
  activationToken: string
  expiresAt: string
}

export function PendingActivationPage() {
  const { user } = useAuth()
  const [accounts, setAccounts] = useState<PendingUser[]>([])
  const [selected, setSelected] = useState<PendingUser | null>(null)
  const [password, setPassword] = useState('')
  const [issued, setIssued] = useState<IssuedToken | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)

  useEffect(() => {
    if (!user?.canManageAccess) return
    void apiFetch<PendingUser[]>('/users/pending-activation')
      .then(setAccounts)
      .catch(() => setError('Não foi possível carregar as ativações pendentes.'))
  }, [user?.canManageAccess])

  if (!user?.canManageAccess) return <Navigate to="/painel" replace />

  async function reissue() {
    if (!selected || !password) return
    setLoading(true)
    setError(null)
    try {
      await ensureApiReady()
      await apiFetch<undefined>('/auth/reauth', {
        method: 'POST',
        body: JSON.stringify({ password }),
      })
      const result = await apiFetch<IssuedToken>(
        `/users/${encodeURIComponent(selected.userId)}/activation-token`,
        { method: 'POST' },
      )
      setIssued(result)
      setPassword('')
      setAccounts(await apiFetch<PendingUser[]>('/users/pending-activation'))
    } catch {
      setError('Senha incorreta ou reemissão indisponível. Tente novamente.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <Box>
      <PageHeader
        title="Ativações pendentes"
        subtitle="Consulte a validade e emita um novo código quando necessário."
      />
      {error && (
        <Typography role="alert" color="error" sx={{ mb: 2 }}>
          {error}
        </Typography>
      )}
      {accounts.length === 0 && <Typography>Nenhuma conta pendente.</Typography>}
      {accounts.map((account) => (
        <Paper
          key={account.userId}
          sx={{ p: 2, mb: 2, display: 'flex', gap: 2, alignItems: 'center', flexWrap: 'wrap' }}
        >
          <Box sx={{ flex: 1, minWidth: 180 }}>
            <Typography sx={{ fontWeight: 600 }}>
              {account.displayName} ({account.username})
            </Typography>
            <Typography>
              Validade:{' '}
              {account.expiresAt
                ? new Date(account.expiresAt).toLocaleString('pt-BR')
                : 'sem código válido'}
            </Typography>
          </Box>
          <Button
            onClick={() => {
              setSelected(account)
              setIssued(null)
              setError(null)
            }}
          >
            Reemitir código
          </Button>
        </Paper>
      ))}
      {selected && (
        <Paper sx={{ p: 3, mt: 3 }}>
          <Typography variant="h6">Reemitir código para {selected.displayName}</Typography>
          {!issued ? (
            <Box sx={{ mt: 2, maxWidth: 380 }}>
              <PasswordField
                label="Sua senha atual"
                value={password}
                onChange={(event) => setPassword(event.target.value)}
                autoComplete="current-password"
              />
              <Button
                variant="contained"
                disabled={loading || !password}
                onClick={() => void reissue()}
                sx={{ mt: 2 }}
              >
                Confirmar reemissão
              </Button>
            </Box>
          ) : (
            <Box sx={{ mt: 2 }}>
              <Typography>
                Copie e entregue o novo código ao usuário. Ele aparecerá apenas agora.
              </Typography>
              <Typography
                component="code"
                sx={{ display: 'block', overflowWrap: 'anywhere', my: 2 }}
              >
                {issued.activationToken}
              </Typography>
              <Button
                variant="contained"
                onClick={() => void navigator.clipboard.writeText(issued.activationToken)}
              >
                Copiar código
              </Button>
            </Box>
          )}
        </Paper>
      )}
    </Box>
  )
}
