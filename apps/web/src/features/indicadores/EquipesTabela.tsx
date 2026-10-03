import Typography from '@mui/material/Typography'
import type { EquipeResultado, ValueKind } from '@/api/types'
import { DataTable, type Column } from '@/components/data/DataTable'
import { SectionCard } from '@/components/ui/SectionCard'
import { StatusChip } from '@/components/ui/StatusChip'
import { colors } from '@/theme/tokens'
import { rotulosDoPar } from './rotulos'

const texto = (valor: string | null, forte = false) => (
  <Typography
    sx={{
      fontSize: 13.5,
      color: colors.navy,
      fontWeight: forte ? 700 : 400,
      whiteSpace: 'nowrap',
    }}
  >
    {valor ?? '—'}
  </Typography>
)

/**
 * The same result per team (INE), the granularity of the fichas. The records no team holds are
 * kept apart ("Sem equipe"), never folded into another team.
 */
export function EquipesTabela({
  equipes,
  valueKind,
}: {
  equipes: EquipeResultado[]
  valueKind: ValueKind
}) {
  const par = rotulosDoPar(valueKind)
  const columns: Column<EquipeResultado>[] = [
    { key: 'equipe', header: 'Equipe', render: (e) => texto(e.equipe, true) },
    { key: 'cnes', header: 'CNES', render: (e) => texto(e.cnes) },
    {
      key: 'situacao',
      header: 'Situação',
      render: (e) => (
        <StatusChip status={e.status} label={e.statusRotulo} size="sm" withIcon={false} />
      ),
    },
    { key: 'valor', header: 'Valor', align: 'right', render: (e) => texto(e.valor, true) },
    { key: 'numerador', header: par.numerador, align: 'right', render: (e) => texto(e.numerador) },
    {
      key: 'denominador',
      header: par.denominador,
      align: 'right',
      render: (e) => texto(e.denominador),
    },
    { key: 'classificacao', header: 'Classificação', render: (e) => texto(e.classificacao) },
    {
      key: 'media',
      header: 'Entra na média do quadrimestre',
      render: (e) => texto(e.consolidacao ? 'Sim' : 'Não'),
    },
  ]
  return (
    <SectionCard
      title="Resultado por equipe"
      subtitle="O mesmo cálculo para cada equipe (INE). Os registros sem equipe ficam à parte, nunca atribuídos a outra."
    >
      {equipes.length > 0 ? (
        <DataTable columns={columns} rows={equipes} getRowKey={(e) => e.chave} dense />
      ) : (
        <Typography sx={{ py: 3, textAlign: 'center', color: colors.textSecondary }}>
          Nenhum resultado por equipe publicado nesta competência.
        </Typography>
      )}
    </SectionCard>
  )
}
