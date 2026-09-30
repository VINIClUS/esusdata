import { useState } from 'react'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Grid from '@mui/material/Grid'
import Typography from '@mui/material/Typography'
import { useQueryClient } from '@tanstack/react-query'
import { Building, CalendarSearch, Database, Radio, Save, Shield, ShieldCheck } from 'lucide-react'
import { useNavigate, useSearchParams } from 'react-router'
import { ApiError } from '@/api/client'
import {
  salvarFonte,
  testarFonte,
  useFonte,
  useRequisitosFonte,
  verificarCobertura,
} from '@/api/hooks'
import { normalizeCoverage, sourceTestNotice } from '@/api/normalizers'
import type { Fonte } from '@/api/types'
import { Checklist } from '@/components/data/ChecklistCard'
import { PageHeader } from '@/components/layout/PageHeader'
import { Callout } from '@/components/ui/Callout'
import { Field, PasswordField } from '@/components/ui/Inputs'
import { PageSkeleton } from '@/components/ui/PageSkeleton'
import { PageUnavailable } from '@/components/ui/PageUnavailable'
import { SectionCard } from '@/components/ui/SectionCard'
import { UnderlineTabs } from '@/components/ui/Tabs'
import { colors } from '@/theme/tokens'

const tabs = [
  { key: 'conexao', label: 'Conexão', icon: Database },
  { key: 'teste', label: 'Teste e Validação', icon: ShieldCheck },
  { key: 'isolamento', label: 'Isolamento Municipal', icon: Building },
]

function InfoCard({
  icon,
  title,
  children,
}: {
  icon: React.ReactNode
  title: string
  children: React.ReactNode
}) {
  return (
    <Box
      sx={{
        bgcolor: colors.primarySoft,
        border: `1px solid ${colors.infoBorder}`,
        borderRadius: '14px',
        p: 2.5,
      }}
    >
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, mb: 2 }}>
        <Box
          sx={{
            width: 36,
            height: 36,
            borderRadius: '50%',
            bgcolor: colors.primary,
            color: '#fff',
            display: 'grid',
            placeItems: 'center',
          }}
        >
          {icon}
        </Box>
        <Typography sx={{ fontSize: 20, fontWeight: 700, color: colors.primary }}>
          {title}
        </Typography>
      </Box>
      {children}
    </Box>
  )
}

const TITLE = 'Configuração da Fonte de Dados'
const SUBTITLE = 'Configure a fonte de dados do município.'

function ResultadoBox({ ok, children }: { ok: boolean; children: React.ReactNode }) {
  return (
    <Box
      role="status"
      sx={{
        display: 'flex',
        alignItems: 'center',
        gap: 1.5,
        px: 2,
        py: 1,
        bgcolor: ok ? colors.successBg : colors.errorBg,
        border: `1px solid ${ok ? colors.successBorder : colors.error}`,
        borderRadius: '10px',
        color: ok ? colors.success : colors.error,
        fontSize: 15,
        fontWeight: 600,
      }}
    >
      <Box
        sx={{
          width: 26,
          height: 26,
          borderRadius: '50%',
          bgcolor: ok ? colors.success : colors.error,
          color: '#fff',
          display: 'grid',
          placeItems: 'center',
          fontSize: 14,
          flexShrink: 0,
        }}
      >
        {ok ? '✓' : '✗'}
      </Box>
      <span>{children}</span>
    </Box>
  )
}

const quando = (iso: string) => new Date(iso).toLocaleString('pt-BR')

interface Conexao {
  host: string
  porta: string
  nomeBanco: string
  usuario: string
}

function conexaoDe(fonte: Fonte): Conexao {
  return {
    host: fonte.host,
    porta: fonte.porta,
    nomeBanco: fonte.nomeBanco,
    usuario: fonte.usuario,
  }
}

function erroConexao({ host, porta, nomeBanco, usuario }: Conexao): string | null {
  if (!host.trim() || !nomeBanco.trim() || !usuario.trim()) {
    return 'Informe host, nome do banco e usuário.'
  }
  const numero = Number(porta)
  if (!/^\d+$/.test(porta) || numero < 1 || numero > 65535) {
    return 'Informe uma porta entre 1 e 65535.'
  }
  return null
}

function mensagemDeErro(error: unknown, fallback: string): string {
  if (error instanceof ApiError && error.status === 401) return 'Senha incorreta. Tente novamente.'
  if (error instanceof ApiError && error.status === 400) return `Dados recusados: ${error.message}`
  return fallback
}

