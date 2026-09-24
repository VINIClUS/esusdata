package esusdata.run.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import org.junit.jupiter.api.Test;

class SseConfigTest {

    @Test
    void schedulersRemoveCancelledTasksFromTheirDelayQueues() {
        SseConfig config = new SseConfig();
        // shut down with shutdownNow() in finally
        @SuppressWarnings("PMD.CloseResource")
        ScheduledExecutorService pollScheduler = config.sseScheduler();
        // shut down with shutdownNow() in finally
        @SuppressWarnings("PMD.CloseResource")
        ScheduledExecutorService reauthScheduler = config.sseReauthScheduler();
        try {
            assertThat(((ScheduledThreadPoolExecutor) pollScheduler).getRemoveOnCancelPolicy())
                    .isTrue();
            assertThat(((ScheduledThreadPoolExecutor) reauthScheduler).getRemoveOnCancelPolicy())
                    .isTrue();
        } finally {
            pollScheduler.shutdownNow();
            reauthScheduler.shutdownNow();
        }
    }
}
