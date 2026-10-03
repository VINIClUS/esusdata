import Box from '@mui/material/Box'
import Typography from '@mui/material/Typography'
import type { ComponenteResultado } from '@/api/types'
import { DataTable, type Column } from '@/components/data/DataTable'
import { SectionCard } from '@/components/ui/SectionCard'
import { StatusChip } from '@/components/ui/StatusChip'
import { colors } from '@/theme/tokens'

const textos = {
  PRACTICE: {
    titulo: 'Boas práticas',
    coluna: 'Prática',
    subtitulo:
      'Quantos elegíveis cumpriram cada prática, com o seu peso em pontos. Cada pessoa soma os pesos das práticas comprovadas.',
  },
  SUBGROUP: {
    titulo: 'Subgrupos (soma ponderada)',
    coluna: 'Subgrupo',
    subtitulo:
      'Cada subgrupo tem a sua população e o seu peso; o escore soma peso × proporção. Um subgrupo sem valor deixa o escore indefinido, nunca zero.',
  },
} as const

const numero = (texto: string | null) => (
  <Typography sx={{ fontSize: 13.5, color: colors.navy, whiteSpace: 'nowrap' }}>
    {texto ?? '—'}
  </Typography>
)

/**
 * The practices (C2–C6) or subgroups (C7) of a pack: the ficha's wording and weight, and, when a
 * result is published, how many met each one. `contagens={false}` shows the ficha alone.
 */
export function ComponentesTabela({
  componentes,
  contagens = true,
  titulo,
}: {
  componentes: ComponenteResultado[]
  contagens?: boolean
  titulo?: string
}) {
  const tipo = componentes[0]?.tipo ?? 'PRACTICE'
  const texto = textos[tipo]
  const columns: Column<ComponenteResultado>[] = [
    {
      key: 'componente',
      header: texto.coluna,
      sx: { minWidth: 320 },
      render: (c) => (
        <Box sx={{ display: 'flex', gap: 1.25, alignItems: 'flex-start' }}>
          <Typography sx={{ fontSize: 14, fontWeight: 700, color: colors.navy, minWidth: 18 }}>
            {c.codigo}
          </Typography>
          <Typography sx={{ fontSize: 13, color: colors.navy, lineHeight: 1.4 }}>
            {c.rotulo}
          </Typography>
        </Box>
      ),
    },
    { key: 'peso', header: 'Peso', align: 'center', render: (c) => numero(c.peso) },
    {
      key: 'janela',
      header: 'Janela',
      sx: { minWidth: 96 },
      render: (c) => (
        <Typography sx={{ fontSize: 13, color: colors.textSecondary }}>
          {c.janela ?? '—'}
        </Typography>
      ),
    },
  ]
  if (contagens) {
    columns.push(
      { key: 'cumpriram', header: 'Cumpriram', align: 'right', render: (c) => numero(c.cumpriram) },
      { key: 'elegiveis', header: 'Elegíveis', align: 'right', render: (c) => numero(c.elegiveis) },
      {
        key: 'proporcao',
        header: 'Proporção',
        align: 'right',
        render: (c) => numero(c.proporcao),
      },
      {
        key: 'situacao',
        header: 'Situação',
        render: (c) =>
          c.situacao ? (
            <StatusChip
              status={c.situacao}
              label={c.situacaoRotulo ?? undefined}
              size="sm"
              withIcon={false}
            />
          ) : (
            <Typography sx={{ fontSize: 13, color: colors.textSecondary }}>
              Não publicado
            </Typography>
          ),
      },
    )
  }
  return (
    <SectionCard title={titulo ?? texto.titulo} subtitle={texto.subtitulo}>
      <DataTable columns={columns} rows={componentes} getRowKey={(c) => c.codigo} dense />
    </SectionCard>
  )
}
