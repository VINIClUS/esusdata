import { useState } from 'react'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Grid from '@mui/material/Grid'
import Paper from '@mui/material/Paper'
import Typography from '@mui/material/Typography'
import useMediaQuery from '@mui/material/useMediaQuery'
import { useTheme } from '@mui/material/styles'
import {
  ArrowLeft,
  Building,
  Calendar,
  Database,
  FileText,
  RefreshCw,
  Sigma,
  Target,
  User,
  Users,
  type LucideIcon,
} from 'lucide-react'
import { Link, Navigate, useNavigate, useParams } from 'react-router'
import { useIndicadorDetalhe, useIndicadorNaVisaoGeral } from '@/api/hooks'
import { COMPONENTE_III_ID, COMPONENTE_III_PATH, competenciaLabel } from '@/api/normalizers'
import type { HistoricoPonto, IndicadorDetalhe, InfoAdicional, MetodologiaItem } from '@/api/types'
import { LineChartCard } from '@/components/charts/LineChartCard'
import { DataTable, type Column } from '@/components/data/DataTable'
import { KpiCard } from '@/components/data/KpiCard'
import { PageHeader } from '@/components/layout/PageHeader'
import { Callout } from '@/components/ui/Callout'
import { IconRow } from '@/components/ui/IconRow'
import { LinkButton } from '@/components/ui/LinkButton'
import { PageSkeleton } from '@/components/ui/PageSkeleton'
import { SectionCard } from '@/components/ui/SectionCard'
import { StatusChip } from '@/components/ui/StatusChip'
import { UnderlineTabs } from '@/components/ui/Tabs'
import { colors } from '@/theme/tokens'
import { ComponentesTabela } from './ComponentesTabela'
import { detailTabContent, type DetailTabContent } from './detail-tabs'
import { EquipesTabela } from './EquipesTabela'
import { EvidenciasResultado } from './EvidenciasResultado'
import { naturezaDoValor, rotulosDoPar } from './rotulos'

const metodologiaIcons: Record<MetodologiaItem['icone'], LucideIcon> = {
  target: Target,
  sigma: Sigma,
  users: Users,
  database: Database,
  file: FileText,
}
const infoIcons: Record<InfoAdicional['icone'], LucideIcon> = {
  calendar: Calendar,
  building: Building,
  refresh: RefreshCw,
  users: Users,
  user: User,
  file: FileText,
}

const desktopTabs = [
  { key: 'resultados', label: 'Resultados' },
  { key: 'metodologia', label: 'Metodologia' },
  { key: 'populacao', label: 'População e filtros' },
  { key: 'evidencias', label: 'Evidências' },
  { key: 'historico', label: 'Histórico' },
]
const phoneTabs = [
  { key: 'resultados', label: 'Resumo' },
  { key: 'metodologia', label: 'Metodologia' },
  { key: 'estratificacoes', label: 'Estratificações' },
]