export function FonteDeDadosPage() {
  const { data, error, isError, isPending } = useFonte()
  const {
    data: requisitos,
    error: requisitosErrorValue,
    isError: requisitosError,
  } = useRequisitosFonte(data?.id)
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  // In the URL, so a reload keeps the tab and other screens can link to the test.
  const [params, setParams] = useSearchParams()
  const tab = params.get('aba') === 'teste' ? 'teste' : 'conexao'
  const [senhaAtual, setSenhaAtual] = useState('')
  const [enviando, setEnviando] = useState<'salvar' | 'testar' | 'cobertura' | null>(null)
  const [aviso, setAviso] = useState<{ ok: boolean; texto: string } | null>(null)
  // null while the form shows the registered configuration as is.
  const [rascunho, setRascunho] = useState<Conexao | null>(null)

  if (isPending) return <PageSkeleton title={TITLE} />
  if (isError) return <PageUnavailable title={TITLE} subtitle={SUBTITLE} error={error} />
  const fonte = data
  const conexao = rascunho ?? conexaoDe(fonte)
  const invalida = erroConexao(conexao)
  const readOnly = { input: { readOnly: true } }

  function editar(campo: keyof Conexao, valor: string) {
    setRascunho({ ...conexao, [campo]: valor })
    setAviso(null)
  }

  /** Runs one action behind the reauth, then refreshes every screen that reads the source. */
  async function comSenha(
    acao: 'salvar' | 'testar' | 'cobertura',
    executar: () => Promise<{ ok: boolean; texto: string } | null>,
    fallback: string,
  ) {
    if (!senhaAtual) return
    setEnviando(acao)
    setAviso(null)
    try {
      setAviso(await executar())
      setSenhaAtual('')
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['fonte'] }),
        queryClient.invalidateQueries({ queryKey: ['execucao', 'fontes'] }),
        queryClient.invalidateQueries({ queryKey: ['painel'] }),
      ])
    } catch (e) {
      setAviso({ ok: false, texto: mensagemDeErro(e, fallback) })
    } finally {
      setEnviando(null)
    }
  }

  const salvar = () =>
    comSenha(
      'salvar',
      async () => {
        const salvo = await salvarFonte(
          {
            ...fonte.cadastro,
            host: conexao.host.trim(),
            port: Number(conexao.porta),
            databaseName: conexao.nomeBanco.trim(),
            dbUser: conexao.usuario.trim(),
          },
          senhaAtual,
        )
        setRascunho(null)
        return {
          ok: true,
          texto: `Configuração salva como versão ${salvo.sourceConfigurationVersion}. Teste a fonte e verifique a cobertura na aba Teste e Validação.`,
        }
      },
      'Não foi possível salvar a fonte. Tente novamente.',
    )

  const testar = () =>
    comSenha(
      'testar',
      async () => {
        const notice = sourceTestNotice(await testarFonte(fonte.id, senhaAtual))
        return notice ? { ok: false, texto: notice } : null
      },
      'Senha incorreta ou teste indisponível. Tente novamente.',
    )

  const verificar = () =>
    comSenha(
      'cobertura',
      async () => {
        const coverage = await verificarCobertura(fonte.id, senhaAtual)
        // Every outcome but a busy source is stored and read back in the coverage card.
        return coverage.outcome === 'SOURCE_BUSY'
          ? { ok: false, texto: normalizeCoverage(coverage).mensagem }
          : null
      },
      'Senha incorreta ou verificação indisponível. Tente novamente.',
    )

  const senhaField = (helperText: string) => (
    <PasswordField
      label="Senha da sua conta Esusdata"
      value={senhaAtual}
      onChange={(e) => setSenhaAtual(e.target.value)}
      autoComplete="current-password"
      helperText={helperText}
    />
  )

  const avisoView = aviso && (
    <Typography role={aviso.ok ? 'status' : 'alert'} color={aviso.ok ? 'success' : 'error'}>
      {aviso.texto}
    </Typography>
  )

  return (
    <>
      <PageHeader title={TITLE} subtitle={SUBTITLE} />

      <UnderlineTabs
        items={tabs}
        value={tab}
        onChange={(k) => {
          if (k === 'isolamento') void navigate('/configuracoes/isolamento-municipal')
          else {
            setParams(k === 'teste' ? { aba: 'teste' } : {}, { replace: true })
            setAviso(null)
          }
        }}
        sx={{ mb: 2 }}
      />

      {tab === 'conexao' ? (
        <Grid container spacing={2.5}>
          <Grid size={{ xs: 12, md: 7.2 }}>
            <SectionCard
              title="Conexão com o banco do PEC"
              subtitle={`Fonte ${fonte.id} · ${fonte.tipo} · versão ${fonte.versao} da configuração.`}
              padding={3}
              headerSx={{ pb: 2.5 }}
              sx={{ height: '100%' }}
            >
              <Box
                component="form"
                onSubmit={(e) => {
                  e.preventDefault()
                  if (!invalida && rascunho) void salvar()
                }}
                sx={{ display: 'flex', flexDirection: 'column', gap: 2.5 }}
              >
                <Box sx={{ display: 'grid', gridTemplateColumns: '1.35fr 1fr', gap: 2.5 }}>
                  <Field
                    label="Host"
                    value={conexao.host}
                    onChange={(e) => editar('host', e.target.value)}
                  />
                  <Field
                    label="Porta"
                    value={conexao.porta}
                    onChange={(e) => editar('porta', e.target.value.trim())}
                    slotProps={{ htmlInput: { inputMode: 'numeric' } }}
                  />
                </Box>
                <Field
                  label="Nome do banco de dados"
                  value={conexao.nomeBanco}
                  onChange={(e) => editar('nomeBanco', e.target.value)}
                />
                <Field
                  label="Usuário"
                  value={conexao.usuario}
                  onChange={(e) => editar('usuario', e.target.value)}
                />
                <Box sx={{ display: 'grid', gridTemplateColumns: '1.35fr 1fr', gap: 2.5 }}>
                  <Field
                    label="Senha do banco (referência)"
                    value={fonte.secretRef}
                    slotProps={readOnly}
                    helperText="Onde o servidor lê a senha. A senha não passa pela interface."
                  />
                  <Field
                    label="IBGE do município"
                    value={fonte.municipioIbge}
                    slotProps={readOnly}
                  />
                </Box>
                {rascunho && invalida && (
                  <Typography role="alert" color="error">
                    {invalida}
                  </Typography>
                )}
                {rascunho && !invalida && (
                  <Callout
                    variant="warning"
                    title={`Salvar cria a versão ${fonte.versao + 1}`}
                    dense
                  >
                    O último teste, o isolamento e a cobertura deixam de valer até serem refeitos, e
                    o agendador só volta a executar depois de uma nova cobertura.
                  </Callout>
                )}
                <Box sx={{ borderTop: `1px solid ${colors.infoBorder}`, pt: 2.5 }}>
                  {senhaField('Não é a senha do banco: confirma sua identidade para salvar.')}
                </Box>
                {avisoView}
                <Box sx={{ display: 'flex', gap: 2, flexWrap: 'wrap' }}>
                  <Button
                    type="submit"
                    variant="contained"
                    size="large"
                    disabled={!rascunho || !!invalida || !senhaAtual || enviando !== null}
                    startIcon={<Save size={20} />}
                    sx={{ minHeight: 50, px: 3 }}
                  >
                    Salvar alterações
                  </Button>
                  <Button
                    size="large"
                    disabled={!rascunho || enviando !== null}
                    onClick={() => setRascunho(null)}
                  >
                    Descartar
                  </Button>
                </Box>
              </Box>
            </SectionCard>
          </Grid>
          <Grid size={{ xs: 12, md: 4.8 }}>
            <InfoCard icon={<Shield size={18} fill="#fff" />} title="Segurança">
              <Typography sx={{ fontSize: 15, color: colors.navy, lineHeight: 1.6, mb: 2.5 }}>
                A senha do banco fica no servidor, fora da interface e do banco do Esusdata. A
                leitura é somente de consulta, sem alterações nos dados de origem.
              </Typography>
              <Box
                sx={{
                  bgcolor: '#fff',
                  borderRadius: '12px',
                  border: `1px solid ${colors.infoBorder}`,
                  p: 2,
                }}
              >
                <Callout variant="info" title="Destinos liberados" dense>
                  O servidor só se conecta a destinos liberados na configuração da instalação; um
                  host novo precisa ser liberado lá antes do teste.
                </Callout>
              </Box>
            </InfoCard>
          </Grid>
        </Grid>
      ) : (
        <Grid container spacing={2.5}>
          <Grid size={{ xs: 12, md: 7.2 }}>
            <SectionCard
              title="Teste e validação"
              subtitle={`${fonte.host}:${fonte.porta} · ${fonte.nomeBanco} · versão ${fonte.versao} da configuração.`}
              padding={3}
              headerSx={{ pb: 2.5 }}
              sx={{ height: '100%' }}
            >
              <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2.5 }}>
                <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
                  <Typography sx={{ fontSize: 17, fontWeight: 700, color: colors.navy }}>
                    Conexão
                  </Typography>
                  {fonte.ultimoTeste ? (
                    <ResultadoBox ok={fonte.ultimoTeste.ok}>
                      {fonte.ultimoTeste.mensagem}{' '}
                      <Box component="span" sx={{ fontWeight: 400 }}>
                        Testado em {quando(fonte.ultimoTeste.testadoEm)}.
                      </Box>
                    </ResultadoBox>
                  ) : (
                    <Typography sx={{ color: colors.textSecondary }}>
                      Esta versão da configuração ainda não foi testada.
                    </Typography>
                  )}
                </Box>
                <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
                  <Typography sx={{ fontSize: 17, fontWeight: 700, color: colors.navy }}>
                    Cobertura de competências
                  </Typography>
                  {fonte.cobertura ? (
                    <>
                      <ResultadoBox ok={fonte.cobertura.ok}>
                        {fonte.cobertura.mensagem}{' '}
                        <Box component="span" sx={{ fontWeight: 400 }}>
                          Verificada em {quando(fonte.cobertura.verificadaEm)}.
                        </Box>
                      </ResultadoBox>
                      {fonte.cobertura.competencias.length > 0 && (
                        <Box
                          component="ul"
                          aria-label="Competências com atendimentos"
                          sx={{ display: 'flex', flexWrap: 'wrap', gap: 1, p: 0, m: 0 }}
                        >
                          {fonte.cobertura.competencias.map((c) => (
                            <Box
                              component="li"
                              key={c.periodo}
                              sx={{
                                listStyle: 'none',
                                px: 1.25,
                                py: 0.5,
                                borderRadius: '8px',
                                bgcolor: colors.primarySoft,
                                fontSize: 14,
                              }}
                            >
                              {c.label}
                            </Box>
                          ))}
                        </Box>
                      )}
                    </>
                  ) : (
                    <Typography sx={{ color: colors.textSecondary }}>
                      Nenhuma cobertura verificada nesta versão: o agendador e a execução não sabem
                      quais competências têm dados.
                    </Typography>
                  )}
                </Box>
                <Box sx={{ borderTop: `1px solid ${colors.infoBorder}`, pt: 2.5 }}>
                  {senhaField('Não é a senha do banco: confirma sua identidade para ler o PEC.')}
                </Box>
                {avisoView}
                <Box sx={{ display: 'flex', gap: 2, flexWrap: 'wrap' }}>
                  <Button
                    variant="contained"
                    size="large"
                    disabled={enviando !== null || !senhaAtual}
                    startIcon={<Radio size={20} />}
                    onClick={() => void testar()}
                    sx={{ minHeight: 50, px: 3 }}
                  >
                    Testar fonte cadastrada
                  </Button>
                  <Button
                    variant="outlined"
                    size="large"
                    disabled={enviando !== null || !senhaAtual}
                    startIcon={<CalendarSearch size={20} />}
                    onClick={() => void verificar()}
                    sx={{ minHeight: 50, px: 3 }}
                  >
                    Verificar cobertura
                  </Button>
                  <Button
                    size="large"
                    startIcon={<Building size={20} />}
                    onClick={() => void navigate('/configuracoes/isolamento-municipal')}
                  >
                    Validar isolamento
                  </Button>
                </Box>
              </Box>
            </SectionCard>
          </Grid>
          <Grid size={{ xs: 12, md: 4.8 }}>
            <InfoCard
              icon={
                <Typography sx={{ fontWeight: 700, fontSize: 18, lineHeight: 1 }}>i</Typography>
              }
              title="Requisitos"
            >
              {requisitosError ? (
                <Callout variant="warning" title="Requisitos indisponíveis" dense>
                  {requisitosErrorValue instanceof Error
                    ? requisitosErrorValue.message
                    : 'A API não fornece os requisitos da fonte neste momento.'}
                </Callout>
              ) : (
                <Checklist
                  items={(requisitos ?? []).map((r) => ({ label: r.label, ok: r.ok }))}
                  size="lg"
                />
              )}
            </InfoCard>
          </Grid>
        </Grid>
      )}
    </>
  )
}
