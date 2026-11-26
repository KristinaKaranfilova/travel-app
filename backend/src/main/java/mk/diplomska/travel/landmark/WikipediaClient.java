package mk.diplomska.travel.landmark;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.*;

/**
 * Wikipedia API, за две работи:
 *  1. Главна слика на статијата за град (за картичките „Популарни дестинации“).
 *  2. Резервен извор за знаменитости кога Wikidata не одговара:
 *     статии во близина (geosearch) со слика, филтрирани по опис
 *     (црква, музеј, тврдина...) и подредени по број на прегледи.
 */
@Component
public class WikipediaClient {

    /** Зборови во описот на статијата што означуваат знаменитост. */
    private static final List<String> LANDMARK_WORDS = List.of(
            "church", "cathedral", "basilica", "mosque", "monastery", "museum", "gallery", "castle",
            "fortress", "fort", "palace", "monument", "memorial", "archaeological", "ancient", "theatre",
            "amphitheatre", "bridge", "tower", "square", "fountain", "park", "temple", "landmark", "gate");

    private final RestClient http;

    public WikipediaClient(RestClient.Builder builder) {
        // Без рок, „закочена“ врска знаеше да чека и по 20+ секунди
        SimpleClientHttpRequestFactory timeouts = new SimpleClientHttpRequestFactory();
        timeouts.setConnectTimeout(Duration.ofSeconds(5));
        timeouts.setReadTimeout(Duration.ofSeconds(10));
        this.http = builder
                .requestFactory(timeouts)
                .baseUrl("https://en.wikipedia.org")
                .defaultHeader("User-Agent", "travel-planner-thesis/0.1")
                .build();
    }

    /** Наслов на англиска статија → URL на главната слика (ширина ~800px). */
    public Map<String, String> pageImages(Collection<String> titles) {
        Response r = http.get()
                .uri(uri -> uri.path("/w/api.php")
                        .queryParam("action", "query")
                        .queryParam("format", "json")
                        .queryParam("formatversion", 2)
                        .queryParam("redirects", 1)
                        .queryParam("prop", "pageimages")
                        .queryParam("piprop", "thumbnail")
                        .queryParam("pithumbsize", 800)
                        .queryParam("titles", String.join("|", titles))
                        .build())
                .retrieve()
                .body(Response.class);

        Map<String, String> result = new HashMap<>();
        if (r == null || r.query() == null || r.query().pages() == null) {
            return result;
        }
        // Wikipedia може да го пренасочи насловот (на пр. "Rome" остава ист, "Skopje" исто),
        // па враќаме по насловот што го враќа API-то, а и по оригиналниот ако има redirect
        Map<String, String> redirectedFrom = new HashMap<>();
        if (r.query().redirects() != null) {
            r.query().redirects().forEach(rd -> redirectedFrom.put(rd.to(), rd.from()));
        }
        for (Page p : r.query().pages()) {
            if (p.thumbnail() != null) {
                result.put(p.title(), p.thumbnail().source());
                if (redirectedFrom.containsKey(p.title())) {
                    result.put(redirectedFrom.get(p.title()), p.thumbnail().source());
                }
            }
        }
        return result;
    }

    /** Резервни знаменитости: статии во радиус од 10 km, со слика и опис што личи на знаменитост. */
    public List<Landmark> nearbyLandmarks(double lat, double lon, int limit) {
        Response r = http.get()
                .uri(uri -> uri.path("/w/api.php")
                        .queryParam("action", "query")
                        .queryParam("format", "json")
                        .queryParam("formatversion", 2)
                        .queryParam("generator", "geosearch")
                        .queryParam("ggscoord", lat + "|" + lon)
                        .queryParam("ggsradius", 10000)
                        .queryParam("ggslimit", 100)
                        .queryParam("prop", "pageimages|pageviews|description|coordinates|pageprops")
                        .queryParam("piprop", "thumbnail")
                        .queryParam("pithumbsize", 640)
                        .queryParam("colimit", 100)
                        .queryParam("ppprop", "wikibase_item")
                        .queryParam("pvipdays", 30)
                        .build())
                .retrieve()
                .body(Response.class);
        if (r == null || r.query() == null || r.query().pages() == null) {
            return List.of();
        }
        return r.query().pages().stream()
                .filter(p -> p.thumbnail() != null && p.description() != null
                        && p.coordinates() != null && !p.coordinates().isEmpty())
                .filter(p -> LANDMARK_WORDS.stream().anyMatch(w -> p.description().toLowerCase().contains(w)))
                .sorted(Comparator.comparingLong(Page::totalViews).reversed())
                .limit(limit)
                .map(p -> new Landmark(p.title(), p.description(), p.thumbnail().source(),
                        "https://en.wikipedia.org/wiki/" + p.title().replace(' ', '_'),
                        p.coordinates().get(0).lat(), p.coordinates().get(0).lon(),
                        p.pageprops() == null ? null : p.pageprops().get("wikibase_item")))
                .toList();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Response(Query query) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Query(List<Page> pages, List<Redirect> redirects) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Redirect(String from, String to) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Page(String title, String description, Thumbnail thumbnail, Map<String, Long> pageviews,
                List<Coordinate> coordinates, Map<String, String> pageprops) {
        long totalViews() {
            if (pageviews == null) {
                return 0;
            }
            return pageviews.values().stream().filter(Objects::nonNull).mapToLong(Long::longValue).sum();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Thumbnail(String source) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Coordinate(double lat, double lon) {
    }
}
