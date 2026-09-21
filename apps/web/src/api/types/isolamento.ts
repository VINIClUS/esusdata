export interface RegraValidacao {
  nome: string
  descricao: string
  resultado: 'conforme' | 'verificado'
  detalhes: string
}

export interface IsolamentoStatus {
  municipio: string
  municipioUf: string
  ibge: string
  totalCadastros: number
  ultimaValidacao: string
  regras: RegraValidacao[]
}
