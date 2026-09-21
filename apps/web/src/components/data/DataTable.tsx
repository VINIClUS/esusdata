import type { ReactNode } from 'react'
import Box from '@mui/material/Box'
import Table from '@mui/material/Table'
import TableBody from '@mui/material/TableBody'
import TableCell from '@mui/material/TableCell'
import TableContainer from '@mui/material/TableContainer'
import TableHead from '@mui/material/TableHead'
import TableRow from '@mui/material/TableRow'
import { ChevronsUpDown } from 'lucide-react'
import { colors } from '@/theme/tokens'

export interface Column<T> {
  key: string
  header: ReactNode
  render: (row: T) => ReactNode
  align?: 'left' | 'center' | 'right'
  width?: number | string
  sortable?: boolean
  sx?: object
}

interface DataTableProps<T> {
  columns: Column<T>[]
  rows: T[]
  getRowKey: (row: T) => string
  onRowClick?: (row: T) => void
  dense?: boolean
  bordered?: boolean
  renderCard?: (row: T) => ReactNode
  cardMode?: boolean
  sx?: object
}

export function DataTable<T>({
  columns,
  rows,
  getRowKey,
  onRowClick,
  dense,
  bordered,
  renderCard,
  cardMode,
  sx,
}: DataTableProps<T>) {
  if (cardMode && renderCard) {
    return (
      <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1.25 }}>
        {rows.map((row) => (
          <Box key={getRowKey(row)} onClick={onRowClick ? () => onRowClick(row) : undefined}>
            {renderCard(row)}
          </Box>
        ))}
      </Box>
    )
  }
  return (
    <TableContainer
      sx={{
        borderRadius: '10px',
        border: `1px solid ${colors.border}`,
        ...(bordered
          ? {
              '& td, & th': { borderRight: `1px solid ${colors.border}` },
              '& td:last-of-type, & th:last-of-type': { borderRight: 0 },
            }
          : {}),
        ...sx,
      }}
    >
      <Table size={dense ? 'small' : 'medium'}>
        <TableHead>
          <TableRow>
            {columns.map((c) => (
              <TableCell
                key={c.key}
                align={c.align}
                sx={{
                  width: c.width,
                  whiteSpace: 'nowrap',
                  ...(dense ? { py: 1, px: 1.5 } : {}),
                  ...c.sx,
                }}
              >
                <Box sx={{ display: 'inline-flex', alignItems: 'center', gap: 0.75 }}>
                  {c.header}
                  {c.sortable && <ChevronsUpDown size={14} color={colors.textMuted} />}
                </Box>
              </TableCell>
            ))}
          </TableRow>
        </TableHead>
        <TableBody>
          {rows.map((row) => (
            <TableRow
              key={getRowKey(row)}
              hover={Boolean(onRowClick)}
              onClick={onRowClick ? () => onRowClick(row) : undefined}
              sx={{
                cursor: onRowClick ? 'pointer' : 'default',
                '&:last-of-type td': { borderBottom: 0 },
              }}
            >
              {columns.map((c) => (
                <TableCell
                  key={c.key}
                  align={c.align}
                  sx={{ ...(dense ? { py: 1, px: 1.5 } : {}), ...c.sx }}
                >
                  {c.render(row)}
                </TableCell>
              ))}
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </TableContainer>
  )
}
