package br.gov.observatorioaps.api;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Dedicated schedulers for {@code RunEventsController}'s per-connection work — kept separate from
 * {@code JobWorker}'s own thread (jobrunner has no Spring Web dependency, and never should, per
 * {@code ModuleBoundaryTest}) and shut down gracefully with the application context.
 */
@Configuration
class SseConfig {

    @Bean(destroyMethod = "shutdown")
    ScheduledExecutorService sseScheduler() {
        return Executors.newScheduledThreadPool(4, threadFactory("sse-poll-"));
    }

    /**
     * One slot per allowed connection keeps reauthorization from queuing behind progress polls or
     * behind another slow reauthorization check. The pool remains bounded by the global stream cap.
     */
    @Bean(name = "sseReauthScheduler", destroyMethod = "shutdown")
    ScheduledExecutorService sseReauthScheduler() {
        return Executors.newScheduledThreadPool(50, threadFactory("sse-reauth-"));
    }

    private ThreadFactory threadFactory(String prefix) {
        AtomicInteger counter = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }
}
