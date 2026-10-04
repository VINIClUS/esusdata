import { useMemo, useState } from 'react'
import Box from '@mui/material/Box'
import Typography from '@mui/material/Typography'
import { ArrowLeft, Calendar } from 'lucide-react'
import { Link } from 'react-router'
import { useCompetencia, useCompetenciasPublicadas } from '@/api/hooks'
import { quadrimestreDe, quadrimestreLabel, quadrimestresDasCompetencias } from '@/api/normalizers'
import type { Componente3Indicador, Componente3Resumo, Componente3Unidade } from '@/api/types'
import { DataTable, type Column } from '@/components/data/DataTable'
import { StatColumns } from '@/components/data/StatColumns'
import { PageHeader } from '@/components/layout/PageHeader'
import { Callout } from '@/components/ui/Callout'
import { FilterSelect } from '@/components/ui/FilterSelect'
import { SectionCard } from '@/components/ui/SectionCard'
import { StatusChip } from '@/components/ui/StatusChip'
import { colors } from '@/theme/tokens'
import { lidosChave } from './lidos'
import { useComponente3 } from './useComponente3'

const TITLE = 'Componente III – Nota Final'
const SUBTITLE =
  'A consolidação quadrimestral de C1 a C7 (NT nº 8/2026), calculada na leitura a partir dos resultados mensais publicados. Nada é executado aqui.'

const traco = (texto: string | null) => (
  <Typography sx={{ fontSize: 13.5, color: colors.navy, whiteSpace: 'nowrap' }}>
    {texto ?? '—'}
  </Typography>
)

const situacao = (linha: { status: Componente3Unidade['status']; statusRotulo: string }) => (
  <StatusChip status={linha.status} label={linha.statusRotulo} size="sm" withIcon={false} />
)

const unidadeColumns: Column<Componente3Unidade>[] = [
  {
    key: 'unidade',
    header: 'Unidade',
    render: (u) => (
      <Typography sx={{ fontSize: 13.5, fontWeight: 700, color: colors.navy }}>
        {u.unidade}
      </Typography>
    ),
  },
  { key: 'cnes', header: 'CNES', render: (u) => traco(u.cnes) },
  { key: 'nota', header: 'Nota (0 a 10)', align: 'right', render: (u) => traco(u.nota) },
  {
    key: 'metodologica',
    header: 'Classificação metodológica',
    render: (u) => traco(u.classificacaoMetodologica),
  },
  {
    key: 'financeira',
    header: 'Transição financeira (Portaria 10.994/2026)',
    // The NT 8/2026 grades "uma equipe" and the Portaria pays teams: the municipality has none.
    render: (u) =>
      traco(u.ine === null ? 'Não se aplica (repasse por equipe)' : u.classificacaoFinanceira),
  },
  { key: 'situacao', header: 'Situação', render: situacao },
]

function indicadorColumns(lidos: (codigo: string) => number): Column<Componente3Indicador>[] {
  return [
    {
      key: 'indicador',
      header: 'Indicador',
      sx: { minWidth: 220 },
      render: (i) => <Typography sx={{ fontSize: 13.5, color: colors.navy }}>{i.nome}</Typography>,
    },
    { key: 'peso', header: 'Peso', align: 'center', render: (i) => traco(i.peso) },
    {
      key: 'meses',
      header: 'Meses usados',
      // Every result read is counted, the months that did not enter the mean included (ADR 0030).
      render: (i) =>
        traco(
          `${i.mesesUsados.length > 0 ? i.mesesUsados.join(', ') : '—'} · ${lidos(i.codigo)} lidos`,
        ),
    },
    { key: 'media', header: 'Média', align: 'right', render: (i) => traco(i.media) },
    { key: 'classificacao', header: 'Classificação', render: (i) => traco(i.classificacao) },
    { key: 'fator', header: 'Fator', align: 'right', render: (i) => traco(i.fator) },
    { key: 'situacao', header: 'Situação', render: situacao },
  ]
}

function Lista({ itens }: { itens: string[] }) {
  return (
    <Box component="ul" sx={{ m: 0, pl: 2.5, display: 'grid', gap: 0.5 }}>
      {itens.map((item, indice) => (
        <Typography
          component="li"
          key={`${indice}-${item}`}
          sx={{ fontSize: 13.5, color: colors.navy }}
        >
          {item}
        </Typography>
      ))}
    </Box>
  )
}

function Unidade({
  unidade,
  geral,
  lidos,
}: {
  unidade: Componente3Unidade
  geral: string[]
  lidos: ReadonlyMap<string, number>
}) {
  const colunas = indicadorColumns((codigo) => lidos.get(lidosChave(unidade.ine, codigo)) ?? 0)
  const proprias = unidade.limitacoes.filter((l) => !geral.includes(l))
  return (
    <SectionCard
      title={unidade.unidade}
      subtitle={
        unidade.cnes
          ? `CNES ${unidade.cnes} · indicadores do quadrimestre`
          : 'Indicadores do quadrimestre'
      }
      action={situacao(unidade)}
    >
      {unidade.indicadores.length > 0 ? (
        <DataTable columns={colunas} rows={unidade.indicadores} getRowKey={(i) => i.codigo} dense />
      ) : (
        <Typography sx={{ py: 2, color: colors.textSecondary }}>
          Nenhum indicador consolidado nesta unidade.
        </Typography>
      )}
      {proprias.length > 0 && (
        <Box sx={{ mt: 1.5 }}>
          <Lista itens={proprias} />
        </Box>
      )}
    </SectionCard>
  )
}

