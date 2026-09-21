import { useState } from 'react'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Grid from '@mui/material/Grid'
import Typography from '@mui/material/Typography'
import { Building, Database, Radio, Shield, ShieldCheck } from 'lucide-react'
import { useNavigate } from 'react-router'
import { useFonteConexao, useRequisitosFonte } from '@/api/hooks'
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

function InfoCard({ icon, title, children }: { icon: React.ReactNode; title: string; children: React.ReactNode }) {
  return (
    <Box sx={{ bgcolor: colors.primarySoft, border: `1px solid ${colors.infoBorder}`, borderRadius: '14px', p: 2.5 }}>
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, mb: 2 }}>
        <Box sx={{ width: 36, height: 36, borderRadius: '50%', bgcolor: colors.primary, color: '#fff', display: 'grid', placeItems: 'center' }}>{icon}</Box>
        <Typography sx={{ fontSize: 20, fontWeight: 700, color: colors.primary }}>{title}</Typography>
      </Box>
      {children}
    </Box>
  )
}

export function FonteDeDadosPage() {
  const { data, error, isError, isPending } = useFonteConexao()
  const { data: requisitos, error: requisitosErrorValue, isError: requisitosError } = useRequisitosFonte()
  const navigate = useNavigate()
  const [tab, setTab] = useState('conexao')
  const [form, setForm] = useState<Record<string, string> | null>(null)

  if (isPending) return <PageSkeleton title="Configuração da Fonte de Dados" />
  if (isError || !data) {
    return (
      <PageUnavailable
        title="Configuração da Fonte de Dados"
        subtitle="Configure a conexão com o banco de dados do e-SUS PEC."
        error={error}
      />
    )
  }
  const values = form ?? { host: data.host, porta: data.porta, banco: data.banco, usuario: data.usuario, senha: data.senha }
  const set = (k: string) => (e: React.ChangeEvent<HTMLInputElement>) => setForm({ ...values, [k]: e.target.value })

  return (
    <>
      <PageHeader title="Configuração da Fonte de Dados" subtitle="Configure a conexão com o banco de dados do e-SUS PEC." />

      <UnderlineTabs
        items={tabs}
        value={tab}
        onChange={(k) => (k === 'isolamento' ? navigate('/configuracoes/isolamento-municipal') : setTab(k))}
        sx={{ mb: 2 }}
      />

      <Grid container spacing={2.5}>
        <Grid size={{ xs: 12, md: 7.2 }}>
          <SectionCard title="Dados da conexão" subtitle="Informe os dados de acesso ao banco de dados do e-SUS PEC." padding={3} headerSx={{ pb: 2.5 }} sx={{ height: '100%' }}>
            <Box component="form" onSubmit={(e) => e.preventDefault()} sx={{ display: 'flex', flexDirection: 'column', gap: 2.5 }}>
              <Box sx={{ display: 'flex', flexDirection: 'column', gap: 0.75 }}>
                <Typography sx={{ fontSize: 15, fontWeight: 600 }}>Tipo de banco</Typography>
                <FilterSelect value={data.tipo} options={[data.tipo]} icon={Database} fullWidth />
              </Box>
              <Box sx={{ display: 'grid', gridTemplateColumns: '1.35fr 1fr', gap: 2.5 }}>
                <Field label="Host" value={values.host} onChange={set('host')} />
                <Field label="Porta" value={values.porta} onChange={set('porta')} />
              </Box>
              <Field label="Banco de dados" value={values.banco} onChange={set('banco')} />
              <Field label="Usuário" value={values.usuario} onChange={set('usuario')} autoComplete="off" />
              <PasswordField label="Senha" value={values.senha} onChange={set('senha')} autoComplete="new-password" />
              <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: 'auto 1fr' }, gap: 2, alignItems: 'stretch', mt: 1 }}>
                <Button type="submit" variant="contained" size="large" startIcon={<Radio size={22} />} sx={{ minHeight: 58, px: 3, fontSize: 17, fontWeight: 500 }}>
                  Testar conexão
                </Button>
                {data.ultimoTeste && (
                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, px: 2, bgcolor: colors.successBg, border: `1px solid ${colors.successBorder}`, borderRadius: '10px', color: colors.success, fontSize: 15, fontWeight: 600 }}>
                    <Box sx={{ width: 26, height: 26, borderRadius: '50%', bgcolor: colors.success, color: '#fff', display: 'grid', placeItems: 'center', fontSize: 14, flexShrink: 0 }}>✓</Box>
                    {data.ultimoTeste.mensagem}
                  </Box>
                )}
              </Box>
            </Box>
          </SectionCard>
        </Grid>

        <Grid size={{ xs: 12, md: 4.8 }}>
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2.5 }}>
            <InfoCard icon={<Typography sx={{ fontWeight: 700, fontSize: 18, lineHeight: 1 }}>i</Typography>} title="Requisitos">
              {requisitosError ? (
                <Callout variant="warning" title="Requisitos indisponíveis" dense>
                  {requisitosErrorValue instanceof Error
                    ? requisitosErrorValue.message
                    : 'A API não fornece os requisitos da fonte neste momento.'}
                </Callout>
              ) : (
                <Checklist items={(requisitos ?? []).map((r) => ({ label: r.label, ok: r.ok }))} size="lg" />
              )}
            </InfoCard>
            <InfoCard icon={<Shield size={18} fill="#fff" />} title="Segurança">
              <Typography sx={{ fontSize: 15, color: colors.navy, lineHeight: 1.6, mb: 2.5 }}>
                As credenciais são armazenadas de forma segura e criptografada no sistema. O acesso ao banco de dados é somente de leitura, não sendo permitidas alterações nos dados.
              </Typography>
              <Box sx={{ bgcolor: '#fff', borderRadius: '12px', border: `1px solid ${colors.infoBorder}`, p: 2 }}>
                <Callout variant="info" title="Seus dados estão protegidos" dense>
                  Utilizamos criptografia e boas práticas de segurança para manter suas informações seguras.
                </Callout>
              </Box>
            </InfoCard>
          </Box>
        </Grid>
      </Grid>
    </>
  )
}
