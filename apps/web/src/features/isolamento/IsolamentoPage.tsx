import { useState } from 'react'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Typography from '@mui/material/Typography'
import { Check, ChevronRight, FileText, RefreshCw } from 'lucide-react'
import { useIsolamento } from '@/api/hooks'
import type { RegraValidacao } from '@/api/types'
import { CheckIcon } from '@/components/data/ChecklistCard'
import { DataTable, type Column } from '@/components/data/DataTable'
import { StatColumns } from '@/components/data/StatColumns'
import { PageHeader } from '@/components/layout/PageHeader'
import { Callout } from '@/components/ui/Callout'
import { PageSkeleton } from '@/components/ui/PageSkeleton'
import { SectionCard } from '@/components/ui/SectionCard'
import { StatusChip } from '@/components/ui/StatusChip'
import { UnderlineTabs } from '@/components/ui/Tabs'
import { formatInt } from '@/lib/format'
import { colors } from '@/theme/tokens'

const tabs = [
  { key: 'validacao', label: 'Validação do recorte' },
  { key: 'territorio', label: 'Território e equipes' },
]

function RuleIcon({ resultado }: { resultado: RegraValidacao['resultado'] }) {
  if (resultado === 'conforme') return <CheckIcon size={26} />
  return (
    <Box sx={{ width: 26, height: 26, borderRadius: '50%', bgcolor: colors.primary, color: '#fff', display: 'grid', placeItems: 'center', fontSize: 14, fontWeight: 700, flexShrink: 0 }}>
      i
    </Box>
  )
}

const columns: Column<RegraValidacao>[] = [
  {
    key: 'regra',
    header: 'Regra de validação',
    render: (r) => (
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
        <RuleIcon resultado={r.resultado} />
        <Typography sx={{ fontSize: 14, fontWeight: 600, color: colors.navy }}>{r.nome}</Typography>
      </Box>
    ),
  },
  { key: 'desc', header: 'Descrição', render: (r) => <Typography sx={{ fontSize: 13, color: colors.textSecondary, lineHeight: 1.4, maxWidth: 300 }}>{r.descricao}</Typography> },
  { key: 'resultado', header: 'Resultado', align: 'center', render: (r) => <StatusChip status={r.resultado} withIcon={false} /> },
  {
    key: 'detalhes',
    header: 'Detalhes',
    render: (r) => (
      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 2 }}>
        <Typography sx={{ fontSize: 13, color: colors.textSecondary }}>{r.detalhes}</Typography>
        <ChevronRight size={18} color={colors.primary} />
      </Box>
    ),
  },
]

export function IsolamentoPage() {
  const { data, isPending } = useIsolamento()
  const [tab, setTab] = useState('validacao')
  if (isPending || !data) return <PageSkeleton title="Isolamento Municipal" />

  return (
    <>
      <PageHeader title="Isolamento Municipal" subtitle="Garanta que os dados utilizados são apenas do município selecionado." lastUpdate />

      <UnderlineTabs items={tabs} value={tab} onChange={setTab} sx={{ mb: 2 }} />

      <Box sx={{ bgcolor: '#f4fbf6', border: `1px solid ${colors.successBorder}`, borderRadius: '14px', p: 2.5, mb: 2 }}>
        <Box sx={{ display: 'flex', gap: 3, alignItems: 'flex-start' }}>
          <Box sx={{ width: 100, height: 100, borderRadius: '50%', bgcolor: '#dff5e6', display: 'grid', placeItems: 'center', flexShrink: 0 }}>
            <Box sx={{ width: 60, height: 60, borderRadius: '50%', bgcolor: colors.success, color: '#fff', display: 'grid', placeItems: 'center' }}>
              <Check size={32} strokeWidth={3.5} />
            </Box>
          </Box>
          <Box sx={{ flex: 1 }}>
            <Typography sx={{ fontSize: 22, fontWeight: 700, color: colors.navy }}>Recorte municipal validado</Typography>
            <Typography sx={{ fontSize: 15, color: colors.textSecondary, mt: 0.5 }}>
              A base de dados está filtrada e contém apenas registros do município de <strong style={{ color: colors.navy }}>{data.municipioUf}</strong>.
            </Typography>
            <Box sx={{ mt: 2 }}>
              <StatColumns
                stats={[
                  { label: 'Município identificado', value: data.municipio },
                  { label: 'IBGE', value: data.ibge },
                  { label: 'Total de cadastros', value: formatInt(data.totalCadastros) },
                  { label: 'Última validação', value: data.ultimaValidacao },
                ]}
              />
            </Box>
          </Box>
        </Box>
        <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', md: '340px 1fr' }, gap: 2.5, mt: 2.5 }}>
          <Button variant="outlined" size="large" startIcon={<RefreshCw size={22} />} sx={{ minHeight: 60, fontSize: 17, fontWeight: 500, borderWidth: 1.5 }}>
            Validar novamente
          </Button>
          <Callout variant="info" title="Importante">
            A validação confirma apenas que os registros pertencem ao município selecionado.
            <br />
            Não avalia a qualidade, a completude ou a consistência clínica dos dados.
          </Callout>
        </Box>
      </Box>

      <SectionCard
        title="Regras de validação do recorte"
        subtitle="Verificações realizadas para garantir o isolamento dos dados do município selecionado."
        action={
          <Button variant="outlined" startIcon={<FileText size={18} />} sx={{ fontSize: 14 }}>
            Ver detalhes técnicos
          </Button>
        }
        sx={{ mb: 2 }}
      >
        <DataTable columns={columns} rows={data.regras} getRowKey={(r) => r.nome} bordered sx={{ '& th': { fontSize: 13.5, py: 1 }, '& td': { py: 0.9 } }} />
      </SectionCard>

      <Callout variant="info" iconStyle="filled" title="Sobre o isolamento municipal">
        Esta validação garante que os indicadores, relatórios e análises utilizarão apenas os registros do município selecionado.
        <br />
        Para alterações de município, utilize o seletor no topo da página e valide novamente o recorte.
      </Callout>
    </>
  )
}
