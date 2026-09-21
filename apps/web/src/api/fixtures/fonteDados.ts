// DEMO DATA — dados de demonstração; credenciais fictícias.
import type { FonteConexao, RequisitoFonte } from '../types'

export const fonteConexaoFixture: FonteConexao = {
  tipo: 'PostgreSQL (e-SUS PEC)',
  host: '192.168.1.100',
  porta: '5432',
  banco: 'pec_dw',
  usuario: 'esusdata',
  senha: 'demo-senha-1',
  ultimoTeste: { ok: true, mensagem: 'Conexão estabelecida com sucesso!' },
}

export const requisitosFixture: RequisitoFonte[] = [
  { label: 'Acesso de leitura (somente SELECT)', ok: true },
  { label: 'Banco PostgreSQL do e-SUS PEC', ok: true },
  { label: 'Estruturas DW e/ou transacionais', ok: true },
  { label: 'Escopo municipal configurado', ok: true },
]
