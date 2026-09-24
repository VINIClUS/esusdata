package esusdata.run;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * ADR 0016: {@code binary} must name the packaged Rust execution plane — {@link RunConfig} refuses to
 * start without it. The empty default exists only so that refusal carries our message, not a
 * binding error.
 */
@ConfigurationProperties(prefix = "observatorio.execution-plane")
public record ExecPlaneProperties(
        @DefaultValue("") String binary,
        @DefaultValue("30s") Duration exitGrace) {}
