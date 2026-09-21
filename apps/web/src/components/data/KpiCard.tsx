import type { ReactNode } from 'react'
import Box from '@mui/material/Box'
import Paper from '@mui/material/Paper'
import Typography from '@mui/material/Typography'
import { ArrowUp, CircleX, Info } from 'lucide-react'
import { colors } from '@/theme/tokens'

interface KpiCardProps {
  icon?: ReactNode
  label: string
  value: string
  valueColor?: string
  chip?: { label?: string; value: string }
  trend?: { text: string; tone: 'up' | 'down' }
  caption?: ReactNode
  infoIcon?: boolean
  compact?: boolean
}

export function KpiCard({
  icon,
  label,
  value,
  valueColor = colors.navy,
  chip,
  trend,
  caption,
  infoIcon,
  compact,
}: KpiCardProps) {
  return (
    <Paper
      sx={{
        p: compact ? 1.75 : 2,
        display: 'flex',
        flexDirection: 'column',
        gap: 0.75,
        minWidth: 0,
      }}
    >
      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 1 }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.25, minWidth: 0 }}>
          {icon && <Box sx={{ color: colors.primary, display: 'flex', flexShrink: 0 }}>{icon}</Box>}
          <Typography
            sx={{
              fontSize: compact ? 13 : { xs: 12.5, lg: 14.5 },
              fontWeight: 600,
              color: colors.navy,
              whiteSpace: 'nowrap',
              overflow: 'hidden',
              textOverflow: 'ellipsis',
            }}
          >
            {label}
          </Typography>
        </Box>
        {infoIcon && <Info size={18} color={colors.primary} />}
      </Box>
      <Box
        sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 1.5 }}
      >
        <Typography
          sx={{
            fontSize: compact ? 28 : { xs: 30, lg: 36 },
            fontWeight: 700,
            color: valueColor,
            lineHeight: 1.05,
            letterSpacing: '-1px',
          }}
        >
          {value}
        </Typography>
        {chip && (
          <Box
            sx={{
              bgcolor: colors.primarySoft,
              borderRadius: '10px',
              px: 1.5,
              py: 0.75,
              textAlign: 'center',
              flexShrink: 0,
            }}
          >
            {chip.label && (
              <Typography sx={{ fontSize: 11.5, color: colors.textSecondary, lineHeight: 1.2 }}>
                {chip.label}
              </Typography>
            )}
            <Typography
              sx={{ fontSize: 13.5, fontWeight: 700, color: colors.navy, lineHeight: 1.2 }}
            >
              {chip.value}
            </Typography>
          </Box>
        )}
      </Box>
      {trend && (
        <Box
          sx={{
            display: 'flex',
            alignItems: 'center',
            gap: 0.75,
            color: trend.tone === 'up' ? colors.success : colors.error,
            fontSize: compact ? 11.5 : 12.5,
            fontWeight: 600,
            whiteSpace: 'nowrap',
            overflow: 'hidden',
            textOverflow: 'ellipsis',
          }}
        >
          {trend.tone === 'up' ? (
            <ArrowUp size={16} strokeWidth={2.4} />
          ) : (
            <CircleX size={15} fill={colors.error} color="#fff" />
          )}
          <span>{trend.text}</span>
        </Box>
      )}
      {caption && (
        <Typography sx={{ fontSize: 13, color: colors.textSecondary }}>{caption}</Typography>
      )}
    </Paper>
  )
}
