export interface ExecutionEnvironment {
  VITE_JOB_ID?: string
}

/** The runs API addresses jobs; keep the frontend configuration aligned with that contract. */
export function configuredJobId(env: ExecutionEnvironment): string | undefined {
  const jobId = env.VITE_JOB_ID?.trim()
  return jobId || undefined
}