function Consolidacao({
  resumo,
  lidos,
}: {
  resumo: Componente3Resumo
  lidos: ReadonlyMap<string, number>
}) {
  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1.75 }}>
      {!resumo.completo && (
        <Callout variant="warning" title="Nota Final indisponível neste quadrimestre">
          <Typography sx={{ fontSize: 13.5, color: colors.navy, mb: 1 }}>
            Enquanto algum indicador estiver ausente ou bloqueado, a unidade fica sem nota: nenhum
            zero é imputado e nenhum peso é redistribuído.
          </Typography>
          {resumo.limitacoes.length > 0 && <Lista itens={resumo.limitacoes} />}
        </Callout>
      )}
      <SectionCard
        title="Unidades"
        subtitle="O município e cada equipe (INE) com resultados publicados. A classificação da transição financeira fica separada da metodológica."
      >
        <DataTable
          columns={unidadeColumns}
          rows={resumo.unidades}
          getRowKey={(u) => u.chave}
          dense
        />
      </SectionCard>
      {resumo.unidades.map((unidade) => (
        <Unidade key={unidade.chave} unidade={unidade} geral={resumo.limitacoes} lidos={lidos} />
      ))}
      <Typography sx={{ fontSize: 12.5, color: colors.textSecondary, overflowWrap: 'anywhere' }}>
        Regra {resumo.versaoRegra} ·{' '}
        {resumo.fingerprint
          ? `impressão digital dos resultados lidos: ${resumo.fingerprint}`
          : 'nenhum resultado publicado lido'}
      </Typography>
    </Box>
  )
}

function quadrimestreAtual(): string {
  const now = new Date()
  return `${now.getFullYear()}-Q${Math.ceil((now.getMonth() + 1) / 4)}`
}

/**
 * The Nota Final do Componente III of the scope's municipality, by quadrimestre (2026-Q2 =
 * mai–ago): the municipality and each team, the methodological and the financial classification,
 * and each indicator's mean, months, factor and status — or why there is no note yet.
 */
export function Componente3Page() {
  const competencias = useCompetenciasPublicadas()
  const { competencia } = useCompetencia()
  const opcoes = useMemo(() => {
    const publicados = quadrimestresDasCompetencias(competencias)
    return publicados.length > 0 ? publicados : [quadrimestreAtual()]
  }, [competencias])
  const [escolhido, setEscolhido] = useState<string | null>(null)
  const padrao =
    (competencia ? quadrimestreDe(competencia) : null) ?? opcoes[0] ?? quadrimestreAtual()
  const quadrimestre = escolhido && opcoes.includes(escolhido) ? escolhido : padrao
  const { resumo, lidos, isPending, error } = useComponente3(quadrimestre)

  return (
    <>
      <PageHeader
        above={
          <Box
            component={Link}
            to="/indicadores"
            sx={{
              display: 'inline-flex',
              alignItems: 'center',
              gap: 0.75,
              color: colors.primary,
              fontSize: 14,
              fontWeight: 500,
              textDecoration: 'none',
              mb: 1.5,
            }}
          >
            <ArrowLeft size={18} /> Voltar aos indicadores
          </Box>
        }
        title={TITLE}
        subtitle={SUBTITLE}
      />

      <SectionCard
        title="Quadrimestre"
        subtitle="Q1 jan–abr, Q2 mai–ago, Q3 set–dez — nunca o trimestre civil."
        sx={{ mb: 1.75 }}
      >
        <Box
          sx={{
            display: 'grid',
            gridTemplateColumns: { xs: '1fr', md: 'minmax(0, 380px) 1fr' },
            gap: 2,
            alignItems: 'center',
          }}
        >
          <FilterSelect
            ariaLabel="Quadrimestre"
            icon={Calendar}
            value={quadrimestreLabel(quadrimestre)}
            options={opcoes.map(quadrimestreLabel)}
            onChange={(rotulo) =>
              setEscolhido(opcoes.find((q) => quadrimestreLabel(q) === rotulo) ?? null)
            }
            fullWidth
          />
          {resumo && (
            <StatColumns
              valueSize={15}
              stats={[
                { label: 'Município (IBGE)', value: resumo.municipioIbge },
                { label: 'Meses', value: resumo.meses.join(', ') || '—' },
              ]}
            />
          )}
        </Box>
      </SectionCard>

      {isPending ? (
        <Typography
          aria-busy="true"
          sx={{ py: 6, textAlign: 'center', color: colors.textSecondary }}
        >
          Carregando a consolidação…
        </Typography>
      ) : error || !resumo ? (
        <Callout variant="error" title="Dados indisponíveis">
          {error?.message ?? 'A API não fornece esta consolidação neste momento.'}
        </Callout>
      ) : (
        <Consolidacao resumo={resumo} lidos={lidos} />
      )}
    </>
  )
}
