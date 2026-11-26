package mk.diplomska.travel.landmark;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/**
 * Опис на место со кеш во базата.
 * Пронајден опис се чува 30 дена, а „нема опис“ само 1 ден (можеби подоцна ќе го има).
 * Привремена грешка на надворешен сервис не се кешира.
 */
@Service
public class PlaceDetailsService {

    private static final Logger log = LoggerFactory.getLogger(PlaceDetailsService.class);

    private final PlaceDetailsClient client;
    private final JdbcTemplate jdbc;
    private final JsonMapper json;

    public PlaceDetailsService(PlaceDetailsClient client, JdbcTemplate jdbc, JsonMapper json) {
        this.client = client;
        this.jdbc = jdbc;
        this.json = json;
    }

    public PlaceDetails get(String name, double lat, double lon, String wikidataId) {
        String key = wikidataId != null
                ? wikidataId
                : String.format(Locale.ROOT, "%s|%.4f|%.4f", name.strip().toLowerCase(), lat, lon);

        List<String> cached = jdbc.queryForList("""
                SELECT details::text FROM place_details_cache
                WHERE cache_key = ?
                  AND fetched_at > now() - (CASE WHEN found THEN INTERVAL '30 days' ELSE INTERVAL '1 day' END)
                """, String.class, key);
        if (!cached.isEmpty()) {
            return json.readValue(cached.get(0), PlaceDetails.class);
        }

        PlaceDetails details;
        try {
            details = client.find(name, lat, lon, wikidataId);
        } catch (RuntimeException e) {
            // Привремена грешка не значи „нема опис“: не се кешира и корисникот може да проба повторно
            log.warn("Place details lookup failed: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Описот моментално не може да се вчита", e);
        }

        jdbc.update("""
                INSERT INTO place_details_cache (cache_key, details, found, fetched_at) VALUES (?, ?::jsonb, ?, ?)
                ON CONFLICT (cache_key) DO UPDATE
                SET details = EXCLUDED.details, found = EXCLUDED.found, fetched_at = EXCLUDED.fetched_at
                """, key, json.writeValueAsString(details), details.found(), Timestamp.valueOf(LocalDateTime.now()));
        return details;
    }
}
