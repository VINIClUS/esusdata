import { Callout } from './Callout'
import { PageHeader } from '@/components/layout/PageHeader'

export function PageUnavailable({
  title,
  subtitle,
  error,
}: {
  title: string
  subtitle: string
  error: unknown
}) {
  return (
    <>
      <PageHeader title={title} subtitle={subtitle} />
      <Callout variant="error" title="Dados indisponíveis">
        {error instanceof Error ? error.message : 'A API não fornece esta tela neste momento.'}
      </Callout>
    </>
  )
}