function MetaChip({
  icon: Icon,
  label,
  value,
}: {
  icon: LucideIcon
  label: string
  value: string
}) {
  return (
    <Paper
      sx={{ display: 'flex', alignItems: 'center', gap: 1, px: 1.75, py: 1, borderRadius: '10px' }}
    >
      <Icon size={18} color={colors.primary} />
      <Typography sx={{ fontSize: 13.5, color: colors.navy }}>
        <strong>{label}:</strong>{' '}
        <Box component="span" sx={{ color: colors.primary }}>
          {value}
        </Box>
      </Typography>
    </Paper>
  )
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

/** A methodology source: a link when it is a URL, the repository path otherwise. */
function Fonte({ fonte }: { fonte: string }) {
  if (!/^https?:\/\//.test(fonte)) {
    return <Typography sx={{ fontSize: 13.5, color: colors.navy }}>{fonte}</Typography>
  }
  return (
    <Typography sx={{ fontSize: 13.5, overflowWrap: 'anywhere' }}>
      <Box
        component="a"
        href={fonte}
        target="_blank"
        rel="noreferrer"
        sx={{ color: colors.primary }}
      >
        {fonte}
      </Box>
    </Typography>
  )
}

/** Why the value is missing, and what holds the result back. */
function Retencao({ data }: { data: IndicadorDetalhe }) {
  if (!data.resultado.motivo) return null
  const limitacoes = data.resultId
    ? data.limitacoes
    : [...data.limitacoesPermanentes, ...data.portoes]
  return (
    <Callout
      variant={data.status === 'pendente' || data.status === 'na_leitura' ? 'info' : 'warning'}
      title={data.resultId ? `Sem valor: ${data.statusRotulo.toLowerCase()}` : data.statusRotulo}
    >
      <Typography sx={{ fontSize: 13.5, color: colors.navy, mb: limitacoes.length ? 1 : 0 }}>
        {data.resultado.motivo}
      </Typography>
      {limitacoes.length > 0 && <Lista itens={limitacoes} />}
    </Callout>
  )
}

function Resultados({
  data,
  phone,
  onVerEquipes,
}: {
  data: IndicadorDetalhe
  phone: boolean
  onVerEquipes: () => void
}) {
  const par = rotulosDoPar(data.valueKind)
  const semPar = data.tipoComponentes === 'SUBGROUP'
  return (
    <Grid container spacing={1.75}>
      <Grid size={{ xs: 12, md: 4 }}>
        <KpiCard
          label="Resultado"
          value={data.resultado.valor ?? 'Indisponível'}
          valueColor={data.resultado.valor ? colors.success : colors.textSecondary}
          chip={
            data.resultado.classificacao
              ? { label: 'Classificação', value: data.resultado.classificacao }
              : undefined
          }
          caption={naturezaDoValor(data.valueKind)}
          compact={phone}
        />
      </Grid>
      <Grid size={{ xs: 6, md: 4 }}>
        <KpiCard
          label={par.numerador}
          value={data.numerador ?? '—'}
          caption={semPar ? 'Cada subgrupo tem o seu par.' : undefined}
          compact={phone}
        />
      </Grid>
      <Grid size={{ xs: 6, md: 4 }}>
        <KpiCard
          label={par.denominador}
          value={data.denominador ?? '—'}
          caption={
            semPar ? (
              'Veja os subgrupos abaixo.'
            ) : data.denominadorTipo ? (
              // A code without spaces: it breaks anywhere rather than widen the card.
              <Box component="span" sx={{ overflowWrap: 'anywhere' }}>
                {data.denominadorTipo}
              </Box>
            ) : undefined
          }
          compact={phone}
        />
      </Grid>
      {data.resultado.motivo && (
        <Grid size={12}>
          <Retencao data={data} />
        </Grid>
      )}
      {data.componentes.length > 0 && (
        <Grid size={12}>
          <ComponentesTabela componentes={data.componentes} />
        </Grid>
      )}
      {data.equipes.length > 0 && (
        <Grid size={12}>
          <LinkButton onClick={onVerEquipes}>
            Ver o resultado por equipe ({data.equipes.length})
          </LinkButton>
        </Grid>
      )}
      {data.resultado.valor !== null && data.limitacoes.length > 0 && (
        <Grid size={12}>
          <SectionCard title="Limitações do resultado">
            <Lista itens={data.limitacoes} />
          </SectionCard>
        </Grid>
      )}
    </Grid>
  )
}

function Metodologia({ data }: { data: IndicadorDetalhe }) {
  return (
    <Grid container spacing={1.75}>
      <Grid size={{ xs: 12, lg: 7 }}>
        <SectionCard title="Metodologia" subtitle="Como o indicador é calculado e interpretado.">
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1.5 }}>
            {data.metodologia.map((item) => {
              const Icon = metodologiaIcons[item.icone]
              return (
                <IconRow
                  key={item.titulo}
                  icon={<Icon size={18} strokeWidth={2.4} />}
                  title={item.titulo}
                  text={item.texto}
                />
              )
            })}
          </Box>
        </SectionCard>
      </Grid>
      <Grid size={{ xs: 12, lg: 5 }}>
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1.75 }}>
          <SectionCard
            title="Portões de liberação pendentes"
            subtitle="Enquanto algum falta, o resultado sai bloqueado, com as contagens."
          >
            {data.portoes.length > 0 ? (
              <Lista itens={data.portoes} />
            ) : (
              <Typography sx={{ fontSize: 13.5, color: colors.textSecondary }}>
                Todos os portões foram cumpridos.
              </Typography>
            )}
          </SectionCard>
          <SectionCard title="Limitações permanentes">
            {data.limitacoesPermanentes.length > 0 ? (
              <Lista itens={data.limitacoesPermanentes} />
            ) : (
              <Typography sx={{ fontSize: 13.5, color: colors.textSecondary }}>
                Nenhuma declarada no catálogo.
              </Typography>
            )}
          </SectionCard>
          <SectionCard title="Fontes metodológicas">
            {data.fontes.length > 0 ? (
              <Box sx={{ display: 'grid', gap: 0.75 }}>
                {data.fontes.map((fonte) => (
                  <Fonte key={fonte} fonte={fonte} />
                ))}
              </Box>
            ) : (
              <Typography sx={{ fontSize: 13.5, color: colors.textSecondary }}>
                Não informadas pelo catálogo.
              </Typography>
            )}
          </SectionCard>
        </Box>
      </Grid>
      {data.componentes.length > 0 && (
        <Grid size={12}>
          <ComponentesTabela
            componentes={data.componentes}
            contagens={false}
            titulo={
              data.tipoComponentes === 'SUBGROUP' ? 'Subgrupos da ficha' : 'Boas práticas da ficha'
            }
          />
        </Grid>
      )}
    </Grid>
  )
}

