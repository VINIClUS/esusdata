package br.gov.observatorioaps.api;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A dedicated scheduler for {@code RunEventsController}'s per-connection polling — kept separate
 * from {@code JobWorker}'s own thread (jobrunner has no Spring Web dependency, and never should,
 * per {@code ModuleBoundaryTest}) and shut down gracefully with the application context.
 */
@Configuration
class SseConfig {

    @Bean(destroyMethod = "shutdown")
    ScheduledExecutorService sseScheduler() {
        AtomicInteger counter = new AtomicInteger();
        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable, "sse-poll-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        return Executors.newScheduledThreadPool(4, threadFactory);
    }
}
