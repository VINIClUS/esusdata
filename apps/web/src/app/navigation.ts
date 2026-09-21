import {
  ChartColumn,
  ChartLine,
  Database,
  FileText,
  House,
  Settings,
  type LucideIcon,
} from 'lucide-react'

export interface NavItem {
  to: string
  label: string
  shortLabel: string
  icon: LucideIcon
  match: (pathname: string) => boolean
}

export const navItems: NavItem[] = [
  { to: '/painel', label: 'Painel', shortLabel: 'Painel', icon: House, match: (p) => p.startsWith('/painel') },
  {
    to: '/indicadores',
    label: 'Indicadores',
    shortLabel: 'Indicadores',
    icon: ChartColumn,
    match: (p) => p.startsWith('/indicadores'),
  },
  {
    to: '/execucao',
    label: 'Execução de Dados',
    shortLabel: 'Execução',
    icon: Database,
    match: (p) => p.startsWith('/execucao'),
  },
  {
    to: '/base-de-dados',
    label: 'Base de Dados',
    shortLabel: 'Base',
    icon: FileText,
    match: (p) => p.startsWith('/base-de-dados'),
  },
  {
    to: '/relatorios',
    label: 'Relatórios',
    shortLabel: 'Relatórios',
    icon: ChartLine,
    match: (p) => p.startsWith('/relatorios'),
  },
  {
    to: '/configuracoes',
    label: 'Configurações',
    shortLabel: 'Config.',
    icon: Settings,
    match: (p) => p.startsWith('/configuracoes'),
  },
]
