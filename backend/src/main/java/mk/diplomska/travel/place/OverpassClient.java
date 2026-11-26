package mk.diplomska.travel.place;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Преземање места од OpenStreetMap преку Overpass API (бесплатно, без клуч).
 *
 * Со едно барање се земаат сите атракции, ресторани и кафулиња
 * во круг со даден радиус околу центарот на градот.
 */
@Component
public class OverpassClient {

    /**
     * Јавните Overpass сервери знаат да бидат преоптоварени (грешка 502/504).
     * Затоа ги пробуваме по ред: ако првиот не успее, се оди на следниот.
     */
    private static final List<String> SERVERS = List.of(
            "https://overpass-api.de/api/interpreter",
            "https://overpass.private.coffee/api/interpreter"
    );

    private static final Logger log = LoggerFactory.getLogger(OverpassClient.class);

    private final RestClient http;

    public OverpassClient(RestClient.Builder builder) {
        SimpleClientHttpRequestFactory timeouts = new SimpleClientHttpRequestFactory();
        timeouts.setConnectTimeout(Duration.ofSeconds(10));
        timeouts.setReadTimeout(Duration.ofSeconds(100));
        this.http = builder
                .requestFactory(timeouts)
                .defaultHeader("User-Agent", "travel-planner-thesis/0.1")
                .build();
    }

    public List<OverpassElement> fetchPlaces(double lat, double lon, int radiusMeters) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("data", buildQuery(lat, lon, radiusMeters));

        RuntimeException lastError = null;
        for (String server : SERVERS) {
            try {
                OverpassResponse response = http.post()
                        .uri(server)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .body(form)
                        .retrieve()
                        .body(OverpassResponse.class);
                return response == null || response.elements() == null ? List.of() : response.elements();
            } catch (RestClientException e) {
                log.warn("Overpass server {} failed: {}", server, e.getMessage());
                lastError = e;
            }
        }
        // 503 = сервисот привремено не е достапен (наместо општа грешка 500)
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "OpenStreetMap (Overpass) серверите се преоптоварени, обидете се повторно за неколку минути",
                lastError);
    }

    /**
     * Overpass QL барање. "nwr" значи node, way или relation.
     * ["name"] = само објекти со име.
     * ["wikidata"] кај парковите и црквите = само познатите,
     * бидејќи во голем град ги има илјадници.
     * "out center" враќа центар и за објекти што се површини (way/relation).
     */
    static String buildQuery(double lat, double lon, int radiusMeters) {
        // Locale.ROOT: децималите секогаш со точка (42.11), без оглед на јазикот на системот
        String around = String.format(Locale.ROOT, "(around:%d,%.6f,%.6f)", radiusMeters, lat, lon);
        return """
                [out:json][timeout:90];
                (
                  nwr["tourism"~"^(museum|gallery|viewpoint|attraction)$"]["name"]%1$s;
                  nwr["historic"~"^(castle|fort|monument|ruins|archaeological_site|city_gate)$"]["name"]%1$s;
                  nwr["leisure"="park"]["name"]["wikidata"]%1$s;
                  nwr["amenity"="place_of_worship"]["name"]["wikidata"]%1$s;
                  nwr["amenity"~"^(restaurant|cafe)$"]["name"]%1$s;
                );
                out center tags;
                """.formatted(around);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OverpassResponse(List<OverpassElement> elements) {
    }

    /** Еден OSM објект. Точка (node) има lat/lon; површина (way/relation) има center. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OverpassElement(String type, long id, Double lat, Double lon,
                                  Center center, Map<String, String> tags) {

        public Double latitude() {
            return lat != null ? lat : (center != null ? center.lat() : null);
        }

        public Double longitude() {
            return lon != null ? lon : (center != null ? center.lon() : null);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Center(double lat, double lon) {
    }
}
