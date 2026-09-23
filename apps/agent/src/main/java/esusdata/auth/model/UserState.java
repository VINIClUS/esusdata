package esusdata.auth.model;

/** {@code users.state} (§1.12.7: "sem conta/senha padrão; ativação local de uso único"). */
public enum UserState {
    PENDING_ACTIVATION,
    ACTIVE,
    BLOCKED
}
