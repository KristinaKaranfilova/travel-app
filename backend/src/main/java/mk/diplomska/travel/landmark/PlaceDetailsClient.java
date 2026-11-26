package mk.diplomska.travel.landmark;

import mk.diplomska.travel.itinerary.GeoUtils;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Наоѓа опис за место преку Wikidata и Wikipedia.
 *
 *  1. Ако го знаеме Wikidata id-то, го користиме директно.
 *     Инаку се бара по име, а се прифаќа само запис чии координати се на најмногу 1 km.
 *     Така не се прикажува опис за друго место со исто име (на пр. „Св. Софија“ во друг град).
 *  2. Од записот се зема линкот до статијата на Wikipedia (прво македонска, па англиска).
 *  3. Од статијата се зема воведот, скратен на неколку реченици.
 *  4. Ако нема статија, се користи краткиот опис од Wikidata.
 */
@Component
public class PlaceDetailsClient {

    static final double MAX_MATCH_DISTANCE_KM = 1.0;
    static final int MAX_DESCRIPTION_CHARS = 420;
    static final long MIN_MILLIS_BETWEEN_CALLS = 400;
    static final long DEFAULT_RETRY_SECONDS = 30;

    private final RestClient http;
    private long nextCallMillis = 0;
    private volatile long blockedUntilMillis = 0;

    public PlaceDetailsClient(RestClient.Builder builder) {
        SimpleClientHttpRequestFactory timeouts = new SimpleClientHttpRequestFactory();
        timeouts.setConnectTimeout(Duration.ofSeconds(5));
        timeouts.setReadTimeout(Duration.ofSeconds(10));
        this.http = builder
                .requestFactory(timeouts)
                .defaultHeader("User-Agent", "travel-planner-thesis/0.1")
                .build();
    }

    public PlaceDetails find(String name, double lat, double lon, String wikidataId) {
        JsonNode entity = wikidataId != null
                ? entities(List.of(wikidataId)).path(wikidataId)
                : closestEntity(name, lat, lon);
        if (entity == null || entity.isMissingNode()) {
            return PlaceDetails.notFound(name);
        }

        String image = commonsImage(entity);
        for (String lang : List.of("mk", "en")) {
            String title = text(entity.path("sitelinks").path(lang + "wiki").path("title"));
            if (title != null) {
                PlaceDetails fromWikipedia = wikipediaSummary(name, lang, title, image);
                if (fromWikipedia != null) {
                    return fromWikipedia;
                }
            }
        }

        // Нема статија: краткиот опис од Wikidata
        for (String lang : List.of("mk", "en")) {
            String description = text(entity.path("descriptions").path(lang).path("value"));
            if (description != null) {
                return new PlaceDetails(true, name, capitalize(description), lang, image, "Wikidata",
                        "https://www.wikidata.org/wiki/" + text(entity.path("id")));
            }
        }
        return PlaceDetails.notFound(name);
    }

    /** Меѓу записите со исто име, го избира најблискиот до дадените координати. */
    private JsonNode closestEntity(String name, double lat, double lon) {
        // Прво јазикот на името; вториот јазик се бара само ако првиот не даде резултат
        boolean cyrillic = name.chars().anyMatch(c -> Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CYRILLIC);
        for (String language : cyrillic ? List.of("mk", "en") : List.of("en", "mk")) {
            JsonNode result = getJson("https://www.wikidata.org/w/api.php?action=wbsearchentities&format=json&type=item"
                    + "&limit=7&language={language}&uselang={language}&search={name}", language, language, name);
            Set<String> ids = new LinkedHashSet<>();
            result.path("search").forEach(item -> ids.add(text(item.path("id"))));
            if (!ids.isEmpty()) {
                JsonNode best = closestTo(new ArrayList<>(ids), lat, lon);
                if (best != null) {
                    return best;
                }
            }
        }
        return null;
    }

    private JsonNode closestTo(List<String> ids, double lat, double lon) {
        JsonNode best = null;
        double bestDistance = MAX_MATCH_DISTANCE_KM;
        for (JsonNode entity : entities(ids)) {
            JsonNode coordinates = entity.path("claims").path("P625").path(0)
                    .path("mainsnak").path("datavalue").path("value");
            if (coordinates.isMissingNode()) {
                continue;
            }
            double distance = GeoUtils.distanceKm(lat, lon,
                    coordinates.path("latitude").asDouble(), coordinates.path("longitude").asDouble());
            if (distance <= bestDistance) {
                bestDistance = distance;
                best = entity;
            }
        }
        return best;
    }

