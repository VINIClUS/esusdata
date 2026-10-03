import { createBrowserRouter, Navigate } from 'react-router'
import { RequireAuth } from './RequireAuth'
import { LoginPage } from '@/features/acesso/LoginPage'
import { ActivationPage } from '@/features/acesso/ActivationPage'
import { PendingActivationPage } from '@/features/acesso/PendingActivationPage'
import { AccessHelpPage } from '@/features/acesso/AccessHelpPage'
import { PainelPage } from '@/features/painel/PainelPage'
import { IndicadoresListPage } from '@/features/indicadores/IndicadoresListPage'
import { IndicadorDetailPage } from '@/features/indicadores/IndicadorDetailPage'
import { Componente3Page } from '@/features/componente3/Componente3Page'
import { ExecucaoPage } from '@/features/execucoes/ExecucaoPage'
import { FonteDeDadosPage } from '@/features/fontes/FonteDeDadosPage'
import { IsolamentoPage } from '@/features/isolamento/IsolamentoPage'
import { RelatoriosPage } from '@/features/relatorios/RelatoriosPage'
import { AjudaPage } from '@/features/ajuda/AjudaPage'
import { AlertasPage } from '@/features/alertas/AlertasPage'
import { QualidadePage } from '@/features/qualidade/QualidadePage'
import { UsuariosPage } from '@/features/usuarios/UsuariosPage'

export const router = createBrowserRouter([
  { path: '/login', element: <LoginPage /> },
  { path: '/ativar-acesso', element: <ActivationPage /> },
  { path: '/ajuda-acesso', element: <AccessHelpPage /> },
  {
    element: <RequireAuth />,
    children: [
      { path: '/', element: <Navigate to="/painel" replace /> },
      { path: '/painel', element: <PainelPage /> },
      { path: '/alertas', element: <AlertasPage /> },
      { path: '/qualidade', element: <QualidadePage /> },
      { path: '/indicadores', element: <IndicadoresListPage /> },
      // Before the detail: the Nota Final is computed on read and has its own page (ADR 0030).
      { path: '/indicadores/componente-iii', element: <Componente3Page /> },
      { path: '/indicadores/:codigo', element: <IndicadorDetailPage /> },
      { path: '/execucao', element: <ExecucaoPage /> },
      { path: '/base-de-dados', element: <Navigate to="/configuracoes" replace /> },
      { path: '/relatorios', element: <RelatoriosPage /> },
      { path: '/configuracoes', element: <FonteDeDadosPage /> },
      { path: '/configuracoes/fonte-de-dados', element: <Navigate to="/configuracoes" replace /> },
      { path: '/configuracoes/isolamento-municipal', element: <IsolamentoPage /> },
      { path: '/configuracoes/usuarios', element: <UsuariosPage /> },
      { path: '/ajuda', element: <AjudaPage /> },
      { path: '/ativacoes-pendentes', element: <PendingActivationPage /> },
      { path: '*', element: <Navigate to="/painel" replace /> },
    ],
  },
])
