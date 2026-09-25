import Box from '@mui/material/Box'
import Typography from '@mui/material/Typography'
import {
  CartesianGrid,
  Line,
  LineChart,
  ReferenceLine,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import type { SerieDef } from '@/api/types'
import { colors } from '@/theme/tokens'

interface LineChartCardProps {
  data: Record<string, number | string>[]
  series: SerieDef[]
  xKey: string
  height?: number
  referenceLine?: { value: number; label: string }
  legend?: boolean
}

export function Legend({
  series,
  extra,
}: {
  series: SerieDef[]
  extra?: { label: string; dashed?: boolean; cor: string }[]
}) {
  return (
    <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 2, rowGap: 0.5, mt: 0.5 }}>
      {series.map((s) => (
        <Box
          key={s.key}
          sx={{
            display: 'flex',
            alignItems: 'center',
            gap: 0.75,
            fontSize: 11.5,
            color: colors.navy,
          }}
        >
          <Box sx={{ width: 9, height: 9, borderRadius: '50%', bgcolor: s.cor }} />
          {s.label}
        </Box>
      ))}
      {extra?.map((e) => (
        <Box
          key={e.label}
          sx={{
            display: 'flex',
            alignItems: 'center',
            gap: 0.75,
            fontSize: 12.5,
            color: colors.navy,
          }}
        >
          <Box sx={{ width: 18, borderTop: `2px ${e.dashed ? 'dashed' : 'solid'} ${e.cor}` }} />
          {e.label}
        </Box>
      ))}
    </Box>
  )
}

export function LineChartCard({
  data,
  series,
  xKey,
  height = 230,
  referenceLine,
  legend = true,
}: LineChartCardProps) {
  return (
    <Box sx={{ minWidth: 0 }}>
      <Box sx={{ width: '100%', height }}>
        <ResponsiveContainer width="100%" height="100%">
          <LineChart data={data} margin={{ top: 8, right: 16, bottom: 0, left: -18 }}>
            <CartesianGrid stroke={colors.border} vertical />
            <XAxis
              dataKey={xKey}
              tick={{ fontSize: 12, fill: colors.textSecondary }}
              axisLine={{ stroke: colors.border }}
              tickLine={false}
            />
            <YAxis
              domain={[0, 100]}
              ticks={[0, 25, 50, 75, 100]}
              tickFormatter={(v: number) => `${v}%`}
              tick={{ fontSize: 12, fill: colors.textSecondary }}
              axisLine={false}
              tickLine={false}
            />
            <Tooltip
              formatter={(v) => `${String(v)}%`}
              contentStyle={{ borderRadius: 10, borderColor: colors.border, fontSize: 12 }}
            />
            {referenceLine && (
              <ReferenceLine
                y={referenceLine.value}
                stroke={colors.textMuted}
                strokeDasharray="6 6"
              />
            )}
            {series.map((s) => (
              <Line
                key={s.key}
                type="monotone"
                dataKey={s.key}
                name={s.label}
                stroke={s.cor}
                strokeWidth={2.5}
                dot={{ r: 4.5, fill: s.cor, strokeWidth: 0 }}
                activeDot={{ r: 6 }}
                isAnimationActive={false}
              />
            ))}
          </LineChart>
        </ResponsiveContainer>
      </Box>
      {legend && (
        <Legend
          series={series}
          extra={
            referenceLine
              ? [{ label: referenceLine.label, dashed: true, cor: colors.textMuted }]
              : undefined
          }
        />
      )}
      {!legend && <Typography component="span" sx={{ display: 'none' }} />}
    </Box>
  )
}
