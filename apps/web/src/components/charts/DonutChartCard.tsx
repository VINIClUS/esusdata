import type { ReactNode } from 'react'
import Box from '@mui/material/Box'
import Typography from '@mui/material/Typography'
import { Cell, Pie, PieChart, ResponsiveContainer } from 'recharts'
import { colors } from '@/theme/tokens'

interface DonutSlice {
  name: string
  value: number
  color: string
}

interface DonutChartProps {
  data: DonutSlice[]
  centerValue: ReactNode
  centerLabel?: ReactNode
  size?: number
  thickness?: number
}

export function DonutChart({ data, centerValue, centerLabel, size = 170, thickness = 22 }: DonutChartProps) {
  return (
    <Box sx={{ position: 'relative', width: size, height: size, mx: 'auto' }}>
      <ResponsiveContainer width="100%" height="100%">
        <PieChart>
          <Pie
            data={data}
            dataKey="value"
            innerRadius={size / 2 - thickness}
            outerRadius={size / 2}
            startAngle={90}
            endAngle={-270}
            paddingAngle={data.length > 1 ? 1.5 : 0}
            stroke="none"
            isAnimationActive={false}
          >
            {data.map((d) => (
              <Cell key={d.name} fill={d.color} />
            ))}
          </Pie>
        </PieChart>
      </ResponsiveContainer>
      <Box sx={{ position: 'absolute', inset: 0, display: 'grid', placeItems: 'center', textAlign: 'center', pointerEvents: 'none' }}>
        <Box>
          <Typography sx={{ fontSize: size > 150 ? 32 : 24, fontWeight: 700, color: colors.navy, lineHeight: 1.05 }}>{centerValue}</Typography>
          {centerLabel && <Typography sx={{ fontSize: 12.5, color: colors.navy, lineHeight: 1.25, mt: 0.5 }}>{centerLabel}</Typography>}
        </Box>
      </Box>
    </Box>
  )
}
