package mk.diplomska.travel.city;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Пребарување градови преку Nominatim (геокодер на OpenStreetMap).
 *
 * Правила за користење на бесплатниот сервис:
 *  - најмногу 1 барање во секунда (затоа нема autocomplete, туку копче „Барај“)
 *  - задолжителен User-Agent што ја идентификува апликацијата
 */
@Component
public class NominatimClient {

    private static final String BASE_URL = "https://nominatim.openstreetmap.org";
    private static final long MIN_MILLIS_BETWEEN_CALLS = 1000;

    private final RestClient http;
    private long lastCallMillis = 0;

    public NominatimClient(RestClient.Builder builder) {
        SimpleClientHttpRequestFactory timeouts = new SimpleClientHttpRequestFactory();
        timeouts.setConnectTimeout(Duration.ofSeconds(5));
        timeouts.setReadTimeout(Duration.ofSeconds(10));
        this.http = builder
                .requestFactory(timeouts)
                .baseUrl(BASE_URL)
                .defaultHeader("User-Agent", "travel-planner-thesis/0.1")
                .build();
    }

    /** Враќа до 5 населени места (градови, гратчиња) што одговараат на текстот. */
    public List<NominatimResult> search(String query) {
        waitForRateLimit();
        List<NominatimResult> results = http.get()
                .uri(uri -> uri.path("/search")
                        .queryParam("q", query)
                        .queryParam("format", "jsonv2")
                        .queryParam("addressdetails", 1)
                        .queryParam("featureType", "settlement")
                        .queryParam("accept-language", "mk,en")
                        .queryParam("limit", 5)
                        .build())
                .retrieve()
                .body(new ParameterizedTypeReference<List<NominatimResult>>() {});
        return results == null ? List.of() : results;
    }

    /** Ако претходното барање беше пред помалку од 1 секунда, почекај. */
    private synchronized void waitForRateLimit() {
        long wait = lastCallMillis + MIN_MILLIS_BETWEEN_CALLS - System.currentTimeMillis();
        if (wait > 0) {
            try {
                Thread.sleep(wait);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        lastCallMillis = System.currentTimeMillis();
    }

    /**
     * Дел од одговорот на Nominatim што ни треба.
     * boundingbox е во редослед: [south, north, west, east], како текст.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record NominatimResult(
            @JsonProperty("osm_type") String osmType,
            @JsonProperty("osm_id") Long osmId,
            String lat,
            String lon,
            String name,
            @JsonProperty("display_name") String displayName,
            List<String> boundingbox,
            Map<String, String> address
    ) {
    }
}
