-- PROVISÓRIA (ADR 0030, fase 1b): contrato de colunas e binds de `immunization_history`, sem linhas.
-- A consulta real sobre o DW é escrita na fase 1c (adaptadores) e só então recebe entrada na matriz.
SELECT CAST(NULL AS text) AS source_entity_type,
       CAST(NULL AS text) AS source_record_id,
       CAST(NULL AS text) AS municipality_ibge,
       CAST(NULL AS text) AS person_key,
       CAST(NULL AS date) AS application_date,
       CAST(NULL AS text) AS immunobiological_code,
       CAST(NULL AS text) AS dose_code,
       CAST(NULL AS text) AS strategy_code,
       CAST(NULL AS boolean) AS transcription,
       CAST(NULL AS text) AS cbo,
       CAST(NULL AS text) AS cnes,
       CAST(NULL AS text) AS ine,
       CAST(NULL AS date) AS registration_date
 WHERE FALSE
   AND CAST(? AS text) IS NULL
   AND CAST(? AS date) IS NULL
   AND CAST(? AS date) IS NULL
   AND CAST(? AS date) IS NULL
   AND CAST(? AS date) IS NULL
   AND CAST(? AS text[]) IS NULL
