-- PROVISÓRIA (ADR 0030, fase 1b): contrato de colunas e binds de `citizen`, sem linhas.
-- A consulta real sobre o DW é escrita na fase 1c (adaptadores) e só então recebe entrada na matriz.
SELECT CAST(NULL AS text) AS source_entity_type,
       CAST(NULL AS text) AS source_record_id,
       CAST(NULL AS text) AS municipality_ibge,
       CAST(NULL AS text) AS person_key,
       CAST(NULL AS date) AS birth_date,
       CAST(NULL AS text) AS sex,
       CAST(NULL AS text) AS gender_identity,
       CAST(NULL AS date) AS death_date
 WHERE FALSE
   AND CAST(? AS text) IS NULL
   AND CAST(? AS date) IS NULL
   AND CAST(? AS date) IS NULL
