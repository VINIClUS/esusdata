package br.gov.observatorioaps.execution.adapter.in.http;

import org.junit.jupiter.api.Test;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import static org.assertj.core.api.Assertions.assertThat;

public class SseConfigTest {

    @Test
    void schedulersRemoveCancelledTasksFromTheirDelayQueues() {
        SseConfig config = new SseConfig();
        ScheduledExecutorService pollScheduler = config.sseScheduler();
        ScheduledExecutorService reauthScheduler = config.sseReauthScheduler();
        try {
            assertThat(((ScheduledThreadPoolExecutor) pollScheduler).getRemoveOnCancelPolicy()).isTrue();
            assertThat(((ScheduledThreadPoolExecutor) reauthScheduler).getRemoveOnCancelPolicy()).isTrue();
        } finally {
            pollScheduler.shutdownNow();
            reauthScheduler.shutdownNow();
        }
    }
}
