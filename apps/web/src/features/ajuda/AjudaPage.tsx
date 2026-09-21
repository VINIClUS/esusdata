import Typography from '@mui/material/Typography'
import { PageHeader } from '@/components/layout/PageHeader'
import { SectionCard } from '@/components/ui/SectionCard'

export function AjudaPage() {
  return (
    <>
      <PageHeader title="Ajuda" subtitle="Documentação, suporte e perguntas frequentes." />
      <SectionCard title="Em breve">
        <Typography sx={{ fontSize: 14 }}>A central de ajuda será disponibilizada nas próximas versões.</Typography>
      </SectionCard>
    </>
  )
}