    private JsonNode entities(List<String> ids) {
        return getJson("https://www.wikidata.org/w/api.php?action=wbgetentities&format=json"
                + "&props=claims|descriptions|sitelinks&languages=mk|en&ids={ids}", String.join("|", ids))
                .path("entities");
    }

    /**
     * Сите повици кон Wikimedia минуваат тука:
     *  - меѓу два повика има пауза, за да не се преоптоварува сервисот
     *  - ако серверот врати 429, не праќаме ништо додека не помине времето од Retry-After
     */
    private JsonNode getJson(String uri, Object... variables) {
        throttle();
        try {
            JsonNode result = http.get().uri(uri, variables).retrieve().body(JsonNode.class);
            if (result == null) {
                throw new RestClientException("Празен одговор од Wikimedia");
            }
            return result;
        } catch (HttpClientErrorException.TooManyRequests e) {
            String retryAfter = e.getResponseHeaders() == null ? null : e.getResponseHeaders().getFirst("Retry-After");
            blockedUntilMillis = System.currentTimeMillis() + retryAfterSeconds(retryAfter) * 1000;
            throw e;
        }
    }

    private synchronized void throttle() {
        long now = System.currentTimeMillis();
        if (now < blockedUntilMillis) {
            throw new RestClientException("Wikimedia привремено ги ограничува барањата");
        }
        long wait = nextCallMillis - now;
        if (wait > 0) {
            try {
                Thread.sleep(wait);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RestClientException("Барањето е прекинато");
            }
        }
        nextCallMillis = System.currentTimeMillis() + MIN_MILLIS_BETWEEN_CALLS;
    }

    static long retryAfterSeconds(String header) {
        try {
            return Math.max(1, Long.parseLong(header.trim()));
        } catch (RuntimeException e) {
            return DEFAULT_RETRY_SECONDS; // header-от недостасува или е во друг формат
        }
    }

    private PlaceDetails wikipediaSummary(String name, String lang, String title, String image) {
        try {
            JsonNode summary = getJson("https://" + lang + ".wikipedia.org/api/rest_v1/page/summary/{title}",
                    title.replace(' ', '_'));
            String extract = text(summary.path("extract"));
            if (extract == null) {
                return null;
            }
            String thumbnail = text(summary.path("thumbnail").path("source"));
            String url = text(summary.path("content_urls").path("desktop").path("page"));
            // Сликата од Wikipedia е директен линк, па се вчитува побрзо од Commons пренасочувањето
            return new PlaceDetails(true, name, shorten(extract), lang, thumbnail != null ? thumbnail : image,
                    "Wikipedia", url);
        } catch (HttpClientErrorException.NotFound e) {
            return null; // нема таква статија: се пробува следниот јазик
        }
    }

    private static String commonsImage(JsonNode entity) {
        String file = text(entity.path("claims").path("P18").path(0).path("mainsnak").path("datavalue").path("value"));
        if (file == null) {
            return null;
        }
        String encoded = URLEncoder.encode(file.replace(' ', '_'), StandardCharsets.UTF_8).replace("+", "%20");
        return "https://commons.wikimedia.org/wiki/Special:FilePath/" + encoded + "?width=640";
    }

    /** Го скратува текстот на крај од реченица, ако е можно. */
    static String shorten(String text) {
        String clean = text.strip().replaceAll("\\s+", " ");
        if (clean.length() <= MAX_DESCRIPTION_CHARS) {
            return clean;
        }
        String cut = clean.substring(0, MAX_DESCRIPTION_CHARS);
        int sentenceEnd = cut.lastIndexOf(". ");
        if (sentenceEnd >= 120) {
            return cut.substring(0, sentenceEnd + 1);
        }
        int wordEnd = cut.lastIndexOf(' ');
        return (wordEnd > 0 ? cut.substring(0, wordEnd) : cut) + "…";
    }

    private static String capitalize(String text) {
        return text.substring(0, 1).toUpperCase() + text.substring(1);
    }

    private static String text(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String value = node.asString();
        return value == null || value.isBlank() ? null : value;
    }
}
