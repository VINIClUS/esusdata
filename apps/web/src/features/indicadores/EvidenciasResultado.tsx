import { useMemo, useState } from 'react'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Typography from '@mui/material/Typography'
import { useEvidencias } from '@/api/hooks'
import { competenciaLabel, normalizeEvidencia } from '@/api/normalizers'
import type { EvidenciaLinha } from '@/api/types'
import { DataTable, type Column } from '@/components/data/DataTable'
import { FilterSelect } from '@/components/ui/FilterSelect'
import { SectionCard } from '@/components/ui/SectionCard'
import { colors } from '@/theme/tokens'

const TODAS = 'Todas'
const TODOS = 'Todos'

const traco = (texto: string | null) => texto ?? '—'

/** C1: one row per encounter, as before ADR 0030. */
const colunasDeEvento: Column<EvidenciaLinha>[] = [
  { key: 'data', header: 'Data do atendimento', render: (e) => traco(e.data) },
  { key: 'registro', header: 'Registro', render: (e) => traco(e.registro) },
  { key: 'modalidade', header: 'Modalidade', render: (e) => traco(e.modalidade) },
  { key: 'cnes', header: 'CNES', render: (e) => traco(e.cnes) },
  { key: 'ine', header: 'INE', render: (e) => traco(e.ine) },
  { key: 'decisao', header: 'Decisão', render: (e) => e.decisaoRotulo },
]

const tiposDeSujeito = { PERSON: 'pessoa', EPISODE: 'episódio', EVENT: 'evento' } as const

/** C2–C7: per person or episode and practice, plus the supporting events behind a decision. */
const colunasDeSujeito: Column<EvidenciaLinha>[] = [
  {
    key: 'sujeito',
    header: 'Sujeito (chave opaca)',
    render: (e) => (
      <Box>
        <Typography sx={{ fontSize: 13, fontFamily: 'monospace', color: colors.navy }}>
          {traco(e.sujeito)}
        </Typography>
        <Typography sx={{ fontSize: 11.5, color: colors.textSecondary }}>
          {tiposDeSujeito[e.tipo]}
        </Typography>
      </Box>
    ),
  },
  { key: 'componente', header: 'Componente', align: 'center', render: (e) => traco(e.componente) },
  {
    key: 'decisao',
    header: 'Decisão',
    sx: { whiteSpace: 'nowrap' },
    render: (e) => e.decisaoRotulo,
  },
  { key: 'motivo', header: 'Motivo (código)', render: (e) => traco(e.motivo) },
  { key: 'pontos', header: 'Pontos', align: 'right', render: (e) => traco(e.pontos) },
  { key: 'data', header: 'Data', render: (e) => traco(e.data) },
  { key: 'registro', header: 'Registro de suporte', render: (e) => traco(e.registro) },
  { key: 'ine', header: 'INE', render: (e) => traco(e.ine) },
]

/** How many rows show, of how many loaded, and whether the API has more. */
function contagem(visiveis: number, carregados: number, maisPaginas: boolean): string {
  const filtrados = visiveis < carregados ? `${visiveis} de ` : ''
  const pelosFiltros = visiveis < carregados ? ', pelos filtros' : ''
  return maisPaginas
    ? `${filtrados}${carregados} registro(s) carregado(s)${pelosFiltros}; há mais a carregar, e os filtros valem só para o que já foi carregado.`
    : `${filtrados}${carregados} registro(s)${pelosFiltros}; todos carregados.`
}

const unicos = (valores: (string | null)[]) =>
  [...new Set(valores.filter((v): v is string => v !== null))].sort((a, b) => a.localeCompare(b))

/**
 * The records behind a published result (§1.12.1): no name, CPF or CNS — a person or episode is
 * only the source's opaque key — narrowed by the API to the caller's team when the grant is
 * team-scoped. Paged on request; the filters apply to the rows already loaded.
 */
export function EvidenciasResultado({
  resultId,
  competencia,
}: {
  resultId: string
  competencia: string | undefined
}) {
  const { data, error, isPending, isError, fetchNextPage, hasNextPage, isFetchingNextPage } =
    useEvidencias(resultId)
  const [decisao, setDecisao] = useState(TODAS)
  const [componente, setComponente] = useState(TODOS)
  const linhas = useMemo(
    () =>
      (data?.pages ?? []).flatMap((page, pagina) =>
        page.items.map((item, indice) => normalizeEvidencia(item, `${pagina}-${indice}`)),
      ),
    [data],
  )
  const porSujeito = linhas.some((l) => l.tipo !== 'EVENT')
  const decisoes = unicos(linhas.map((l) => l.decisaoRotulo))
  const componentes = unicos(linhas.map((l) => l.componente))
  const visiveis = linhas.filter(
    (l) =>
      (decisao === TODAS || l.decisaoRotulo === decisao) &&
      (componente === TODOS || l.componente === componente),
  )

  return (
    <SectionCard
      title="Evidências"
      subtitle={`Registros usados no resultado${competencia ? ` de ${competenciaLabel(competencia)}` : ''}; sem nome, CPF ou CNS: uma pessoa ou gestação é só a chave opaca da fonte.`}
    >
      {isPending ? (
        <Typography sx={{ py: 4, textAlign: 'center', color: colors.textSecondary }}>
          Carregando evidências…
        </Typography>
      ) : isError ? (
        <Typography role="alert" color="error">
          {error.message}
        </Typography>
      ) : linhas.length === 0 ? (
        <Typography sx={{ py: 4, textAlign: 'center', color: colors.textSecondary }}>
          Nenhuma evidência registrada para este resultado.
        </Typography>
      ) : (
        <>
          <Box
            sx={{
              display: 'grid',
              gridTemplateColumns: { xs: '1fr', md: 'repeat(2, minmax(0, 320px))' },
              gap: 1.5,
              mb: 1.5,
            }}
          >
            <FilterSelect
              label="Decisão"
              value={decisao}
              options={[TODAS, ...decisoes]}
              onChange={setDecisao}
              fullWidth
            />
            {componentes.length > 0 && (
              <FilterSelect
                label="Componente"
                value={componente}
                options={[TODOS, ...componentes]}
                onChange={setComponente}
                fullWidth
              />
            )}
          </Box>
          <DataTable
            columns={porSujeito ? colunasDeSujeito : colunasDeEvento}
            rows={visiveis}
            getRowKey={(e) => e.chave}
            dense
          />
          <Box
            sx={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              flexWrap: 'wrap',
              gap: 1.5,
              mt: 1.5,
            }}
          >
            <Typography sx={{ fontSize: 13, color: colors.textSecondary }}>
              {contagem(visiveis.length, linhas.length, hasNextPage)}
            </Typography>
            {hasNextPage && (
              <Button
                variant="outlined"
                disabled={isFetchingNextPage}
                onClick={() => void fetchNextPage()}
              >
                Carregar mais
              </Button>
            )}
          </Box>
        </>
      )}
    </SectionCard>
  )
}
