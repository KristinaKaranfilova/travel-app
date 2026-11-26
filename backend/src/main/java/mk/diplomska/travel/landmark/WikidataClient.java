package mk.diplomska.travel.landmark;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

/**
 * Најпознатите знаменитости околу дадена точка, од Wikidata (SPARQL).
 *
 * Барањето ги наоѓа објектите во радиус од 5 km што:
 *  - се од тип знаменитост (музеј, црква, тврдина, споменик, палата, мост...)
 *  - имаат слика (својство P18)
 * и ги подредува по број на Wikipedia јазици на кои имаат статија (sitelinks).
 * Тоа е добра мерка за „колку е позната“: Колосеумот има статија на 150+ јазици.
 */
@Component
public class WikidataClient {

    private static final String URL = "https://query.wikidata.org/sparql";

    /**
     * Типови знаменитости (Wikidata Q-идентификатори):
     * туристичка атракција, музеј, уметнички музеј, црква, катедрала, базилика, џамија,
     * манастир, замок, утврдување, споменик, археолошки локалитет, палата, мост,
     * плоштад, кула, фонтана, парк.
     */
    private static final String TYPES = "wd:Q570116 wd:Q33506 wd:Q207694 wd:Q16970 wd:Q2977 wd:Q163687 "
            + "wd:Q32815 wd:Q44613 wd:Q23413 wd:Q57821 wd:Q4989906 wd:Q839954 wd:Q16560 wd:Q12280 "
            + "wd:Q174782 wd:Q12518 wd:Q483453 wd:Q22698";

    private final RestClient http;

    public WikidataClient(RestClient.Builder builder) {
        SimpleClientHttpRequestFactory timeouts = new SimpleClientHttpRequestFactory();
        timeouts.setConnectTimeout(Duration.ofSeconds(10));
        timeouts.setReadTimeout(Duration.ofSeconds(30));
        this.http = builder
                .requestFactory(timeouts)
                .defaultHeader("User-Agent", "travel-planner-thesis/0.1")
                .defaultHeader("Accept", "application/sparql-results+json")
                .build();
    }

    public List<Landmark> famousLandmarks(double lat, double lon, int limit) {
        // Locale.ROOT: децимали со точка; WKT формат е "Point(географска_должина географска_ширина)"
        String query = String.format(Locale.ROOT, """
                SELECT ?item ?itemLabel ?itemDescription ?image ?sitelinks ?location WHERE {
                  SERVICE wikibase:around {
                    ?item wdt:P625 ?location .
                    bd:serviceParam wikibase:center "Point(%f %f)"^^geo:wktLiteral .
                    bd:serviceParam wikibase:radius "5" .
                  }
                  ?item wdt:P31 ?type ; wdt:P18 ?image ; wikibase:sitelinks ?sitelinks .
                  VALUES ?type { %s }
                  SERVICE wikibase:label { bd:serviceParam wikibase:language "mk,en". }
                }
                ORDER BY DESC(?sitelinks)
                LIMIT 40
                """, lon, lat, TYPES);

        SparqlResponse response = http.get()
                .uri(URL + "?format=json&query={q}", query)
                .retrieve()
                .body(SparqlResponse.class);
        if (response == null || response.results() == null) {
            return List.of();
        }

        // Еден објект може да се појави повеќе пати (повеќе типови или слики): го земаме првиот
        Map<String, Landmark> byItem = new LinkedHashMap<>();
        for (Map<String, Value> row : response.results().bindings()) {
            String item = value(row, "item");
            String name = value(row, "itemLabel");
            if (item == null || name == null || name.matches("Q\\d+") || byItem.containsKey(item)) {
                continue; // нема име на македонски/англиски
            }
            double[] point = parsePoint(value(row, "location"));
            if (point == null) {
                continue;
            }
            String image = value(row, "image").replace("http://", "https://");
            String filePage = image.replace("Special:FilePath/", "File:");
            String wikidataId = item.substring(item.lastIndexOf('/') + 1);
            byItem.put(item, new Landmark(name, value(row, "itemDescription"), image + "?width=640", filePage,
                    point[0], point[1], wikidataId));
            if (byItem.size() == limit) {
                break;
            }
        }
        return List.copyOf(byItem.values());
    }

    /**
     * Wikidata дава линк „Special:FilePath“, кој минува низ неколку пренасочувања.
     * Со едно барање до Wikimedia Commons ги земаме директните линкови до умалените слики.
     * Ако барањето не успее, остануваат оригиналните линкови (и тие работат, само побавно).
     */
    public List<Landmark> withDirectImages(List<Landmark> landmarks) {
        List<String> titles = landmarks.stream().map(l -> fileTitle(l.imageUrl())).filter(Objects::nonNull).toList();
        if (titles.isEmpty()) {
            return landmarks;
        }
        try {
            JsonNode response = http.get()
                    .uri("https://commons.wikimedia.org/w/api.php?action=query&format=json&formatversion=2"
                            + "&prop=imageinfo&iiprop=url&iiurlwidth=640&titles={titles}", String.join("|", titles))
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(JsonNode.class);
            if (response == null) {
                return landmarks;
            }
            // API-то ги нормализира насловите (на пр. "_" во празно место)
            Map<String, String> normalized = new HashMap<>();
            response.path("query").path("normalized")
                    .forEach(n -> normalized.put(n.path("from").asString(), n.path("to").asString()));
            Map<String, String> thumbnails = new HashMap<>();
            response.path("query").path("pages").forEach(page -> {
                JsonNode thumbnail = page.path("imageinfo").path(0).path("thumburl");
                if (!thumbnail.isMissingNode()) {
                    thumbnails.put(page.path("title").asString(), thumbnail.asString());
                }
            });
            return landmarks.stream().map(l -> {
                String title = fileTitle(l.imageUrl());
                String direct = title == null ? null : thumbnails.get(normalized.getOrDefault(title, title));
                return direct == null ? l
                        : new Landmark(l.name(), l.description(), direct, l.pageUrl(), l.lat(), l.lon(), l.wikidataId());
            }).toList();
        } catch (RestClientException e) {
            return landmarks;
        }
    }

    /** ".../Special:FilePath/Ime%20slika.jpg?width=640" → "File:Ime slika.jpg" */
    static String fileTitle(String imageUrl) {
        String marker = "Special:FilePath/";
        int start = imageUrl == null ? -1 : imageUrl.indexOf(marker);
        if (start < 0) {
            return null;
        }
        String encoded = imageUrl.substring(start + marker.length());
        int query = encoded.indexOf('?');
        if (query >= 0) {
            encoded = encoded.substring(0, query);
        }
        // URLDecoder го претвора "+" во празно место, а во име на фајл "+" е вистински знак
        return "File:" + URLDecoder.decode(encoded.replace("+", "%2B"), StandardCharsets.UTF_8);
    }

    /** "Point(20.80 41.11)" (должина, ширина) → {41.11, 20.80} (ширина, должина). */
    static double[] parsePoint(String wkt) {
        if (wkt == null || !wkt.startsWith("Point(") || !wkt.endsWith(")")) {
            return null;
        }
        String[] parts = wkt.substring(6, wkt.length() - 1).trim().split("\\s+");
        if (parts.length != 2) {
            return null;
        }
        try {
            return new double[]{Double.parseDouble(parts[1]), Double.parseDouble(parts[0])};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String value(Map<String, Value> row, String key) {
        Value v = row.get(key);
        return v == null ? null : v.value();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SparqlResponse(Results results) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Results(List<Map<String, Value>> bindings) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Value(String value) {
    }
}
