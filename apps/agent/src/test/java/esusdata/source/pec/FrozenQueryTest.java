package esusdata.source.pec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class FrozenQueryTest {

    @Test
    void aMissingQueryFailsInsteadOfRunningNothing() {
        assertThatThrownBy(() -> FrozenQuery.load("/compatibility/queries/missing@0.0.0.sql"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("missing@0.0.0.sql");
    }

    @Test
    void theChecksumIsTheSha256OfTheQueryText() {
        assertThat(FrozenQuery.checksum("SELECT 1"))
                .isEqualTo("sha256:e004ebd5b5532a4b85984a62f8ad48a81aa3460c1ca07701f386135d72cdecf5");
        assertThat(MunicipalIsolationContract.QUERY_CHECKSUM)
                .isEqualTo(FrozenQuery.checksum(MunicipalIsolationContract.QUERY));
    }
}
