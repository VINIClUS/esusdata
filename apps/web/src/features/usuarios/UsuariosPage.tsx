import { useState } from 'react'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Chip from '@mui/material/Chip'
import Dialog from '@mui/material/Dialog'
import DialogActions from '@mui/material/DialogActions'
import DialogContent from '@mui/material/DialogContent'
import DialogTitle from '@mui/material/DialogTitle'
import Paper from '@mui/material/Paper'
import Typography from '@mui/material/Typography'
import { useQueryClient } from '@tanstack/react-query'
import { Navigate, Link as RouterLink } from 'react-router'
import { UserPlus } from 'lucide-react'
import { ApiError } from '@/api/client'
import type { CreateUserResponse, GrantResponse, StatusKey, UserResponse } from '@/api/types'
import { useAuth } from '@/app/auth-context'
import { useScope } from '@/app/scope-context'
import { PageHeader } from '@/components/layout/PageHeader'
import { Field, PasswordField } from '@/components/ui/Inputs'
import { PageSkeleton } from '@/components/ui/PageSkeleton'
import { PageUnavailable } from '@/components/ui/PageUnavailable'
import { SectionCard } from '@/components/ui/SectionCard'
import { StatusChip } from '@/components/ui/StatusChip'
import { colors } from '@/theme/tokens'
import {
  alterarBloqueio,
  comSenha,
  concederGestor,
  criarUsuario,
  revogarAcesso,
  useUsuarios,
} from './usuarios-api'

const TITLE = 'Usuários'
const SUBTITLE = 'Crie contas, conceda o papel de gestor por município e bloqueie acessos.'

const states: Record<UserResponse['state'], { status: StatusKey; label: string }> = {
  ACTIVE: { status: 'conforme', label: 'Ativo' },
  PENDING_ACTIVATION: { status: 'atencao', label: 'Aguardando ativação' },
  BLOCKED: { status: 'critico', label: 'Bloqueado' },
}

const roles: Record<GrantResponse['role'], string> = {
  TECHNICAL_ADMIN: 'Administrador técnico',
  MANAGER: 'Gestor',
  TEAM_SCOPED_PROFESSIONAL: 'Profissional de equipe',
  AUDITOR: 'Auditor',
}

function grantLabel(grant: GrantResponse): string {
  const scope = grant.scopeKind === 'INSTALLATION' ? 'instalação' : `IBGE ${grant.municipalityIbge}`
  return `${roles[grant.role]} · ${scope}`
}

/** One access change waiting for the admin's password. */
interface Acao {
  titulo: string
  executar: () => Promise<unknown>
  depois?: (resultado: unknown) => void
}

function mensagemDeErro(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 401) return 'Senha incorreta. Tente novamente.'
    if (error.code === 'SELF_GRANT_FORBIDDEN') return 'Você não pode alterar o próprio acesso.'
    if (error.code === 'SELF_BLOCK_FORBIDDEN') return 'Você não pode bloquear a própria conta.'
    if (error.status === 409) return 'Já existe uma conta com esse usuário.'
    if (error.status === 400) return `Dados recusados: ${error.message}`
  }
  return 'Não foi possível concluir. Tente novamente.'
}

/**
 * The users screen (MANAGE_ACCESS). Every change goes through one password dialog, since the API
 * wants a recent reauthentication; a wrong password keeps the dialog open to try again.
 */
