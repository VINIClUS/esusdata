export interface GrantResponse {
  grantId: string
  userId: string
  role: 'TECHNICAL_ADMIN' | 'MANAGER' | 'TEAM_SCOPED_PROFESSIONAL' | 'AUDITOR'
  scopeKind: 'INSTALLATION' | 'MUNICIPALITY'
  municipalityIbge: string | null
  cnes: string | null
  ine: string | null
  grantedAt: string
  grantedBy: string
}

/** `GET /users`: one account and its active grants; never password material. */
export interface UserResponse {
  userId: string
  username: string
  displayName: string
  state: 'PENDING_ACTIVATION' | 'ACTIVE' | 'BLOCKED'
  createdAt: string
  lastLoginAt: string | null
  grants: GrantResponse[]
}

export interface CreateUserResponse {
  userId: string
  activationToken: string
  expiresAt: string
}
