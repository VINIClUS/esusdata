-- identity-access schema (Tech Spec §1.12.7/§1.4.2/§1.7.3/§1.5, ENG-44/ENG-45/ENG-49).
-- V1/V2 are immutable (§1.12.2: no retroactive migration edits, no automatic repair) — everything
-- here is additive.
--
-- Roles and the role->permission matrix are a PROJECT DECISION, not a spec requirement: the spec
-- names only three permissions (manage_source, manage_access, read_clinical, L513) and four role
-- labels (administrador técnico, gestor, profissional com escopo de equipe, auditor, L427) without
-- a mapping between them. `run_indicator` and `audit` are added here because the API/job-runner
-- need a permission to gate them and the spec's three don't cover it. The full matrix and its
-- rationale are recorded in docs/adr/0007-modelo-de-permissoes.md — do not cite this file as if it
-- were normative text from the Tech Spec.

-- -------------------------------------------------------------------------------------------
-- roles / role_permissions: role is a persisted entity, not a UI label. §1.12.7 requires
-- invalidating sessions and bumping authorization_version on "mudança de papel/escopo" — that is
-- only testable if a role change is a real row change. TECHNICAL_ADMIN never receives
-- read_clinical in any row of role_permissions — this is the structural half of ENG-45
-- ("técnico não se concede escopo clínico"), enforced by what the seed data omits, not by a
-- runtime check that could be bypassed.
-- -------------------------------------------------------------------------------------------

CREATE TABLE roles (
    role_id     TEXT PRIMARY KEY CHECK (role_id IN
                ('TECHNICAL_ADMIN', 'MANAGER', 'TEAM_SCOPED_PROFESSIONAL', 'AUDITOR')),
    description TEXT NOT NULL
);

CREATE TABLE role_permissions (
    role_id    TEXT NOT NULL REFERENCES roles (role_id),
    permission TEXT NOT NULL CHECK (permission IN
               ('manage_source', 'manage_access', 'read_clinical', 'run_indicator', 'audit')),
    PRIMARY KEY (role_id, permission)
);

INSERT INTO roles (role_id, description) VALUES
    ('TECHNICAL_ADMIN', 'Administra fonte e acesso; nunca recebe read_clinical (ENG-45, L548).'),
    ('MANAGER', 'Gestor: lê resultados clínicos e dispara execuções no escopo autorizado.'),
    ('TEAM_SCOPED_PROFESSIONAL', 'Profissional com escopo de equipe: lê resultados clínicos no escopo da equipe (L427).'),
    ('AUDITOR', 'Auditor: lê trilha de auditoria e evidências; não administra fonte/acesso nem dispara execuções.');

INSERT INTO role_permissions (role_id, permission) VALUES
    ('TECHNICAL_ADMIN', 'manage_source'),
    ('TECHNICAL_ADMIN', 'manage_access'),
    ('MANAGER', 'read_clinical'),
    ('MANAGER', 'run_indicator'),
    ('TEAM_SCOPED_PROFESSIONAL', 'read_clinical'),
    ('AUDITOR', 'audit'),
    ('AUDITOR', 'read_clinical');

-- -------------------------------------------------------------------------------------------
-- users: no plaintext password, no recovery question, no default account (§1.12.7 L536).
-- password_params_json records the EFFECTIVE Argon2id parameters used for that hash (L534:
-- "calibrar custo no SO-alvo e registrar parâmetros efetivos"), independent of whatever the
-- current application.yml baseline says — a later baseline change must not silently reinterpret
-- an old hash.
-- -------------------------------------------------------------------------------------------

CREATE TABLE users (
    user_id                 TEXT PRIMARY KEY,
    username                TEXT NOT NULL UNIQUE,
    display_name            TEXT NOT NULL,
    password_hash           TEXT NOT NULL,
    password_algo           TEXT NOT NULL,
    password_params_json    TEXT NOT NULL,
    security_policy_version TEXT NOT NULL,
    authorization_version   INTEGER NOT NULL DEFAULT 1,
    state                   TEXT NOT NULL CHECK (state IN ('PENDING_ACTIVATION', 'ACTIVE', 'BLOCKED')),
    created_at              TEXT NOT NULL,
    created_by              TEXT,
    last_login_at           TEXT
);

-- -------------------------------------------------------------------------------------------
-- user_grants: municipality_ibge is the canonical territorial scope key (§1.4.2 L138, 7-digit
-- text) OR the grant is explicitly INSTALLATION-scoped for technical/audit configuration that
-- must not inherit clinical access (L140) — the CHECK constraint makes exactly one of those two
-- shapes representable, not a convention enforced only in Java. Rows are never deleted on
-- revocation (revoked_at instead) so "concessões atuais" (§1.7.3 L274) means "revoked_at IS NULL"
-- and history stays auditable.
-- -------------------------------------------------------------------------------------------

