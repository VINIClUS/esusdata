package esusdata.indicator;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** §1.10 L385: catalog, vigência, dependências e bloqueios. Any authenticated session may read it. */
@RestController
public class IndicatorPackController {

    @GetMapping("/api/v1/indicator-packs")
    public List<IndicatorPackResponse> packs() {
        return IndicatorPackCatalog.all().stream()
                .map(p -> new IndicatorPackResponse(
                        p.id(),
                        p.ruleVersion(),
                        p.family(),
                        p.unit(),
                        p.dependsOn(),
                        p.executionEnabled(),
                        p.blockedGates()))
                .toList();
    }
}
