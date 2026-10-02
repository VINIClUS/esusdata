-- PROVISÓRIA (ADR 0030, fase 1b): contrato de colunas e binds de `dental_encounter`, sem linhas.
-- A consulta real sobre o DW é escrita na fase 1c (adaptadores) e só então recebe entrada na matriz.
SELECT CAST(NULL AS text) AS source_entity_type,
       CAST(NULL AS text) AS source_record_id,
       CAST(NULL AS text) AS municipality_ibge,
       CAST(NULL AS text) AS person_key,
       CAST(NULL AS date) AS care_date,
       CAST(NULL AS text) AS form,
       CAST(NULL AS text) AS cbo,
       CAST(NULL AS text) AS cnes,
       CAST(NULL AS text) AS ine,
       CAST(NULL AS text) AS care_type_code,
       CAST(NULL AS text) AS care_location_code,
       CAST(NULL AS boolean) AS remote,
       CAST(NULL AS text[]) AS ciap_codes,
       CAST(NULL AS text[]) AS cid_codes,
       CAST(NULL AS text[]) AS procedures_requested,
       CAST(NULL AS text[]) AS procedures_evaluated,
       CAST(NULL AS text[]) AS procedures_performed,
       CAST(NULL AS text) AS weight_kg,
       CAST(NULL AS text) AS height_cm,
       CAST(NULL AS text) AS systolic_mmhg,
       CAST(NULL AS text) AS diastolic_mmhg,
       CAST(NULL AS date) AS lmp_date,
       CAST(NULL AS integer) AS gestational_age_weeks,
       CAST(NULL AS boolean) AS pregnant,
       CAST(NULL AS date) AS birth_date
 WHERE FALSE
   AND CAST(? AS text) IS NULL
   AND CAST(? AS date) IS NULL
   AND CAST(? AS date) IS NULL
   AND CAST(? AS date) IS NULL
   AND CAST(? AS date) IS NULL