CREATE TABLE user_grants (
    grant_id          TEXT PRIMARY KEY,
    user_id           TEXT NOT NULL REFERENCES users (user_id),
    role_id           TEXT NOT NULL REFERENCES roles (role_id),
    scope_kind        TEXT NOT NULL CHECK (scope_kind IN ('MUNICIPALITY', 'INSTALLATION')),
    municipality_ibge TEXT,
    cnes              TEXT,
    ine               TEXT,
    granted_at        TEXT NOT NULL,
    granted_by        TEXT NOT NULL,
    revoked_at        TEXT,
    revoked_by        TEXT,
    CHECK (
        (scope_kind = 'INSTALLATION' AND municipality_ibge IS NULL AND cnes IS NULL AND ine IS NULL)
        OR (scope_kind = 'MUNICIPALITY' AND municipality_ibge IS NOT NULL AND length(municipality_ibge) = 7)
    )
);

CREATE INDEX idx_user_grants_user ON user_grants (user_id) WHERE revoked_at IS NULL;

-- -------------------------------------------------------------------------------------------
-- sessions: session_id IS the hash of the opaque cookie value — the raw token is never
-- persisted, same discipline as activation_tokens below. Deliberately NOT backed by HttpSession:
-- inactivity (last_interactive_at) and absolute duration (absolute_expires_at) are independent
-- checks the servlet container's own timeout cannot express (§1.12.7: "Polling, SSE e heartbeat
-- não contam"), and a SQLite-backed session survives process restart the same way the job queue
-- does, instead of vanishing with in-memory HttpSession state.
-- -------------------------------------------------------------------------------------------

CREATE TABLE sessions (
    session_id                     TEXT PRIMARY KEY,
    user_id                        TEXT NOT NULL REFERENCES users (user_id),
    login_at                       TEXT NOT NULL,
    last_interactive_at            TEXT NOT NULL,
    absolute_expires_at            TEXT NOT NULL,
    authorization_version_at_login INTEGER NOT NULL,
    reauth_at                      TEXT,
    revoked_at                     TEXT,
    revoked_reason                 TEXT
);

CREATE INDEX idx_sessions_user ON sessions (user_id);

-- -------------------------------------------------------------------------------------------
-- activation_tokens: "ativação local de uso único, sem senha padrão" (L110, L536). Only the hash
-- is stored; a hash collision at insert is a correct rejection given >=128 bits of token entropy,
-- not a bug to retry around.
-- -------------------------------------------------------------------------------------------

CREATE TABLE activation_tokens (
    token_hash  TEXT PRIMARY KEY,
    user_id     TEXT NOT NULL REFERENCES users (user_id),
    expires_at  TEXT NOT NULL,
    consumed_at TEXT
);

-- -------------------------------------------------------------------------------------------
-- login_attempts: progressive delay baseline (5 failures / 15 min, ceiling 15 min, §1.12.7 L538)
-- plus a per-origin limit "sem bloquear indefinidamente todos os usuários atrás do mesmo NAT".
-- -------------------------------------------------------------------------------------------

CREATE TABLE login_attempts (
    attempt_id   TEXT PRIMARY KEY,
    username     TEXT NOT NULL,
    origin       TEXT NOT NULL,
    attempted_at TEXT NOT NULL,
    outcome      TEXT NOT NULL CHECK (outcome IN ('SUCCEEDED', 'FAILED', 'THROTTLED'))
);

CREATE INDEX idx_login_attempts_username_time ON login_attempts (username, attempted_at);
CREATE INDEX idx_login_attempts_origin_time ON login_attempts (origin, attempted_at);

-- -------------------------------------------------------------------------------------------
-- auth_audit: login/grant/reset/block trail. No secret material, no clinical data (§1.5 L167,
-- ENG-03) — detail_json is for actor/target/outcome context only.
-- -------------------------------------------------------------------------------------------

CREATE TABLE auth_audit (
    event_id      TEXT PRIMARY KEY,
    at            TEXT NOT NULL,
    actor_user_id TEXT REFERENCES users (user_id),
    event_type    TEXT NOT NULL,
    target        TEXT,
    outcome       TEXT NOT NULL,
    detail_json   TEXT NOT NULL DEFAULT '{}'
);

CREATE INDEX idx_auth_audit_time ON auth_audit (at);
