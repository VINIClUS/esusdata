export interface FonteConexao {
  tipo: string
  host: string
  porta: string
  banco: string
  usuario: string
  senha: string
  ultimoTeste: { ok: boolean; mensagem: string } | null
}

export interface RequisitoFonte {
  label: string
  ok: boolean
}
