import { useState } from 'react'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Grid from '@mui/material/Grid'
import Typography from '@mui/material/Typography'
import { useQueryClient } from '@tanstack/react-query'
import { Building, Database, Radio, Shield, ShieldCheck } from 'lucide-react'
import { useNavigate } from 'react-router'
import { testarFonte, useFonte, useRequisitosFonte } from '@/api/hooks'
import { sourceTestNotice } from '@/api/normalizers'
import { Checklist } from '@/components/data/ChecklistCard'
import { PageHeader } from '@/components/layout/PageHeader'
import { Callout } from '@/components/ui/Callout'
import { FilterSelect } from '@/components/ui/FilterSelect'
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

export function FonteDeDadosPage() {
  const { data, error, isError, isPending } = useFonte()
  const {
    data: requisitos,
    error: requisitosErrorValue,
    isError: requisitosError,
  } = useRequisitosFonte(data?.id)
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [tab, setTab] = useState('conexao')
  const [senhaAtual, setSenhaAtual] = useState('')
  const [testando, setTestando] = useState(false)
  const [erroTeste, setErroTeste] = useState<string | null>(null)

  if (isPending) return <PageSkeleton title="Configuração da Fonte de Dados" />
  if (isError) {
    return (
      <PageUnavailable
        title="Configuração da Fonte de Dados"
        subtitle="Configure a fonte de dados do município."
        error={error}
      />
    )
  }
  const fonte = data
  // The test runs against the registered configuration, so the fields only show it.
  const readOnly = { input: { readOnly: true } }

  async function testar() {
    if (!senhaAtual) return
    setTestando(true)
    setErroTeste(null)
    try {
      const resultado = await testarFonte(fonte.id, senhaAtual)
      setSenhaAtual('')
      setErroTeste(sourceTestNotice(resultado))
      await queryClient.invalidateQueries({ queryKey: ['fonte'] })
    } catch {
      setErroTeste('Senha incorreta ou teste indisponível. Tente novamente.')
    } finally {
      setTestando(false)
    }
  }

  return (
    <>
      <PageHeader
        title="Configuração da Fonte de Dados"
        subtitle="Configure a fonte de dados do município."
      />

      <UnderlineTabs
        items={tabs}
        value={tab}
        onChange={(k) => {
          if (k === 'isolamento') void navigate('/configuracoes/isolamento-municipal')
          else setTab(k)
        }}
        sx={{ mb: 2 }}
      />

      <Grid container spacing={2.5}>
        <Grid size={{ xs: 12, md: 7.2 }}>
          <SectionCard
            title="Dados da fonte"
            subtitle="Parâmetros da fonte cadastrada. O teste usa esta configuração."
            padding={3}
            headerSx={{ pb: 2.5 }}
            sx={{ height: '100%' }}
          >
            <Box
              component="form"
              onSubmit={(e) => {
                e.preventDefault()
                void testar()
              }}
              sx={{ display: 'flex', flexDirection: 'column', gap: 2.5 }}
            >
              <Box sx={{ display: 'flex', flexDirection: 'column', gap: 0.75 }}>
                <Typography sx={{ fontSize: 15, fontWeight: 600 }}>Família da fonte</Typography>
                <FilterSelect value={data.tipo} options={[data.tipo]} icon={Database} fullWidth />
              </Box>
              <Box sx={{ display: 'grid', gridTemplateColumns: '1.35fr 1fr', gap: 2.5 }}>
                <Field label="Host" value={data.host} slotProps={readOnly} />
                <Field label="Porta" value={data.porta} slotProps={readOnly} />
              </Box>
              <Field label="Nome do banco de dados" value={data.nomeBanco} slotProps={readOnly} />
              <Field label="Usuário" value={data.usuario} slotProps={readOnly} />
              <Box sx={{ borderTop: `1px solid ${colors.infoBorder}`, pt: 2.5 }}>
                <PasswordField
                  label="Senha da sua conta Esusdata"
                  value={senhaAtual}
                  onChange={(e) => setSenhaAtual(e.target.value)}
                  autoComplete="current-password"
                  helperText="Não é a senha do banco: confirma sua identidade para testar a fonte."
                />
              </Box>
              {erroTeste && (
                <Typography role="alert" color="error">
                  {erroTeste}
                </Typography>
              )}
              <Box
                sx={{
                  display: 'grid',
                  gridTemplateColumns: { xs: '1fr', sm: 'auto 1fr' },
                  gap: 2,
                  alignItems: 'stretch',
                  mt: 1,
                }}
              >
                <Button
                  type="submit"
                  variant="contained"
                  size="large"
                  disabled={testando || !senhaAtual}
                  startIcon={<Radio size={22} />}
                  sx={{ minHeight: 58, px: 3, fontSize: 17, fontWeight: 500 }}
                >
                  Testar fonte cadastrada
                </Button>
                {data.ultimoTeste && (
                  <Box
                    role="status"
                    sx={{
                      display: 'flex',
                      alignItems: 'center',
                      gap: 1.5,
                      px: 2,
                      py: 1,
                      bgcolor: data.ultimoTeste.ok ? colors.successBg : colors.errorBg,
                      border: `1px solid ${data.ultimoTeste.ok ? colors.successBorder : colors.error}`,
                      borderRadius: '10px',
                      color: data.ultimoTeste.ok ? colors.success : colors.error,
                      fontSize: 15,
                      fontWeight: 600,
                    }}
                  >
                    <Box
                      sx={{
                        width: 26,
                        height: 26,
                        borderRadius: '50%',
                        bgcolor: data.ultimoTeste.ok ? colors.success : colors.error,
                        color: '#fff',
                        display: 'grid',
                        placeItems: 'center',
                        fontSize: 14,
                        flexShrink: 0,
                      }}
                    >
                      {data.ultimoTeste.ok ? '✓' : '✗'}
                    </Box>
                    <span>
                      {data.ultimoTeste.mensagem}{' '}
                      <Box component="span" sx={{ fontWeight: 400 }}>
                        Testado em {new Date(data.ultimoTeste.testadoEm).toLocaleString('pt-BR')}.
                      </Box>
                    </span>
                  </Box>
                )}
              </Box>
            </Box>
          </SectionCard>
        </Grid>

        <Grid size={{ xs: 12, md: 4.8 }}>
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2.5 }}>
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
            <InfoCard icon={<Shield size={18} fill="#fff" />} title="Segurança">
              <Typography sx={{ fontSize: 15, color: colors.navy, lineHeight: 1.6, mb: 2.5 }}>
                As credenciais da fonte são armazenadas de forma segura e criptografada no sistema.
                A leitura é somente de consulta, sem alterações nos dados de origem.
              </Typography>
              <Box
                sx={{
                  bgcolor: '#fff',
                  borderRadius: '12px',
                  border: `1px solid ${colors.infoBorder}`,
                  p: 2,
                }}
              >
                <Callout variant="info" title="Seus dados estão protegidos" dense>
                  Utilizamos criptografia e boas práticas de segurança para manter suas informações
                  seguras.
                </Callout>
              </Box>
            </InfoCard>
          </Box>
        </Grid>
      </Grid>
    </>
  )
}
