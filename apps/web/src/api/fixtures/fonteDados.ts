// DEMO DATA — dados de demonstração; credenciais fictícias.
import type { Fonte, RequisitoFonte } from '../types'

export const fonteFixture: Fonte = {
  tipo: 'PostgreSQL (e-SUS PEC)',
  host: '192.0.2.10', // TEST-NET-1 (RFC 5737): documentation range, never a real host
  porta: '5432',
  nomeBanco: 'pec_dw',
  usuario: 'esusdata',
  senha: 'demo-senha-1',
  ultimoTeste: { ok: true, mensagem: 'Diagnóstico da fonte concluído com sucesso!' },
}

export const requisitosFixture: RequisitoFonte[] = [
  { label: 'Acesso de leitura (somente SELECT)', ok: true },
  { label: 'Família PostgreSQL compatível', ok: true },
  { label: 'Estruturas DW e/ou transacionais', ok: true },
  { label: 'Escopo municipal configurado', ok: true },
]