function Populacao({ data }: { data: IndicadorDetalhe }) {
  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1.75 }}>
      <SectionCard
        title="População e filtros"
        subtitle="Escopo e informações do resultado, como a API os publicou."
      >
        <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1.5 }}>
          {data.infoAdicionais.map((item) => (
            <MetaChip
              key={item.label}
              icon={infoIcons[item.icone]}
              label={item.label}
              value={item.valor}
            />
          ))}
        </Box>
      </SectionCard>
      <EquipesTabela equipes={data.equipes} valueKind={data.valueKind} />
    </Box>
  )
}

const historicoColumns: Column<HistoricoPonto>[] = [
  { key: 'competencia', header: 'Competência', render: (h) => h.mes },
  {
    key: 'situacao',
    header: 'Situação',
    render: (h) => (
      <StatusChip status={h.status} label={h.statusRotulo} size="sm" withIcon={false} />
    ),
  },
  { key: 'valor', header: 'Valor', align: 'right', render: (h) => h.valorTexto ?? '—' },
]

function Historico({ codigo }: { codigo: string }) {
  const { data, isPending, isError } = useIndicadorNaVisaoGeral(codigo)
  const pontos = data?.historico ?? []
  // A competência without a value has no `valor` key: the line breaks there, it never drops to 0.
  const serie = pontos.map((p) => {
    const ponto: Record<string, string | number> = { mes: p.mes }
    if (p.valor !== null) ponto.valor = p.valor
    return ponto
  })
  const comValor = pontos.some((p) => p.valor !== null)
  return (
    <SectionCard
      title="Histórico"
      subtitle="As 12 competências até a escolhida. Uma competência bloqueada ou sem denominador é uma lacuna, nunca zero."
    >
      {isPending ? (
        <Typography sx={{ py: 4, textAlign: 'center', color: colors.textSecondary }}>
          Carregando histórico…
        </Typography>
      ) : isError || pontos.length === 0 ? (
        <Typography sx={{ py: 7, textAlign: 'center', color: colors.textSecondary }}>
          Histórico indisponível para este indicador.
        </Typography>
      ) : (
        <>
          {comValor && (
            <LineChartCard
              data={serie}
              series={[{ key: 'valor', label: 'Resultado do indicador', cor: colors.primary }]}
              xKey="mes"
            />
          )}
          <DataTable
            columns={historicoColumns}
            rows={[...pontos].reverse()}
            getRowKey={(h) => h.competencia}
            dense
            sx={{ mt: 1.5 }}
          />
        </>
      )}
    </SectionCard>
  )
}

