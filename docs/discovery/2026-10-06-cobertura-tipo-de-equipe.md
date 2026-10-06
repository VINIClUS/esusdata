# Cobertura do tipo de equipe nos vínculos de 2026-08 (2026-10-06)

Evidência para fechar a lacuna L1 (tipo de equipe, `team`), conforme os registros de decisão C1–C7:
"L1 só fecha com a capacidade `team` VALIDATED, **cobertura comprovada** (todo INE com vínculo na
competência tem tipo) e a regra de tipo aplicada". Só contagens; de 1 a 9 aparece `<10`. Nenhum INE,
CNES, chave de pessoa ou nome está neste documento.

## Como foi feito

- Teste vivo somente leitura,
  [`TeamCoverageLiveTest`](../../apps/agent/src/test/java/esusdata/source/pec/TeamCoverageLiveTest.java),
  contra o PEC 5.5.28 de produção, sessão `readOnly` desde o login (`LivePecInventory`), com o mesmo
  opt-in dos demais testes vivos. Comando:

  ```bash
  mvn -o -B -f apps/agent/pom.xml test -Djacoco.skip=true -Dsurefire.reuseForks=false \
    -Dtest=TeamCoverageLiveTest -Dsurefire.failIfNoSpecifiedTests=false \
    -Dobservatorio.execution-plane.live-pec=true \
    -Dobservatorio.execution-plane.live-pec.env-file=$HOME/.config/observatorio-aps/pec-253.env \
    -Dobservatorio.team-coverage.ibge=<IBGE> -Dobservatorio.team-coverage.competencia=2026-08
  ```

- Duas leituras: (1) a consulta congelada de `team@0.1.0` para o município, como o aplicativo a roda;
  (2) um agregado sobre `tb_fat_cad_individual`: por INE, quantas pessoas têm a versão mais recente
  do cadastro até 2026-08-31 nem inativa nem recusada vinculada a ele. Nenhuma linha de pessoa chega
  ao Java; o agregado já sai por INE.
- A regra aplicada é a de produção, `TeamScope`: tipo `70` eSF, `76` eAP, vigente no último dia da
  competência (`valid_from <= dia < valid_to`, `valid_to` exclusivo); sem estado que cubra o dia, vale
  o mais recente que começou antes dele; dois tipos no dia são conflito.
- O túnel foi aberto e fechado pelo socket de controle (`~/.ssh/pec-tunel.sock`); nada foi escrito
  no PEC.

## Resultado (competência 2026-08, último dia 2026-08-31)

| Medida | Valor |
|---|---|
| Linhas de estado de equipe lidas (`team`) | 83 |
| INEs distintos lidos (`team`) | 29 |
| INEs com cadastro ativo vinculado | 26 |
| Pessoas com cadastro ativo (com e sem INE) | 42382 |
| Pessoas sem INE (já saem como "sem vínculo") | 822 |

| Veredito da regra | INEs | Pessoas |
|---|---|---|
| eSF (70) | 12 | 35220 |
| eAP (76) | `<10` | 6167 |
| Outro tipo (fora do escopo) | 12 | 173 |
| **Sem tipo** | **0** | **0** |
| **Tipo conflitante** | **0** | **0** |

Os 26 INEs vinculados se dividem em 12 eSF, `<10` eAP (o complemento de 26 dá 2) e 12 de outro tipo
(equipes de apoio, como eMulti e eSB, que a ficha deixa fora).

## O que isso decide

- **A cobertura está completa:** todo INE com vínculo ativo na competência 2026-08 tem tipo válido
  na data, e nenhum tem dois tipos. A condição do cabeçalho dos registros ("todo INE com vínculo na
  competência tem tipo") vale para este município nesta competência. O 29 de `team` contra os 30 INEs
  do DW (ver [equipe transacional](2026-10-06-pec-5528-equipe-transacional.md)) não atinge nenhum
  vínculo ativo.
- **Efeito esperado da regra:** em 2026-08 as pessoas de equipes de outro tipo (173, 0,4 % dos
  vínculos ativos) saem das coortes, e as de eAP (6167) passam a ter a prática creditada nas regras
  que a ficha cita (C2 D, C3 E e J, C4 D, C5 D, C6 C). Os 822 sem INE já não entravam.
- **Limite declarado:** a cobertura é de uma competência (2026-08). Cada execução divulga a contagem
  por motivo (`C*-LIM-xx/contagem`): se uma competência futura trouxer INE sem tipo ou com conflito, as
  pessoas saem com motivo e contagem, e a lacuna reabre como fato de execução, não de regra.
- **C1 não fecha.** C1 ainda lê o extrato canônico v1 (`individual_encounter_modality`), que não traz a
  capacidade `team`. A regra de INE do C1 (C1-D2) está implementada e só atua quando o extrato traz a
  parte `team`; em produção ela não atua, e C1-LIM-03 continua `BLOCKING_GAP`. Fechar exige um contrato
  v2 de C1 (fatia à parte).
