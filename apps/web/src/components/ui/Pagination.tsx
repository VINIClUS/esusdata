import Box from '@mui/material/Box'
import { ChevronRight } from 'lucide-react'
import { colors } from '@/theme/tokens'

interface PaginationProps {
  page: number
  count: number
  onChange: (page: number) => void
}

export function Pagination({ page, count, onChange }: PaginationProps) {
  const btn = (active: boolean) => ({
    minWidth: 38,
    height: 38,
    borderRadius: '8px',
    border: 0,
    bgcolor: active ? colors.primary : 'transparent',
    color: active ? '#fff' : colors.navy,
    font: 'inherit',
    fontSize: 14,
    fontWeight: 600,
    cursor: 'pointer',
    display: 'grid',
    placeItems: 'center',
    '&:hover': { bgcolor: active ? colors.primaryDark : colors.primarySoft },
  })
  return (
    <Box
      component="nav"
      aria-label="Paginação"
      sx={{ display: 'flex', alignItems: 'center', gap: 1 }}
    >
      {Array.from({ length: count }, (_, i) => i + 1).map((p) => (
        <Box
          key={p}
          component="button"
          type="button"
          aria-current={p === page ? 'page' : undefined}
          onClick={() => onChange(p)}
          sx={btn(p === page)}
        >
          {p}
        </Box>
      ))}
      <Box
        component="button"
        type="button"
        aria-label="Próxima página"
        onClick={() => onChange(Math.min(count, page + 1))}
        sx={btn(false)}
      >
        <ChevronRight size={18} />
      </Box>
    </Box>
  )
}
