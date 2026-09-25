import { createTheme } from '@mui/material/styles'
import { colors, radius, shadows } from './tokens'

export const theme = createTheme({
  palette: {
    mode: 'light',
    primary: { main: colors.primary, dark: colors.primaryDark, light: colors.primaryLight },
    success: { main: colors.success },
    warning: { main: colors.warning },
    error: { main: colors.error },
    info: { main: colors.info },
    background: { default: colors.bg, paper: colors.paper },
    text: { primary: colors.navy, secondary: colors.textSecondary },
    divider: colors.border,
  },
  shape: { borderRadius: radius.button },
  typography: {
    fontFamily: "'Inter Variable', Inter, system-ui, -apple-system, 'Segoe UI', Roboto, sans-serif",
    h1: {
      fontSize: 32,
      fontWeight: 700,
      lineHeight: 1.15,
      letterSpacing: '-0.5px',
      '@media (max-width:899px)': { fontSize: 26 },
    },
    h2: { fontSize: 22, fontWeight: 700, lineHeight: 1.2 },
    h3: { fontSize: 16, fontWeight: 700, lineHeight: 1.3 },
    h4: { fontSize: 15, fontWeight: 600, lineHeight: 1.3 },
    subtitle1: { fontSize: 15, color: colors.textSecondary },
    subtitle2: { fontSize: 13, color: colors.textSecondary, fontWeight: 400 },
    body1: { fontSize: 14 },
    body2: { fontSize: 13 },
    caption: { fontSize: 12, color: colors.textSecondary },
    button: { textTransform: 'none', fontWeight: 600 },
  },
  components: {
    MuiCssBaseline: {
      styleOverrides: { body: { backgroundColor: colors.bg } },
    },
    MuiPaper: {
      defaultProps: { elevation: 0 },
      styleOverrides: {
        root: {
          borderRadius: radius.card,
          border: `1px solid ${colors.border}`,
          boxShadow: shadows.card,
          backgroundImage: 'none',
        },
      },
    },
    MuiButton: {
      defaultProps: { disableElevation: true },
      styleOverrides: {
        root: {
          borderRadius: radius.button,
          fontSize: 14,
          fontWeight: 600,
          paddingInline: 16,
          minHeight: 40,
        },
        sizeSmall: { minHeight: 34, fontSize: 13, paddingInline: 12 },
        sizeLarge: { minHeight: 52, fontSize: 17 },
        outlined: { borderColor: colors.infoBorder, backgroundColor: colors.paper },
      },
    },
    MuiChip: {
      styleOverrides: {
        root: { borderRadius: radius.chip, fontWeight: 600, fontSize: 12, height: 26 },
        label: { paddingInline: 10 },
      },
    },
    MuiOutlinedInput: {
      styleOverrides: {
        root: {
          borderRadius: radius.input,
          backgroundColor: colors.paper,
          fontSize: 14,
          '& fieldset': { borderColor: colors.border },
          '&:hover fieldset': { borderColor: colors.borderStrong },
          '&.Mui-focused fieldset': { borderWidth: 1.5 },
        },
        input: { paddingBlock: 12 },
      },
    },
    MuiInputLabel: { styleOverrides: { root: { fontSize: 14 } } },
    MuiTableCell: {
      styleOverrides: {
        root: {
          borderBottomColor: colors.border,
          fontSize: 13,
          paddingBlock: 10,
          paddingInline: 12,
        },
        head: {
          backgroundColor: colors.bgSubtle,
          color: colors.textSecondary,
          fontWeight: 600,
          fontSize: 13,
          paddingBlock: 9,
        },
      },
    },
    MuiCheckbox: { styleOverrides: { root: { color: colors.borderStrong } } },
    MuiTab: {
      styleOverrides: {
        root: { textTransform: 'none', fontSize: 15, fontWeight: 500, minHeight: 44 },
      },
    },
    MuiTabs: { styleOverrides: { indicator: { height: 3, borderRadius: 3 } } },
    MuiDivider: { styleOverrides: { root: { borderColor: colors.border } } },
    MuiTooltip: { styleOverrides: { tooltip: { fontSize: 12 } } },
    MuiLinearProgress: {
      styleOverrides: {
        root: { height: 10, borderRadius: 6, backgroundColor: colors.border },
        bar: { borderRadius: 6 },
      },
    },
  },
})
