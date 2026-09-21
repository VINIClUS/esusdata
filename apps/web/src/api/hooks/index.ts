import { useQuery } from '@tanstack/react-query'
import { USE_MOCKS, apiFetch, resolveMock } from '../client'
import { execucaoFixture } from '../fixtures/execucao'
import { fonteConexaoFixture, requisitosFixture } from '../fixtures/fonteDados'
import { indicadorDetalheFixture, indicadoresFixture } from '../fixtures/indicadores'
import { isolamentoFixture } from '../fixtures/isolamento'
import { painelFixture } from '../fixtures/painel'
import { relatoriosFixture } from '../fixtures/relatorios'
import type {
  ExecucaoAtual,
  FonteConexao,
  IndicadorDetalhe,
  IndicadoresLista,
  IsolamentoStatus,
  PainelResumo,
  RelatorioGerado,
  RequisitoFonte,
} from '../types'

function source<T>(mock: T, path: string) {
  return () => (USE_MOCKS ? resolveMock(mock) : apiFetch<T>(path))
}

export function usePainelResumo() {
  return useQuery({ queryKey: ['painel'], queryFn: source<PainelResumo>(painelFixture, '/painel') })
}

export function useIndicadores() {
  return useQuery({
    queryKey: ['indicadores'],
    queryFn: source<IndicadoresLista>(indicadoresFixture, '/indicator-packs'),
  })
}

export function useIndicadorDetalhe(codigo: string) {
  return useQuery({
    queryKey: ['indicadores', codigo],
    queryFn: () =>
      USE_MOCKS
        ? resolveMock<IndicadorDetalhe>({ ...indicadorDetalheFixture, codigo })
        : apiFetch<IndicadorDetalhe>(`/results/${codigo}`),
  })
}

export function useExecucaoAtual() {
  return useQuery({ queryKey: ['execucao'], queryFn: source<ExecucaoAtual>(execucaoFixture, '/runs/current') })
}

export function useFonteConexao() {
  return useQuery({ queryKey: ['fonte'], queryFn: source<FonteConexao>(fonteConexaoFixture, '/sources') })
}

export function useRequisitosFonte() {
  return useQuery({
    queryKey: ['fonte', 'requisitos'],
    queryFn: source<RequisitoFonte[]>(requisitosFixture, '/sources/requirements'),
  })
}

export function useIsolamento() {
  return useQuery({ queryKey: ['isolamento'], queryFn: source<IsolamentoStatus>(isolamentoFixture, '/scope') })
}

export function useRelatoriosRecentes() {
  return useQuery({ queryKey: ['relatorios'], queryFn: source<RelatorioGerado[]>(relatoriosFixture, '/exports') })
}
