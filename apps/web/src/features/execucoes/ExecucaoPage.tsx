import { useState } from 'react'
import { USE_MOCKS } from '@/api/client'
import { useFontesExecucao, useFontesPec, useUltimaExecucao } from '@/api/hooks'
import { useScope } from '@/app/scope-context'
import { PageHeader } from '@/components/layout/PageHeader'
import { FilterSelect } from '@/components/ui/FilterSelect'
import { PageUnavailable } from '@/components/ui/PageUnavailable'
import { PageSkeleton } from '@/components/ui/PageSkeleton'
import { UnderlineTabs } from '@/components/ui/Tabs'
import { AgendamentoPanel } from './AgendamentoPanel'
import { ExecucaoUnica } from './ExecucaoUnica'

const TITLE = 'Execução de Dados'
const SUBTITLE = 'Gerencie a atualização dos dados e a execução dos indicadores do e-SUS PEC.'
const NO_SOURCE = new Error('Nenhuma fonte do e-SUS PEC cadastrada para este município.')

export function ExecucaoPage() {
  const { municipalityIbge, municipalities, isLoading } = useScope()
  // A technical admin has no clinical municipality: the page follows the PEC sources they manage.
  const fontesPec = useFontesPec(!USE_MOCKS && !isLoading && !municipalityIbge)
  const ibge = municipalityIbge ?? fontesPec.data?.[0]?.municipalityIbge
  // RUN_INDICATOR comes with the manager role, like READ_CLINICAL: the clinical municipalities are
  // the ones a run may target. Anyone else here manages the source and only sees its scheduler.
  const podeExecutar = USE_MOCKS || (!!ibge && municipalities.includes(ibge))
  const fontes = useFontesExecucao(ibge)
  const ultima = useUltimaExecucao(ibge, podeExecutar)
  const [tab, setTab] = useState('unica')
  const [sourceId, setSourceId] = useState<string>()
  const [acompanhando, setAcompanhando] = useState<string>()

  // isLoading, not isPending: a query the caller may not run stays pending forever.
  if (isLoading || fontesPec.isLoading || fontes.isLoading || ultima.isLoading) {
    return <PageSkeleton title={TITLE} />
  }
  const error = fontesPec.error ?? fontes.error ?? ultima.error
  if (error) return <PageUnavailable title={TITLE} subtitle={SUBTITLE} error={error} />
  const lista = fontes.data ?? []
  const fonte = lista.find((f) => f.sourceId === sourceId) ?? lista[0]
  if (!ibge || !fonte)
    return <PageUnavailable title={TITLE} subtitle={SUBTITLE} error={NO_SOURCE} />

  return (
    <>
      <PageHeader
        title={TITLE}
        subtitle={SUBTITLE}
        actions={
          lista.length > 1 && (
            <FilterSelect
              label="Fonte"
              value={fonte.sourceId}
              options={lista.map((f) => f.sourceId)}
              onChange={setSourceId}
            />
          )
        }
      />

      <UnderlineTabs
        boxed
        items={[
          { key: 'unica', label: 'Execução única' },
          { key: 'agendamento', label: 'Agendamento' },
        ]}
        value={tab}
        onChange={setTab}
      />

      {tab === 'unica' ? (
        <ExecucaoUnica
          municipalityIbge={ibge}
          fonte={fonte}
          ultima={ultima.data ?? null}
          podeExecutar={podeExecutar}
          acompanhando={acompanhando ?? ultima.data?.jobId}
          onAcompanhar={setAcompanhando}
          onVerCobertura={() => setTab('agendamento')}
        />
      ) : (
        <AgendamentoPanel
          fonte={fonte}
          onEnfileirada={(jobId) => {
            if (!podeExecutar) return
            setAcompanhando(jobId)
            setTab('unica')
          }}
        />
      )}
    </>
  )
}
