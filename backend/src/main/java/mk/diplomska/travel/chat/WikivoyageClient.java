package mk.diplomska.travel.chat;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Преземање текст од Wikivoyage (отворен туристички водич, лиценца CC BY-SA)
 * преку MediaWiki API. Се зема целата страница како обичен текст,
 * со наслови на делови во формат "== Наслов ==".
 */
@Component
public class WikivoyageClient {

    public static final String BASE_URL = "https://en.wikivoyage.org";

    /** Пократок текст од ова не е вистински водич (на пр. страница за појаснување). */
    static final int MIN_GUIDE_CHARS = 1500;

    private final RestClient http;
    private final RestClient wikidata;

    public WikivoyageClient(RestClient.Builder builder) {
        SimpleClientHttpRequestFactory timeouts = new SimpleClientHttpRequestFactory();
        timeouts.setConnectTimeout(Duration.ofSeconds(5));
        timeouts.setReadTimeout(Duration.ofSeconds(20));
        this.http = builder.clone()
                .requestFactory(timeouts)
                .baseUrl(BASE_URL)
                .defaultHeader("User-Agent", "travel-planner-thesis/0.1")
                .build();
        this.wikidata = builder.clone()
                .requestFactory(timeouts)
                .baseUrl("https://www.wikidata.org")
                .defaultHeader("User-Agent", "travel-planner-thesis/0.1")
                .build();
    }

    /** Враќа {наслов, текст} на страницата, на пр. за "Ohrid". */
    public Page fetchPage(String title) {
        Response response = http.get()
                .uri(uri -> uri.path("/w/api.php")
                        .queryParam("action", "query")
                        .queryParam("prop", "extracts")
                        .queryParam("explaintext", 1)        // обичен текст, без HTML
                        .queryParam("exsectionformat", "wiki") // наслови како "== Наслов =="
                        .queryParam("redirects", 1)
                        .queryParam("format", "json")
                        .queryParam("formatversion", 2)
                        .queryParam("titles", title)
                        .build())
                .retrieve()
                .body(Response.class);

        if (response == null || response.query() == null || response.query().pages() == null
                || response.query().pages().isEmpty() || response.query().pages().get(0).extract() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Нема Wikivoyage страница: " + title);
        }
        return response.query().pages().get(0);
    }

    /**
     * Наслов на англиската Wikivoyage страница за градот.
     *  1. Преку Wikidata (сигурно: истиот објект, на пр. Q597 → "Lisbon").
     *  2. Ако нема Wikidata врска: по англиското име, ако таква страница постои и е доволно долга.
     */
    public Optional<String> findTitle(String wikidataId, String englishName) {
        if (wikidataId != null && wikidataId.matches("Q\\d+")) {
            JsonNode response = wikidata.get()
                    .uri(uri -> uri.path("/w/api.php")
                            .queryParam("action", "wbgetentities")
                            .queryParam("ids", wikidataId)
                            .queryParam("props", "sitelinks")
                            .queryParam("sitefilter", "enwikivoyage")
                            .queryParam("format", "json")
                            .build())
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(JsonNode.class);
            if (response != null) {
                JsonNode title = response.path("entities").path(wikidataId)
                        .path("sitelinks").path("enwikivoyage").path("title");
                if (!title.isMissingNode() && !title.asString().isBlank()) {
                    return Optional.of(title.asString());
                }
            }
        }
        if (englishName != null && !englishName.isBlank()) {
            try {
                Page page = fetchPage(englishName);
                if (page.extract().length() >= MIN_GUIDE_CHARS) {
                    return Optional.of(page.title());
                }
            } catch (ResponseStatusException e) {
                // нема таква страница
            }
        }
        return Optional.empty();
    }

    public static String pageUrl(String title) {
        return BASE_URL + "/wiki/" + title.replace(' ', '_');
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Response(Query query) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Query(List<Page> pages) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Page(String title, String extract) {
    }
}
