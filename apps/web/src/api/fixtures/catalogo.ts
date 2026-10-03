// DEMO DATA — dados de demonstração; não usar como referência clínica ou operacional.
// `GET /indicator-packs` como a API o descreve (ADR 0030): códigos, títulos, práticas, pesos e janelas
// são os dos descritores C1–C7 e da Nota Final. No demo o C1 aparece liberado, para mostrar um valor
// publicado; C2–C7 seguem com os portões incompletos, como sairão até a validação.
import type { IndicatorPack, PackComponentSpec } from '../types'

const PACOTE = 'qualidade-esf-eap-2026-06'
const FAMILIA = 'QUALIDADE_ESF_EAP'
const FICHAS =
  'https://www.gov.br/saude/pt-br/composicao/saps/publicacoes/fichas-tecnicas/equipe-de-atencao-primaria-e-saude-da-familia'

const PORTOES = [
  'Portão A (fonte e vigência) incompleto',
  'Portão B (modelo de cálculo) incompleto',
  'Portão C (adaptador) incompleto',
  'Portão D (reconciliação) incompleto',
  'Portão E (piloto e operação) incompleto',
]

const FORA_DO_PEC_LOCAL =
  'O PEC municipal não tem os registros de outros municípios que a ficha considera (qualquer profissional do país).'
const SEM_TIPO_DE_EQUIPE =
  'Sem o tipo de equipe na fonte (eSF 70 / eAP 76), a exceção da eAP não é aplicada.'

function pratica(code: string, label: string, weight: number, window: string): PackComponentSpec {
  return { code, label, kind: 'PRACTICE', weight: String(weight), window }
}

function subgrupo(code: string, label: string, weight: number, window: string): PackComponentSpec {
  return { code, label, kind: 'SUBGROUP', weight: String(weight), window }
}

/** One component indicator of the Nota Final, weighted as in NT nº 8/2026 (1/2/2/1/1/1/2). */
function indicador(code: string, label: string, weight: number): PackComponentSpec {
  return { code, label, kind: 'INDICATOR', weight: String(weight), window: 'quadrimestre' }
}

function pacote(
  id: string,
  code: string,
  title: string,
  ficha: string,
  doc: string,
  requiredCapabilities: string[],
  components: PackComponentSpec[],
  standingLimitations: string[],
  valueKind: 'SCORE' | 'COMPOSITE_SCORE' = 'SCORE',
): IndicatorPack {
  return {
    id,
    ruleVersion: `${id}@0.1.0`,
    family: FAMILIA,
    unit: 'percentual',
    dependsOn: [],
    executionEnabled: false,
    blockedGates: PORTOES,
    code,
    title,
    packageId: PACOTE,
    valueKind,
    components,
    requiredCapabilities,
    methodologySources: [`${FICHAS}/${ficha}`, `docs/metodologia/${doc}.md`],
    standingLimitations,
    runnable: true,
  }
}

const ATE_DOIS_ANOS = 'até 2 anos de vida'
const GESTACAO = 'gestação'
const PUERPERIO = 'puerpério'
const SEIS_MESES = '6 meses'
const DOZE_MESES = '12 meses'

