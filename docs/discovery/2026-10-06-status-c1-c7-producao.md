# Status de C1–C7 em produção (2026-10-06)

Leitura somente leitura do SQLite do CT 170 (`observatorio-aps` 0.1.9), feita em 2026-10-06 para
o esforço de portões e ambiguidades (ADR 0032, em preparação). Só agregados; contagens entre 1 e 9
aparecem como `<10`.

## O que já rodou

O agendador (1 job a cada 6 h, competência mais antiga primeiro) publicou 25 resultados:

| Pack | Versão | Competências | Status |
|---|---|---|---|
| C1 | 0.1.0 | 2024-09 a 2026-01 e 2026-03 | `BLOCKED` em todas |
| C1 | 0.2.0 | 2024-10 a 2025-03 | `BLOCKED` em todas; 18 equipes, todas `BLOCKED` |
| C2 | 0.1.0 | 2026-09 | `RULE_AMBIGUITY`; 5 práticas e 12 equipes, todas `RULE_AMBIGUITY` |

C3–C7 ainda não rodaram em produção. Os status de C3–C7 vêm das execuções locais contra o PEC
5.5.28 (PR #64).

O C1 de 2025-04 tem denominador muito abaixo das outras competências (1378 contra 7 mil a 11 mil). Vale
checar a cobertura dessa competência no PEC antes de reconciliar.

## C1 (0.2.0): por que é `BLOCKED`

Limitações publicadas:
- `cbo_policy=FICHA_24C`;
- Portão D `NOT_IMPLEMENTED`;
- encontros fora dos sete CBO excluídos;
- Portões A, B, D e E incompletos.

Nenhuma ambiguidade. O bloqueio vem só dos portões e das limitações permanentes.

## C2 (2026-09): por que é `RULE_AMBIGUITY`

**Coorte:**
- 392 elegíveis e 67 excluídos (49 sem vínculo, 18 por mudança de território).
- Dos elegíveis, 18 completam 2 anos na competência (AMB-C2-03).

**Decisão por prática** (sujeitos distintos):

| Prática | Cumpre | Não cumpre | Ambígua |
|---|---|---|---|
| A | 0 | 189 | 203 |
| B | 90 | 285 | 17 |
| C | 54 | 286 | 52 |
| D | 82 | 299 | 11 |
| E | 87 | 289 | 16 |

**Códigos que tornam a prática ambígua** (sujeitos distintos):

| Prática | Código | Sujeitos |
|---|---|---|
| A | `DADO_INDISPONIVEL:LACUNA-L3` | 103 |
| A | `AMBIGUIDADE:AMB-C2-06,LACUNA-L3` | 75 |
| C | `AMBIGUIDADE:AMB-C2-07` | 52 |
| E | `AMBIGUIDADE:AMB-C2-09` | 16 |
| A | `AMBIGUIDADE:AMB-C2-06` | 10 |
| A, B, D | AMB-C2-01, -04, -06, -08, -15 (combinações) | `<10` cada |

**Leitura:**
- A lacuna L3 (participação presencial × remota não informada na instalação) responde por quase toda
  a ambiguidade da prática A. Ela vem antes de qualquer ambiguidade da ficha.
- AMB-C2-03 afeta 18 crianças.
- As ambiguidades da ficha que mais pesam são AMB-C2-06 (consulta só por procedimento), AMB-C2-07
  (antropometria isolada) e AMB-C2-09 (vacinas).
- 392 crianças estão em equipe sem tipo comprovado (lacuna L1).
