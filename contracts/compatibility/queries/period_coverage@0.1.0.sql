SELECT m.co_ibge, to_char(t.dt_registro, 'YYYY-MM'), count(*)
  FROM public.tb_fat_atendimento_individual f
  JOIN public.tb_dim_tempo      t ON t.co_seq_dim_tempo     = f.co_dim_tempo
  JOIN public.tb_dim_municipio  m ON m.co_seq_dim_municipio = f.co_dim_municipio
 WHERE t.dt_registro >= ? AND t.dt_registro < ?
 GROUP BY m.co_ibge, to_char(t.dt_registro, 'YYYY-MM')
 ORDER BY m.co_ibge, to_char(t.dt_registro, 'YYYY-MM')
