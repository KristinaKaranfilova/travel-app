package mk.diplomska.travel.chat;

import jakarta.annotation.PreDestroy;
import mk.diplomska.travel.city.City;
import mk.diplomska.travel.city.CityService;
import mk.diplomska.travel.city.NominatimClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Туристички водич (Wikivoyage) за чет-ботот, за КОЈ БИЛО град.
 *
 * Порано изворите се внесуваа рачно (admin endpoint), само за 5 градови, па за сите други
 * градови асистентот секогаш одговараше „немам информација“. Сега, првиот пат кога ќе се
 * избере град без водич, тој автоматски се внесува во позадина:
 *
 *  1. Од Nominatim: Wikidata идентификатор и англиско име на градот.
 *  2. Од Wikidata: наслов на англиската Wikivoyage страница (на пр. Q597 → "Lisbon").
 *  3. {@link IngestionService}: делење на парчиња, embeddings и зачувување во pgvector.
 *
 * Внесувањата одат едно по едно (заради лимитот на бесплатниот Gemini план).
 * Состојбата се прикажува во четот: READY, PREPARING (со напредок), UNAVAILABLE, FAILED.
 */
@Service
public class GuideService {

    private static final Logger log = LoggerFactory.getLogger(GuideService.class);

    /** Ако Wikivoyage нема страница за градот, не прашуваме повторно цел ден. */
    private static final Duration UNAVAILABLE_FOR = Duration.ofDays(1);
    /** По привремена грешка (на пр. лимит), дозволуваме нов обид по 2 минути. */
    private static final Duration FAILED_FOR = Duration.ofMinutes(2);

    /**
     * @param state  READY | PREPARING | UNAVAILABLE | FAILED | NONE
     * @param done   колку парчиња се внесени (додека трае PREPARING)
     * @param total  вкупно парчиња (0 додека не се знае)
     * @param title  наслов на Wikivoyage страницата, ако е познат
     */
    public record Status(String state, int done, int total, String title, String message) {
    }

    private static final class Progress {
        volatile int done;
        volatile int total;
        volatile String title;
    }

    private record Failure(String state, String message, Instant until) {
    }

    private final CityService cities;
    private final NominatimClient nominatim;
    private final WikivoyageClient wikivoyage;
    private final IngestionService ingestion;
    private final JdbcTemplate jdbc;

    private final ExecutorService queue = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "guide-ingestion");
        t.setDaemon(true);
        return t;
    });
    private final Map<Long, Progress> running = new ConcurrentHashMap<>();
    private final Map<Long, Failure> failures = new ConcurrentHashMap<>();

    public GuideService(CityService cities, NominatimClient nominatim, WikivoyageClient wikivoyage,
                        IngestionService ingestion, JdbcTemplate jdbc) {
        this.cities = cities;
        this.nominatim = nominatim;
        this.wikivoyage = wikivoyage;
        this.ingestion = ingestion;
        this.jdbc = jdbc;
    }

    /** Состојба на водичот; ако го нема (и не е неодамна неуспешен), внесувањето почнува. */
    public Status status(Long cityId) {
        Progress progress = running.get(cityId);
        if (progress != null) {
            return new Status("PREPARING", progress.done, progress.total, progress.title, null);
        }
        int chunks = countChunks(cityId);
        if (chunks > 0) {
            return new Status("READY", chunks, chunks, null, null);
        }
        Failure failure = failures.get(cityId);
        if (failure != null && Instant.now().isBefore(failure.until())) {
            return new Status(failure.state(), 0, 0, null, failure.message());
        }
        start(cityId);
        return new Status("PREPARING", 0, 0, null, null);
    }

    /** Почни го внесувањето (ако е потребно), без да се чека. */
    public void prefetch(Long cityId) {
        status(cityId);
    }

    private void start(Long cityId) {
        Progress progress = new Progress();
        if (running.putIfAbsent(cityId, progress) != null) {
            return; // веќе е во ред
        }
        failures.remove(cityId);
        queue.execute(() -> {
            try {
                City city = cities.getById(cityId);
                Optional<String> title = nominatim.details(city.getOsmType(), city.getOsmId())
                        .flatMap(d -> wikivoyage.findTitle(d.wikidataId(), d.englishName()));
                if (title.isEmpty()) {
                    log.info("No Wikivoyage guide for city id={} ({})", cityId, city.getName());
                    failures.put(cityId, new Failure("UNAVAILABLE",
                            "Wikivoyage нема туристички водич за " + city.getName() + ".",
                            Instant.now().plus(UNAVAILABLE_FOR)));
                    return;
                }
                progress.title = title.get();
                long started = System.currentTimeMillis();
                int chunks = ingestion.ingest(cityId, title.get(), (done, total) -> {
                    progress.total = total;
                    progress.done = done;
                });
                log.info("Guide ready for city id={} ('{}'): {} chunks in {} s", cityId, title.get(), chunks,
                        (System.currentTimeMillis() - started) / 1000);
            } catch (RuntimeException e) {
                log.warn("Guide ingestion failed for city id={}: {}", cityId, GeminiErrors.shortReason(e));
                failures.put(cityId, new Failure("FAILED",
                        "Водичот моментално не може да се подготви. " + GeminiErrors.describe(e).message(),
                        Instant.now().plus(FAILED_FOR)));
            } finally {
                running.remove(cityId);
            }
        });
    }

    private int countChunks(Long cityId) {
        Integer n = jdbc.queryForObject(
                "SELECT count(*) FROM vector_store WHERE metadata->>'cityId' = ?", Integer.class, String.valueOf(cityId));
        return n == null ? 0 : n;
    }

    @PreDestroy
    void shutdown() {
        queue.shutdownNow();
    }
}
