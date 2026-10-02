-- PROVISÓRIA (ADR 0030, fase 1b): contrato de colunas e binds de `individual_registration`, sem linhas.
-- A consulta real sobre o DW é escrita na fase 1c (adaptadores) e só então recebe entrada na matriz.
SELECT CAST(NULL AS text) AS source_entity_type,
       CAST(NULL AS text) AS source_record_id,
       CAST(NULL AS text) AS municipality_ibge,
       CAST(NULL AS text) AS person_key,
       CAST(NULL AS date) AS registration_date,
       CAST(NULL AS text) AS cnes,
       CAST(NULL AS text) AS ine,
       CAST(NULL AS boolean) AS simplified,
       CAST(NULL AS boolean) AS inactive,
       CAST(NULL AS boolean) AS refused,
       CAST(NULL AS text) AS exit_reason,
       CAST(NULL AS boolean) AS self_reported_hypertension,
       CAST(NULL AS boolean) AS self_reported_diabetes,
       CAST(NULL AS boolean) AS pregnant
 WHERE FALSE
   AND CAST(? AS text) IS NULL
   AND CAST(? AS date) IS NULL
   AND CAST(? AS date) IS NULL
   AND CAST(? AS date) IS NULL
   AND CAST(? AS date) IS NULL
