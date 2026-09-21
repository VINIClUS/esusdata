import Box from '@mui/material/Box'
import Skeleton from '@mui/material/Skeleton'
import { PageHeader } from '@/components/layout/PageHeader'

export function PageSkeleton({ title }: { title: string }) {
  return (
    <Box aria-busy="true">
      <PageHeader title={title} />
      <Skeleton variant="rounded" height={120} sx={{ borderRadius: '14px', mb: 2 }} />
      <Skeleton variant="rounded" height={320} sx={{ borderRadius: '14px' }} />
    </Box>
  )
}
