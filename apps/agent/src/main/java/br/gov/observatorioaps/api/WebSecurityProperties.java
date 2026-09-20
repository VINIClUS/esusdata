package br.gov.observatorioaps.api;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

/**
 * §1.12.6 L519 / ENG-49: "loopback não é exceção para autenticação/autorização" — and that
 * extends to Origin/Host validation. Defaults cover the loopback bind this app ships with
 * (§1.4 L110); a deployment binding elsewhere must configure its real origin/host explicitly.
 */
@ConfigurationProperties(prefix = "observatorio.web")
public record WebSecurityProperties(
        @DefaultValue({"http://127.0.0.1:8080", "http://localhost:8080"}) List<String> allowedOrigins,
        @DefaultValue({"127.0.0.1:8080", "localhost:8080"}) List<String> allowedHosts
) {
}
