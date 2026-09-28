export const colors = {
  primary: '#1560dc',
  primaryDark: '#1558d6',
  primaryLight: '#e8f0fe',
  primarySoft: '#eaf2ff',
  navy: '#0f2a5c',
  navyDeep: '#0a1c42',
  navyMid: '#12305f',
  textSecondary: '#5b6b85',
  textMuted: '#7c8aa3',
  bg: '#f3f6fb',
  bgSubtle: '#f7f9fc',
  paper: '#ffffff',
  border: '#e3e9f2',
  borderStrong: '#cfd9e8',
  success: '#15803d',
  successBg: '#eafaf0',
  successBorder: '#bfe8cc',
  warning: '#f59e0b',
  // `warning` fills icons and borders; text on light backgrounds needs this darker tone (WCAG AA).
  warningText: '#b45309',
  warningBg: '#fff7ea',
  error: '#b91c1c',
  errorBg: '#fdecec',
  info: '#1560dc',
  infoBg: '#eaf2ff',
  infoBorder: '#c9dcfb',
  purple: '#8b5cf6',
} as const

export const radius = {
  card: 14,
  button: 10,
  input: 10,
  chip: 999,
} as const

export const shadows = {
  card: '0 1px 2px rgba(15, 42, 92, 0.05), 0 2px 8px rgba(15, 42, 92, 0.05)',
  cardHover: '0 4px 14px rgba(15, 42, 92, 0.10)',
} as const

export const layout = {
  sidebarWidth: 228,
  topBarHeight: 64,
  bottomNavHeight: 64,
} as const
