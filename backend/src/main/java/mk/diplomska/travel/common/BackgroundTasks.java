package mk.diplomska.travel.common;

import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Заеднички базен на нишки за бавните надворешни повици (Overpass, Wikidata, Wikipedia, Open-Meteo).
 *
 * Со него повеќе повици одат паралелно (наместо еден по еден), а некои работи
 * (на пр. преземањето места за град) почнуваат однапред, додека корисникот ја пополнува формата.
 */
@Component
public class BackgroundTasks {

    /**
     * 16 нишки: Wikidata што доцни продолжува во позадина (до 30 s) за да го надгради кешот,
     * па повеќе такви повици не смеат да ги блокираат другите задачи.
     */
    private final ExecutorService pool = Executors.newFixedThreadPool(16, new NamedThreads());

    public <T> CompletableFuture<T> supply(Supplier<T> task) {
        return CompletableFuture.supplyAsync(task, pool);
    }

    public CompletableFuture<Void> run(Runnable task) {
        return CompletableFuture.runAsync(task, pool);
    }

    @PreDestroy
    void shutdown() {
        pool.shutdownNow();
    }

    private static final class NamedThreads implements java.util.concurrent.ThreadFactory {
        private final AtomicInteger counter = new AtomicInteger();

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "background-" + counter.incrementAndGet());
            t.setDaemon(true);
            return t;
        }
    }
}