export function UsuariosPage() {
  const { user } = useAuth()
  const { municipalityIbge } = useScope()
  const queryClient = useQueryClient()
  const usuarios = useUsuarios(!!user?.canManageAccess)
  const [acao, setAcao] = useState<Acao | null>(null)
  const [senha, setSenha] = useState('')
  const [enviando, setEnviando] = useState(false)
  const [erro, setErro] = useState<string | null>(null)
  const [novo, setNovo] = useState({ username: '', displayName: '' })
  const [criado, setCriado] = useState<(CreateUserResponse & { username: string }) | null>(null)
  const [ibge, setIbge] = useState<Record<string, string>>({})

  if (!user?.canManageAccess) return <Navigate to="/painel" replace />
  if (usuarios.isPending) return <PageSkeleton title={TITLE} />
  if (usuarios.isError) {
    return <PageUnavailable title={TITLE} subtitle={SUBTITLE} error={usuarios.error} />
  }

  function pedir(nova: Acao) {
    setAcao(nova)
    setSenha('')
    setErro(null)
  }

  async function confirmar() {
    if (!acao || !senha) return
    setEnviando(true)
    setErro(null)
    try {
      const resultado = await comSenha(senha, acao.executar)
      acao.depois?.(resultado)
      setAcao(null)
      setSenha('')
      await queryClient.invalidateQueries({ queryKey: ['usuarios'] })
    } catch (error) {
      setErro(mensagemDeErro(error))
    } finally {
      setEnviando(false)
    }
  }

  const ibgeValido = (value: string | undefined) => /^\d{7}$/.test(value ?? '')

  return (
    <>
      <PageHeader title={TITLE} subtitle={SUBTITLE} />

      <SectionCard icon={<UserPlus size={22} />} title="Nova conta" sx={{ mb: 2 }}>
        <Box
          component="form"
          onSubmit={(e) => {
            e.preventDefault()
            const { username, displayName } = novo
            pedir({
              titulo: `Criar a conta ${username}`,
              executar: () => criarUsuario(username, displayName),
              depois: (resultado) => {
                setCriado({ ...(resultado as CreateUserResponse), username })
                setNovo({ username: '', displayName: '' })
              },
            })
          }}
          sx={{
            display: 'grid',
            gridTemplateColumns: { xs: '1fr', md: '1fr 1fr auto' },
            gap: 2,
            alignItems: 'end',
          }}
        >
          <Field
            label="Usuário"
            value={novo.username}
            onChange={(e) => setNovo((n) => ({ ...n, username: e.target.value.trim() }))}
            autoComplete="off"
          />
          <Field
            label="Nome"
            value={novo.displayName}
            onChange={(e) => setNovo((n) => ({ ...n, displayName: e.target.value }))}
            autoComplete="off"
          />
          <Button
            type="submit"
            variant="contained"
            size="large"
            disabled={!novo.username || !novo.displayName.trim()}
            sx={{ minHeight: 50 }}
          >
            Criar conta
          </Button>
        </Box>
        {criado && (
          <Paper variant="outlined" sx={{ p: 2, mt: 2 }} role="status">
            <Typography sx={{ fontWeight: 600 }}>
              Conta {criado.username} criada. Entregue este código de ativação ao usuário; ele
              aparece só agora e vale até {new Date(criado.expiresAt).toLocaleString('pt-BR')}.
            </Typography>
            <Typography
              component="code"
              sx={{ display: 'block', overflowWrap: 'anywhere', my: 1.5 }}
            >
              {criado.activationToken}
            </Typography>
            <Box sx={{ display: 'flex', gap: 1 }}>
              <Button
                variant="contained"
                onClick={() => void navigator.clipboard.writeText(criado.activationToken)}
              >
                Copiar código
              </Button>
              <Button onClick={() => setCriado(null)}>Fechar</Button>
            </Box>
          </Paper>
        )}
      </SectionCard>

      <SectionCard title="Contas" subtitle="O papel de gestor dá leitura e execução no município.">
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1.5 }}>
          {usuarios.data.map((conta) => {
            const voce = conta.userId === user.userId
            const state = states[conta.state]
            return (
              <Paper
                key={conta.userId}
                variant="outlined"
                sx={{ p: 2 }}
                aria-label={`Conta ${conta.username}`}
                role="group"
              >
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, flexWrap: 'wrap' }}>
                  <Typography sx={{ fontWeight: 700, color: colors.navy }}>
                    {conta.displayName} ({conta.username}){voce ? ' · você' : ''}
                  </Typography>
                  <StatusChip
                    status={state.status}
                    label={state.label}
                    withIcon={false}
                    size="sm"
                  />
                  <Box sx={{ flex: 1 }} />
                  {conta.state === 'PENDING_ACTIVATION' && (
                    <Button component={RouterLink} to="/ativacoes-pendentes" size="small">
                      Reemitir código
                    </Button>
                  )}
                  {!voce && (
                    <Button
                      size="small"
                      color={conta.state === 'BLOCKED' ? 'primary' : 'error'}
                      onClick={() =>
                        pedir({
                          titulo:
                            conta.state === 'BLOCKED'
                              ? `Desbloquear ${conta.username}`
                              : `Bloquear ${conta.username} (encerra as sessões dela)`,
                          executar: () => alterarBloqueio(conta.userId, conta.state !== 'BLOCKED'),
                        })
                      }
                    >
                      {conta.state === 'BLOCKED' ? 'Desbloquear' : 'Bloquear'}
                    </Button>
                  )}
                </Box>
                <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap', mt: 1 }}>
                  {conta.grants.length === 0 && (
                    <Typography sx={{ fontSize: 14, color: colors.textSecondary }}>
                      Sem acesso concedido.
                    </Typography>
                  )}
                  {conta.grants.map((grant) => (
                    <Chip
                      key={grant.grantId}
                      label={grantLabel(grant)}
                      onDelete={
                        voce
                          ? undefined
                          : () =>
                              pedir({
                                titulo: `Revogar "${grantLabel(grant)}" de ${conta.username}`,
                                executar: () => revogarAcesso(conta.userId, grant.grantId),
                              })
                      }
                    />
                  ))}
                </Box>
                {!voce && conta.state !== 'BLOCKED' && (
                  <Box
                    component="form"
                    onSubmit={(e) => {
                      e.preventDefault()
                      const codigo = ibge[conta.userId] ?? municipalityIbge ?? ''
                      pedir({
                        titulo: `Conceder o papel de gestor de ${codigo} a ${conta.username}`,
                        executar: () => concederGestor(conta.userId, codigo),
                      })
                    }}
                    sx={{ display: 'flex', gap: 1.5, alignItems: 'end', mt: 1.5, flexWrap: 'wrap' }}
                  >
                    <Box sx={{ width: 200 }}>
                      <Field
                        label="IBGE do município"
                        value={ibge[conta.userId] ?? municipalityIbge ?? ''}
                        onChange={(e) =>
                          setIbge((m) => ({ ...m, [conta.userId]: e.target.value.trim() }))
                        }
                        slotProps={{ htmlInput: { inputMode: 'numeric', maxLength: 7 } }}
                      />
                    </Box>
                    <Button
                      type="submit"
                      variant="outlined"
                      disabled={!ibgeValido(ibge[conta.userId] ?? municipalityIbge)}
                      sx={{ minHeight: 50 }}
                    >
                      Conceder gestor
                    </Button>
                  </Box>
                )}
              </Paper>
            )
          })}
        </Box>
      </SectionCard>

      <Dialog
        open={acao !== null}
        onClose={() => !enviando && setAcao(null)}
        fullWidth
        maxWidth="xs"
      >
        <Box
          component="form"
          onSubmit={(e) => {
            e.preventDefault()
            void confirmar()
          }}
        >
          <DialogTitle>{acao?.titulo}</DialogTitle>
          <DialogContent>
            <Typography sx={{ mb: 2, fontSize: 14, color: colors.textSecondary }}>
              Confirme com a senha da sua conta Esusdata.
            </Typography>
            <PasswordField
              label="Sua senha"
              value={senha}
              onChange={(e) => setSenha(e.target.value)}
              autoComplete="current-password"
              autoFocus
            />
            {erro && (
              <Typography role="alert" color="error" sx={{ mt: 1.5 }}>
                {erro}
              </Typography>
            )}
          </DialogContent>
          <DialogActions>
            <Button onClick={() => setAcao(null)} disabled={enviando}>
              Cancelar
            </Button>
            <Button type="submit" variant="contained" disabled={enviando || !senha}>
              Confirmar
            </Button>
          </DialogActions>
        </Box>
      </Dialog>
    </>
  )
}
