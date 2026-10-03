import type { ValueKind } from '../../api/types'

/** What the numerator and denominator count, by what the value means (ADR 0030). */
export function rotulosDoPar(valueKind: ValueKind): { numerador: string; denominador: string } {
  return valueKind === 'SCORE'
    ? { numerador: 'Soma dos pontos', denominador: 'Elegíveis' }
    : { numerador: 'Numerador', denominador: 'Denominador' }
}

const naturezas: Record<ValueKind, string> = {
  PERCENTAGE: 'Percentual (0 a 100%)',
  SCORE: 'Escore (0 a 100 pontos)',
  COMPOSITE_SCORE: 'Escore composto (0 a 100 pontos)',
  FINAL_SCORE: 'Nota (0 a 10)',
}

/** What a value is, in words, for the detail's header. */
export function naturezaDoValor(valueKind: ValueKind): string {
  return naturezas[valueKind]
}