function Conteudo({ data, content }: { data: IndicadorDetalhe; content: DetailTabContent }) {
  switch (content) {
    case 'metodologia':
      return <Metodologia data={data} />
    case 'populacao':
      return <Populacao data={data} />
    case 'evidencias':
      return data.resultId ? (
        <EvidenciasResultado resultId={data.resultId} competencia={data.competencia} />
      ) : (
        <SectionCard title="Evidências">
          <Typography sx={{ py: 4, textAlign: 'center', color: colors.textSecondary }}>
            Sem resultado publicado nesta competência, não há evidência a mostrar.
          </Typography>
        </SectionCard>
      )
    case 'historico':
      return <Historico codigo={data.codigo} />
    case 'resultados':
      return null
  }
}

function Detalhe({ codigo }: { codigo: string }) {
  const { data, error, isError, isPending } = useIndicadorDetalhe(codigo)
  const visaoGeral = useIndicadorNaVisaoGeral(codigo)
  const theme = useTheme()
  const phone = useMediaQuery(theme.breakpoints.down('md'))
  const navigate = useNavigate()
  const [tab, setTab] = useState('resultados')

  if (isPending) return <PageSkeleton title={codigo} />

  if (isError) {
    return (
      <>
        <PageHeader
          title={codigo}
          subtitle="Os detalhes deste indicador ainda não estão disponíveis."
        />
        <SectionCard title="Detalhes indisponíveis">
          <Typography sx={{ color: colors.textSecondary }}>
            {error.message} Volte à lista de indicadores para escolher outro item.
          </Typography>
          <Button
            variant="outlined"
            startIcon={<ArrowLeft size={18} />}
            sx={{ mt: 2 }}
            onClick={() => void navigate('/indicadores')}
          >
            Voltar aos indicadores
          </Button>
        </SectionCard>
      </>
    )
  }

  const content = detailTabContent(tab)
  const disponibilidade = visaoGeral.data?.disponibilidade ?? null

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
        title={data.nome}
        chip={<StatusChip status={data.status} withIcon={false} />}
        subtitle={data.descricao}
        actions={
          !phone &&
          data.executavel && (
            <Button
              variant="contained"
              size="large"
              startIcon={<RefreshCw size={20} />}
              sx={{ minHeight: 52, px: 3, fontSize: 16 }}
              onClick={() => {
                const executar = new URLSearchParams({ indicador: data.codigo })
                if (data.competencia) executar.set('competencia', data.competencia)
                void navigate(`/execucao?${executar.toString()}`)
              }}
            >
              Executar novamente
            </Button>
          )
        }
      />

      {!phone && (
        <Box sx={{ display: 'flex', gap: 2, flexWrap: 'wrap', mb: 2 }}>
          <MetaChip icon={Target} label="Valor" value={naturezaDoValor(data.valueKind)} />
          <MetaChip
            icon={Calendar}
            label="Competência"
            value={data.competencia ? competenciaLabel(data.competencia) : 'Nenhuma publicada'}
          />
          <MetaChip
            icon={RefreshCw}
            label="Publicação"
            value={data.publicadoEm ?? 'Não publicado'}
          />
        </Box>
      )}

      {disponibilidade && disponibilidade.situacao !== 'disponivel' && (
        <Box sx={{ mb: 2 }}>
          <Callout
            variant="warning"
            dense
            title={`Disponibilidade no município: ${disponibilidade.rotulo}`}
          >
            {disponibilidade.capacidadesFaltantes.length > 0
              ? `Nenhuma fonte do PEC do município tem validadas as capacidades ${disponibilidade.capacidadesFaltantes.join(', ')}; a execução deste pacote falha antes de ler a fonte.`
              : 'O município não tem fonte do PEC cadastrada para executar este pacote.'}
          </Callout>
        </Box>
      )}

      <UnderlineTabs
        items={phone ? phoneTabs : desktopTabs}
        value={tab}
        onChange={setTab}
        sx={{ mb: 2 }}
      />

      {content === 'resultados' ? (
        <Resultados
          data={data}
          phone={phone}
          onVerEquipes={() => setTab(phone ? 'estratificacoes' : 'populacao')}
        />
      ) : (
        <Conteudo data={data} content={content} />
      )}
    </>
  )
}

/** The detail of a pack; the Nota Final has its own page, computed on read. */
export function IndicadorDetailPage() {
  const { codigo = '' } = useParams()
  if (codigo === COMPONENTE_III_ID) return <Navigate to={COMPONENTE_III_PATH} replace />
  return <Detalhe key={codigo} codigo={codigo} />
}
