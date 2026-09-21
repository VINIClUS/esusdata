SELECT f.co_seq_fat_atd_ind, f.co_dim_tipo_atendimento, t.dt_registro,
       u.nu_cnes, e.nu_ine, c.nu_cbo, f.nu_uuid_ficha, f.nu_atendimento
  FROM public.tb_fat_atendimento_individual f
  JOIN public.tb_dim_tempo      t ON t.co_seq_dim_tempo       = f.co_dim_tempo
  JOIN public.tb_dim_municipio  m ON m.co_seq_dim_municipio   = f.co_dim_municipio
  LEFT JOIN public.tb_dim_unidade_saude u ON u.co_seq_dim_unidade_saude = f.co_dim_unidade_saude_1
  LEFT JOIN public.tb_dim_equipe        e ON e.co_seq_dim_equipe        = f.co_dim_equipe_1
  LEFT JOIN public.tb_dim_cbo           c ON c.co_seq_dim_cbo           = f.co_dim_cbo_1
 WHERE m.co_ibge = ?
   AND t.dt_registro >= ? AND t.dt_registro < ?
 ORDER BY f.co_seq_fat_atd_ind
