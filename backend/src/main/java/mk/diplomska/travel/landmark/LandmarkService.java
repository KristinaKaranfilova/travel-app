package mk.diplomska.travel.landmark;

import mk.diplomska.travel.common.BackgroundTasks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Знаменитости со слики за дадена локација.
 *
 *  1. Ако има свеж запис во кешот (табела landmark_cache) → од базата.
 *  2. Инаку Wikidata (најдобар квалитет) и Wikipedia (резерва) се прашуваат ПАРАЛЕЛНО.
 *     - Ако Wikidata одговори во рок од {@link #WIKIDATA_WAIT_SECONDS} секунди → нејзиниот резултат.
 *     - Инаку → резултатот од Wikipedia (кој дотогаш обично веќе стигнал).
 *  3. Wikidata продолжува во позадина и кога ќе одговори, го надградува кешот,
 *     па следниот корисник веднаш ги добива подобрите резултати.
 *
 * Кешот: Wikidata записите важат 30 дена, а Wikipedia (резервата) 1 ден.
 */
@Service
public class LandmarkService {

    private static final Logger log = LoggerFactory.getLogger(LandmarkService.class);

    static final int LIMIT = 8;
    static final int CACHE_DAYS = 30;
    static final int FALLBACK_CACHE_DAYS = 1;

    /** Колку најмногу го чекаме Wikidata пред да ја покажеме резервата. */
    static final int WIKIDATA_WAIT_SECONDS = 7;
    /** Вкупен рок за резервата (Wikipedia има и свој timeout од 10 s). */
    static final int FALLBACK_WAIT_SECONDS = 12;

    private static final String WIKIDATA = "wikidata";
    private static final String WIKIPEDIA = "wikipedia";

    private final WikidataClient wikidata;
    private final WikipediaClient wikipedia;
    private final JdbcTemplate jdbc;
    private final JsonMapper json;
    private final BackgroundTasks background;

    public LandmarkService(WikidataClient wikidata, WikipediaClient wikipedia, JdbcTemplate jdbc,
                           JsonMapper json, BackgroundTasks background) {
        this.wikidata = wikidata;
        this.wikipedia = wikipedia;
        this.jdbc = jdbc;
        this.json = json;
        this.background = background;
    }

    public List<Landmark> getLandmarks(double lat, double lon) {
        String key = key(lat, lon);

        List<Landmark> cached = fromCache(key);
        if (cached != null) {
            return cached;
        }

        // Двата извора паралелно. Wikidata сама се зачувува во кешот штом ќе стигне.
        CompletableFuture<List<Landmark>> fromWikidata = background.supply(() -> {
            List<Landmark> landmarks = wikidata.withDirectImages(wikidata.famousLandmarks(lat, lon, LIMIT));
            if (!landmarks.isEmpty()) {
                save(key, landmarks, WIKIDATA);
            }
            return landmarks;
        });
        CompletableFuture<List<Landmark>> fromWikipedia = background.supply(
                () -> wikipedia.nearbyLandmarks(lat, lon, LIMIT));

        try {
            List<Landmark> best = fromWikidata.get(WIKIDATA_WAIT_SECONDS, TimeUnit.SECONDS);
            if (!best.isEmpty()) {
                return best;
            }
        } catch (TimeoutException e) {
            log.info("Wikidata slow for {}, using Wikipedia meanwhile", key);
        } catch (Exception e) {
            log.warn("Wikidata failed for {}: {}", key, rootMessage(e));
        }

        try {
            List<Landmark> fallback = fromWikipedia.get(FALLBACK_WAIT_SECONDS, TimeUnit.SECONDS);
            if (!fallback.isEmpty() && !fromWikidataDone(fromWikidata)) {
                save(key, fallback, WIKIPEDIA);
            }
            return fallback;
        } catch (Exception e) {
            log.warn("Wikipedia fallback failed for {}: {}", key, rootMessage(e));
            return List.of();
        }
    }

    /** Насловната слика за локацијата, само ако веќе е во кешот (без надворешни повици). */
    public String cachedCoverImage(double lat, double lon) {
        List<String> rows = jdbc.queryForList(
                "SELECT landmarks->0->>'imageUrl' FROM landmark_cache WHERE location_key = ?", String.class, key(lat, lon));
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** Блиски координати (до ~1 km) делат ист запис во кешот. */
    private static String key(double lat, double lon) {
        return String.format(Locale.ROOT, "%.2f,%.2f", lat, lon);
    }

    /** null ако нема свеж запис. */
    private List<Landmark> fromCache(String key) {
        List<String> cached = jdbc.queryForList("""
                SELECT landmarks::text FROM landmark_cache
                WHERE location_key = ?
                  AND fetched_at > now() - (CASE WHEN source = 'wikidata' THEN INTERVAL '%d days' ELSE INTERVAL '%d days' END)
                """.formatted(CACHE_DAYS, FALLBACK_CACHE_DAYS), String.class, key);
        if (cached.isEmpty()) {
            return null;
        }
        List<Landmark> landmarks = json.readValue(cached.get(0), new TypeReference<List<Landmark>>() {});
        // Постарите записи уште немаат директни линкови до сликите
        if (landmarks.stream().anyMatch(l -> l.imageUrl().contains("Special:FilePath"))) {
            landmarks = wikidata.withDirectImages(landmarks);
            save(key, landmarks, WIKIDATA);
        }
        return landmarks;
    }

    private static boolean fromWikidataDone(CompletableFuture<List<Landmark>> f) {
        return f.isDone() && !f.isCompletedExceptionally() && !f.join().isEmpty();
    }

    /** Резервата (wikipedia) никогаш не го препишува подобриот запис од Wikidata. */
    private void save(String key, List<Landmark> landmarks, String source) {
        jdbc.update("""
                INSERT INTO landmark_cache (location_key, landmarks, fetched_at, source) VALUES (?, ?::jsonb, ?, ?)
                ON CONFLICT (location_key) DO UPDATE
                SET landmarks = EXCLUDED.landmarks, fetched_at = EXCLUDED.fetched_at, source = EXCLUDED.source
                WHERE EXCLUDED.source = 'wikidata' OR landmark_cache.source <> 'wikidata'
                """, key, json.writeValueAsString(landmarks), Timestamp.valueOf(LocalDateTime.now()), source);
    }

    private static String rootMessage(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null) {
            t = t.getCause();
        }
        return t.getMessage();
    }
}
