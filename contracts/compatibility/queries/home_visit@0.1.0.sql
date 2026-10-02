-- PROVISÓRIA (ADR 0030, fase 1b): contrato de colunas e binds de `home_visit`, sem linhas.
-- A consulta real sobre o DW é escrita na fase 1c (adaptadores) e só então recebe entrada na matriz.
SELECT CAST(NULL AS text) AS source_entity_type,
       CAST(NULL AS text) AS source_record_id,
       CAST(NULL AS text) AS municipality_ibge,
       CAST(NULL AS text) AS person_key,
       CAST(NULL AS date) AS visit_date,
       CAST(NULL AS text) AS cbo,
       CAST(NULL AS text) AS cnes,
       CAST(NULL AS text) AS ine,
       CAST(NULL AS text) AS outcome_code,
       CAST(NULL AS text[]) AS reason_codes,
       CAST(NULL AS text) AS weight_kg,
       CAST(NULL AS text) AS height_cm
 WHERE FALSE
   AND CAST(? AS text) IS NULL
   AND CAST(? AS date) IS NULL
   AND CAST(? AS date) IS NULL
   AND CAST(? AS date) IS NULL
   AND CAST(? AS date) IS NULL
