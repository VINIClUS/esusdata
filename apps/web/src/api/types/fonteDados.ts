export interface Fonte {
  tipo: string
  host: string
  porta: string
  nomeBanco: string
  usuario: string
  senha: string
  ultimoTeste: { ok: boolean; mensagem: string } | null
}

export interface RequisitoFonte {
  label: string
  ok: boolean
}
