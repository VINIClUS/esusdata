import { useState } from 'react'
import IconButton from '@mui/material/IconButton'
import InputAdornment from '@mui/material/InputAdornment'
import TextField, { type TextFieldProps } from '@mui/material/TextField'
import Typography from '@mui/material/Typography'
import Box from '@mui/material/Box'
import { Eye, EyeOff, Search, type LucideIcon } from 'lucide-react'
import { colors } from '@/theme/tokens'
import { mergeSx } from '@/theme/sx'

interface FieldProps extends Omit<TextFieldProps, 'label'> {
  label?: string
  icon?: LucideIcon
  large?: boolean
}

/** Labelled input with the label rendered above the field (mockup style). */
export function Field({ label, icon: Icon, large, slotProps, sx, ...rest }: FieldProps) {
  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 0.75, width: '100%' }}>
      {label && (
        <Typography sx={{ fontSize: large ? 17 : 15, fontWeight: 600, color: colors.navy }}>
          {label}
        </Typography>
      )}
      <TextField
        fullWidth
        {...rest}
        slotProps={{
          ...slotProps,
          input: {
            ...(slotProps && 'input' in slotProps ? (slotProps.input as object) : {}),
            startAdornment: Icon ? (
              <InputAdornment position="start">
                <Icon size={large ? 24 : 20} color={colors.textMuted} />
              </InputAdornment>
            ) : undefined,
          },
        }}
        sx={mergeSx(
          { '& .MuiOutlinedInput-root': { height: large ? 62 : 50, fontSize: large ? 17 : 15 } },
          sx,
        )}
      />
    </Box>
  )
}

export function PasswordField({ label, icon, large, ...rest }: FieldProps) {
  const [show, setShow] = useState(false)
  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 0.75, width: '100%' }}>
      {label && (
        <Typography sx={{ fontSize: large ? 17 : 15, fontWeight: 600, color: colors.navy }}>
          {label}
        </Typography>
      )}
      <TextField
        fullWidth
        type={show ? 'text' : 'password'}
        {...rest}
        slotProps={{
          input: {
            startAdornment: icon ? (
              <InputAdornment position="start">
                {(() => {
                  const Icon = icon
                  return <Icon size={large ? 24 : 20} color={colors.textMuted} />
                })()}
              </InputAdornment>
            ) : undefined,
            endAdornment: (
              <InputAdornment position="end">
                <IconButton
                  aria-label={show ? 'Ocultar senha' : 'Mostrar senha'}
                  onClick={() => setShow((s) => !s)}
                  edge="end"
                >
                  {show ? (
                    <EyeOff size={22} color={colors.primary} />
                  ) : (
                    <Eye size={22} color={colors.primary} />
                  )}
                </IconButton>
              </InputAdornment>
            ),
          },
        }}
        sx={{
          '& .MuiOutlinedInput-root': {
            height: large ? 62 : 50,
            fontSize: large ? 17 : 15,
            letterSpacing: show ? 0 : 2,
          },
        }}
      />
    </Box>
  )
}

export function SearchInput({ placeholder = 'Buscar...', ...rest }: TextFieldProps) {
  return (
    <TextField
      fullWidth
      placeholder={placeholder}
      {...rest}
      slotProps={{
        input: {
          startAdornment: (
            <InputAdornment position="start">
              <Search size={20} color={colors.primary} />
            </InputAdornment>
          ),
        },
      }}
      sx={{ '& .MuiOutlinedInput-root': { height: 50, fontSize: 15 } }}
    />
  )
}
