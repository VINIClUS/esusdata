-- PROVISÓRIA (ADR 0030, fase 1b): contrato de colunas e binds de `condition_list`, sem linhas.
-- A consulta real sobre o DW é escrita na fase 1c (adaptadores) e só então recebe entrada na matriz.
SELECT CAST(NULL AS text) AS source_entity_type,
       CAST(NULL AS text) AS source_record_id,
       CAST(NULL AS text) AS municipality_ibge,
       CAST(NULL AS text) AS person_key,
       CAST(NULL AS text) AS code_system,
       CAST(NULL AS text) AS code,
       CAST(NULL AS date) AS recorded_date,
       CAST(NULL AS text) AS status,
       CAST(NULL AS date) AS resolved_date,
       CAST(NULL AS text) AS basis,
       CAST(NULL AS text) AS cbo
 WHERE FALSE
   AND CAST(? AS text) IS NULL
   AND CAST(? AS date) IS NULL
   AND CAST(? AS date) IS NULL
   AND CAST(? AS date) IS NULL
   AND CAST(? AS date) IS NULL
   AND CAST(? AS text[]) IS NULL
   AND CAST(? AS text[]) IS NULL
