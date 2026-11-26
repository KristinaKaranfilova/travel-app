package mk.diplomska.travel.chat;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Преземање текст од Wikivoyage (отворен туристички водич, лиценца CC BY-SA)
 * преку MediaWiki API. Се зема целата страница како обичен текст,
 * со наслови на делови во формат "== Наслов ==".
 */
@Component
public class WikivoyageClient {

    public static final String BASE_URL = "https://en.wikivoyage.org";

    private final RestClient http;

    public WikivoyageClient(RestClient.Builder builder) {
        this.http = builder
                .baseUrl(BASE_URL)
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
        Page page = response.query().pages().get(0);
        return page;
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
