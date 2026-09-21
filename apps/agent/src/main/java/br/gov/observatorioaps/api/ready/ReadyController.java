package br.gov.observatorioaps.api.ready;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Readiness probe — no clinical data, {@code permitAll}, also doubles as the SPA's first request
 * to obtain the CSRF cookie before any state-changing call (plan decision 5: the spec is silent
 * on this route; it is a project decision, not a spec requirement).
 */
@RestController
public class ReadyController {

    @GetMapping("/api/v1/ready")
    public Map<String, String> ready() {
        return Map.of("status", "ready");
    }
}
