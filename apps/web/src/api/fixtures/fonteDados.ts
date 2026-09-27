// DEMO DATA — dados de demonstração; credenciais fictícias.
import type { Fonte, RequisitoFonte } from '../types'

export const fonteFixture: Fonte = {
  id: 'pec-demo',
  tipo: 'PostgreSQL (e-SUS PEC)',
  host: '192.0.2.10', // TEST-NET-1 (RFC 5737): documentation range, never a real host
  porta: '5432',
  nomeBanco: 'pec_dw',
  usuario: 'esusdata',
  ultimoTeste: {
    ok: true,
    mensagem: 'Conexão de leitura estabelecida.',
    testadoEm: '2026-09-19T13:05:00Z',
  },
}

export const requisitosFixture: RequisitoFonte[] = [
  { label: 'Conexão de leitura confirmada no último teste', ok: true },
  { label: 'Fonte PostgreSQL do e-SUS PEC', ok: true },
  { label: 'Versão e modelo do PEC na matriz de compatibilidade', ok: true },
  { label: 'Município configurado', ok: true },
]
