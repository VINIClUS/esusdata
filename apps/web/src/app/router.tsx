import { createBrowserRouter, Navigate } from 'react-router'
import { RequireAuth } from './RequireAuth'
import { LoginPage } from '@/features/auth/LoginPage'
import { PainelPage } from '@/features/painel/PainelPage'
import { IndicadoresListPage } from '@/features/indicadores/IndicadoresListPage'
import { IndicadorDetailPage } from '@/features/indicadores/IndicadorDetailPage'
import { ExecucaoPage } from '@/features/execucao/ExecucaoPage'
import { FonteDeDadosPage } from '@/features/fonte-de-dados/FonteDeDadosPage'
import { IsolamentoPage } from '@/features/isolamento/IsolamentoPage'
import { RelatoriosPage } from '@/features/relatorios/RelatoriosPage'
import { AjudaPage } from '@/features/ajuda/AjudaPage'

export const router = createBrowserRouter([
  { path: '/login', element: <LoginPage /> },
  {
    element: <RequireAuth />,
    children: [
      { path: '/', element: <Navigate to="/painel" replace /> },
      { path: '/painel', element: <PainelPage /> },
      { path: '/indicadores', element: <IndicadoresListPage /> },
      { path: '/indicadores/:codigo', element: <IndicadorDetailPage /> },
      { path: '/execucao', element: <ExecucaoPage /> },
      { path: '/base-de-dados', element: <FonteDeDadosPage /> },
      { path: '/relatorios', element: <RelatoriosPage /> },
      { path: '/configuracoes', element: <FonteDeDadosPage /> },
      { path: '/configuracoes/fonte-de-dados', element: <Navigate to="/configuracoes" replace /> },
      { path: '/configuracoes/isolamento-municipal', element: <IsolamentoPage /> },
      { path: '/ajuda', element: <AjudaPage /> },
      { path: '*', element: <Navigate to="/painel" replace /> },
    ],
  },
])