export const catalogoFixture: IndicatorPack[] = [
  {
    id: 'c1-mais-acesso',
    ruleVersion: 'c1-mais-acesso@0.1.0',
    family: FAMILIA,
    unit: 'percentual',
    dependsOn: [],
    executionEnabled: true,
    blockedGates: [],
    code: 'C1',
    title: 'Mais acesso',
    packageId: PACOTE,
    valueKind: 'PERCENTAGE',
    components: [],
    requiredCapabilities: ['individual_encounter_modality'],
    methodologySources: [
      `${FICHAS}/nota-metodologica-c1-mais-acesso`,
      'docs/metodologia/c1-mais-acesso.md',
    ],
    standingLimitations: [],
    runnable: true,
  },
  pacote(
    'c2-desenvolvimento-infantil',
    'C2',
    'Cuidado no desenvolvimento infantil',
    'nota-metodologica-c2-cuidado-no-desenvolvimento-infantil',
    'c2-desenvolvimento-infantil',
    [
      'citizen',
      'individual_registration',
      'care_encounter',
      'procedure_performed',
      'home_visit',
      'measurement_record',
      'immunization_history',
    ],
    [
      pratica(
        'A',
        'Ter a 1ª consulta presencial realizada por médica(o) ou enfermeira(o), até o 30º dia de vida.',
        20,
        'até o 30º dia de vida',
      ),
      pratica(
        'B',
        'Ter pelo menos 09 (nove) consultas presenciais ou remotas realizadas por médica(o) ou enfermeira(o) até dois anos de vida.',
        20,
        ATE_DOIS_ANOS,
      ),
      pratica(
        'C',
        'Ter pelo menos 09 (nove) registros simultâneos de peso e altura até os dois anos de vida.',
        20,
        ATE_DOIS_ANOS,
      ),
      pratica(
        'D',
        'Ter pelo menos 02 (duas) visitas domiciliares realizadas por ACS/TACS, sendo a primeira até os primeiros 30 (trinta) dias de vida e a segunda até os 06 (seis) meses de vida.',
        20,
        'até 30 dias e até 6 meses de vida',
      ),
      pratica(
        'E',
        'Ter vacinas contra difteria, tétano, coqueluche, hepatite B, infecções causadas por Haemophilus influenzae tipo b, poliomielite, sarampo, caxumba e rubéola, pneumocócica, registradas com todas as doses recomendadas.',
        20,
        ATE_DOIS_ANOS,
      ),
    ],
    [FORA_DO_PEC_LOCAL, SEM_TIPO_DE_EQUIPE],
  ),
  pacote(
    'c3-gestacao-puerperio',
    'C3',
    'Cuidado na gestação e puerpério',
    'nota-metodologica-c3-cuidado-na-gestacao-e-puerperio',
    'c3-gestacao-puerperio',
    [
      'citizen',
      'individual_registration',
      'care_encounter',
      'dental_encounter',
      'procedure_performed',
      'exam_request_evaluation',
      'home_visit',
      'measurement_record',
      'immunization_history',
    ],
    [
      pratica(
        'A',
        'Ter a 1ª consulta presencial ou remota realizada por médica(o) ou enfermeira(o), até a 12ª semana de gestação.',
        10,
        'até a 12ª semana de gestação',
      ),
      pratica(
        'B',
        'Ter pelo menos 07 (sete) consultas presenciais ou remotas realizadas por médica(o) ou enfermeira(o) durante o período da gestação.',
        9,
        GESTACAO,
      ),
      pratica(
        'C',
        'Ter pelo menos 07 (sete) registros de aferição de pressão arterial realizadas durante o período da gestação.',
        9,
        GESTACAO,
      ),
      pratica(
        'D',
        'Ter pelo menos 07 (sete) registros simultâneos de peso e altura durante o período da gestação.',
        9,
        GESTACAO,
      ),
      pratica(
        'E',
        'Ter pelo menos 03 (três) visitas domiciliares realizadas por ACS/TACS, após a primeira consulta do pré-natal.',
        9,
        'após a 1ª consulta do pré-natal',
      ),
      pratica(
        'F',
        'Ter vacina acelular contra difteria, tétano, coqueluche (dTpa) registrada a partir da 20ª semana de cada gestação.',
        9,
        'a partir da 20ª semana',
      ),
      pratica(
        'G',
        'Ter registro dos testes rápidos ou dos exames avaliados para sífilis, HIV e hepatites B e C realizados no 1º trimestre de cada gestação.',
        9,
        '1º trimestre',
      ),
      pratica(
        'H',
        'Ter registro dos testes rápidos ou dos exames avaliados para sífilis e HIV realizados no 3º trimestre de cada gestação.',
        9,
        '3º trimestre',
      ),
      pratica(
        'I',
        'Ter pelo menos 01 registro de consulta presencial ou remota realizada por médica(o) ou enfermeira(o) durante o puerpério.',
        9,
        PUERPERIO,
      ),
      pratica(
        'J',
        'Ter pelo menos 01 visita domiciliar realizada por ACS/TACS durante o puerpério.',
        9,
        PUERPERIO,
      ),
      pratica(
        'K',
        'Ter pelo menos 01 atividade em saúde bucal realizada por cirurgiã(ão) dentista ou técnica(o) de saúde bucal durante o período da gestação.',
        9,
        GESTACAO,
      ),
    ],
    [
      FORA_DO_PEC_LOCAL,
      'Sem a data de desfecho da gestação no DW, o fim do puerpério usa a data substitutiva de 294 dias.',
    ],
  ),
  pacote(
    'c4-cuidado-diabetes',
    'C4',
    'Cuidado da pessoa com diabetes',
    'nota-metodologica-c4-cuidado-da-pessoa-com-diabetes',
    'c4-cuidado-diabetes',
    [
      'citizen',
      'individual_registration',
      'care_encounter',
      'procedure_performed',
      'exam_request_evaluation',
      'home_visit',
      'measurement_record',
      'condition_list',
    ],
    [
      pratica(
        'A',
        'Ter pelo menos 01 (uma) consulta presencial ou remota realizadas por médica(o) ou enfermeira(o), nos últimos 06 (seis) meses.',
        20,
        SEIS_MESES,
      ),
      pratica(
        'B',
        'Ter pelo menos 01 (um) registro de aferição de pressão arterial realizado nos últimos 06 (seis) meses.',
        15,
        SEIS_MESES,
      ),
      pratica(
        'C',
        'Ter realizado pelo menos 01 (um) registro de peso e altura, nos últimos 12 meses.',
        15,
        DOZE_MESES,
      ),
      pratica(
        'D',
        'Ter pelo menos 02 (duas) visitas domiciliares por ACS/TACS, com intervalo mínimo de 30 dias, realizadas nos últimos 12 meses.',
        20,
        DOZE_MESES,
      ),
      pratica(
        'E',
        'Ter pelo menos 01 (um) registro de Hemoglobina Glicada, solicitada ou avaliada, nos últimos 12 meses',
        15,
        DOZE_MESES,
      ),
      pratica(
        'F',
        'Ter pelo menos 01 (um) registro de avaliação dos pés, realizado nos últimos 12 meses',
        15,
        DOZE_MESES,
      ),
    ],
    [FORA_DO_PEC_LOCAL, SEM_TIPO_DE_EQUIPE],
  ),
  pacote(
    'c5-cuidado-hipertensao',
    'C5',
    'Cuidado da pessoa com hipertensão',
    'nota-metodologica-c5-cuidado-da-pessoa-com-hipertensao',
    'c5-cuidado-hipertensao',
    [
      'citizen',
      'individual_registration',
      'care_encounter',
      'procedure_performed',
      'home_visit',
      'measurement_record',
      'condition_list',
    ],
    [
      pratica(
        'A',
        'Ter pelo menos 01 (uma) consulta presencial ou remota realizadas por médica(o) ou enfermeira(o), nos últimos 06 (seis) meses.',
        25,
        SEIS_MESES,
      ),
      pratica(
        'B',
        'Ter pelo menos 01 (um) registro de aferição de pressão arterial realizado nos últimos 06 (seis) meses.',
        25,
        SEIS_MESES,
      ),
      pratica(
        'C',
        'Ter pelo menos 01 (um) registro simultâneos de peso e altura realizado nos últimos 12 (doze) meses.',
        25,
        DOZE_MESES,
      ),
      pratica(
        'D',
        'Ter pelo menos 02 (duas) visitas domiciliares realizadas por ACS/TACS, com intervalo mínimo de 30 (trinta) dias, nos últimos 12 (doze) meses.',
        25,
        DOZE_MESES,
      ),
    ],
    [FORA_DO_PEC_LOCAL, SEM_TIPO_DE_EQUIPE],
  ),
  pacote(
    'c6-cuidado-pessoa-idosa',
    'C6',
    'Cuidado da pessoa idosa',
    'nota-metodologica-c6-cuidado-da-pessoa-idosa',
    'c6-cuidado-pessoa-idosa',
    [
      'citizen',
      'individual_registration',
      'care_encounter',
      'procedure_performed',
      'home_visit',
      'measurement_record',
      'immunization_history',
    ],
    [
      pratica(
        'A',
        'Ter registro de pelo menos 01 (uma) consulta presencial ou remota por profissional médica(o) ou enfermeira(o) realizada nos últimos 12 meses.',
        25,
        DOZE_MESES,
      ),
      pratica(
        'B',
        'Ter realizado pelo menos 01 (um) registro simultâneo (no mesmo dia) de peso e altura para avaliação antropométrica nos últimos 12 meses.',
        25,
        DOZE_MESES,
      ),
      pratica(
        'C',
        'Ter pelo menos 02 (duas) visitas domiciliares realizadas por ACS/TACS, com intervalo mínimo de 30 (trinta) dias entre as visitas, realizadas nos últimos 12 meses.',
        25,
        DOZE_MESES,
      ),
      pratica(
        'D',
        'Ter registro de 01 (uma) dose da vacina contra influenza, nos últimos 12 meses.',
        25,
        DOZE_MESES,
      ),
    ],
    [FORA_DO_PEC_LOCAL, 'Doses registradas fora do PEC local (RNDS) não são lidas.'],
  ),
  pacote(
    'c7-prevencao-cancer',
    'C7',
    'Cuidado da mulher na prevenção do câncer',
    'nota-metodologica-c7-cuidado-da-mulher-na-prevencao-do-cancer',
    'c7-prevencao-cancer',
    [
      'citizen',
      'individual_registration',
      'care_encounter',
      'procedure_performed',
      'exam_request_evaluation',
      'immunization_history',
    ],
    [
      subgrupo(
        'A',
        'Ter pelo menos 01 (um) exame de rastreamento para câncer do colo do útero em mulheres e em homens transgênero de 25 a 64 anos de idade, coletado, solicitado ou avaliado nos últimos 36 meses, exceto quando se tratar do procedimento SIGTAP 02.02.10.025-1 - Exame molecular de detecção de HPV, que será considerada a janela temporal de 60 meses.',
        20,
        '36 meses (60 meses para 02.02.10.025-1)',
      ),
      subgrupo(
        'B',
        'Ter pelo menos 01 (uma) dose da vacina HPV para crianças e adolescentes do sexo feminino de 09 a 14 anos de idade.',
        30,
        'dose entre 9 e 14 anos de idade',
      ),
      subgrupo(
        'C',
        'Ter pelo 01 (um) atendimento presencial ou remoto, para adolescentes e mulheres e homens transgênero de 14 a 69 anos de idade, sobre atenção à saúde sexual e reprodutiva, realizado nos últimos 12 meses.',
        30,
        DOZE_MESES,
      ),
      subgrupo(
        'D',
        'Ter pelo menos 01 (um) exame de rastreamento para câncer de mama em mulheres e em homens transgênero de 50 a 69 anos de idade, solicitado ou avaliado nos últimos 24 meses.',
        20,
        '24 meses',
      ),
    ],
    [FORA_DO_PEC_LOCAL, 'Doses registradas fora do PEC local (RNDS) não são lidas.'],
    'COMPOSITE_SCORE',
  ),
  {
    id: 'componente-iii-nota-final',
    ruleVersion: 'componente-iii-nota-final@0.1.0',
    family: FAMILIA,
    unit: 'pontos (0 a 10)',
    dependsOn: [
      'c1-mais-acesso',
      'c2-desenvolvimento-infantil',
      'c3-gestacao-puerperio',
      'c4-cuidado-diabetes',
      'c5-cuidado-hipertensao',
      'c6-cuidado-pessoa-idosa',
      'c7-prevencao-cancer',
    ],
    executionEnabled: false,
    blockedGates: PORTOES,
    code: 'Componente III',
    title: 'Nota Final do Componente III (qualidade)',
    packageId: 'cofin-quad-nt08-2026',
    valueKind: 'FINAL_SCORE',
    components: [
      indicador('c1-mais-acesso', 'C1 — Mais acesso', 1),
      indicador('c2-desenvolvimento-infantil', 'C2 — Cuidado no desenvolvimento infantil', 2),
      indicador('c3-gestacao-puerperio', 'C3 — Cuidado na gestação e puerpério', 2),
      indicador('c4-cuidado-diabetes', 'C4 — Cuidado da pessoa com diabetes', 1),
      indicador('c5-cuidado-hipertensao', 'C5 — Cuidado da pessoa com hipertensão', 1),
      indicador('c6-cuidado-pessoa-idosa', 'C6 — Cuidado da pessoa idosa', 1),
      indicador('c7-prevencao-cancer', 'C7 — Cuidado da mulher na prevenção do câncer', 2),
    ],
    requiredCapabilities: [],
    methodologySources: [
      'https://sisaps.saude.gov.br/sistemas/siaps/assets/files/NT_08-2025_cvat-8638ee08a7310014262c2326c234d35a.pdf',
      'docs/metodologia/componente-iii-nt08-2026.md',
    ],
    standingLimitations: [
      'Sem os meses válidos para pagamento, que não estão no PEC, a suspensão de pagamento (item 4.1.1) não é aplicada.',
    ],
    runnable: false,
  },
]
