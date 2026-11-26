package mk.diplomska.travel.landmark;

import mk.diplomska.travel.city.City;
import mk.diplomska.travel.city.CityRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Популарни дестинации за почетната страна (наместо корисникот веднаш да мора да пребарува).
 *
 * Листата е фиксна. За секоја дестинација:
 *  - cityId: ако градот веќе е во базата (може веднаш да се избере)
 *  - hasAssistant: дали чет-ботот има внесени извори за градот
 *  - imageUrl: главната слика од англиската Wikipedia статија за градот
 * Сликите се земаат со едно барање и се чуваат во меморија додека работи апликацијата.
 */
@Service
public class DestinationService {

    private static final Logger log = LoggerFactory.getLogger(DestinationService.class);

    /** name = како го враќа Nominatim на македонски; wikiTitle = англиска Wikipedia статија. */
    record Preset(String name, String country, String wikiTitle) {
    }

    static final List<Preset> PRESETS = List.of(
            new Preset("Охрид", "Северна Македонија", "Ohrid"),
            new Preset("Скопје", "Северна Македонија", "Skopje"),
            new Preset("Рим", "Италија", "Rome"),
            new Preset("Париз", "Франција", "Paris"),
            new Preset("Барселона", "Шпанија", "Barcelona"),
            new Preset("Истанбул", "Турција", "Istanbul"),
            new Preset("Атина", "Грција", "Athens"),
            new Preset("Солун", "Грција", "Thessaloniki"),
            new Preset("Прага", "Чешка", "Prague"),
            new Preset("Виена", "Австрија", "Vienna"),
            new Preset("Будимпешта", "Унгарија", "Budapest"),
            new Preset("Дубровник", "Хрватска", "Dubrovnik"));

    /** lat/lon се null ако градот уште не е во базата (тогаш frontend-от прво го пребарува). */
    public record Destination(String name, String country, Long cityId, Double lat, Double lon,
                              boolean hasAssistant, String imageUrl) {
    }

    private final CityRepository cities;
    private final WikipediaClient wikipedia;
    private final JdbcTemplate jdbc;

    private volatile Map<String, String> imageCache = null;

    public DestinationService(CityRepository cities, WikipediaClient wikipedia, JdbcTemplate jdbc) {
        this.cities = cities;
        this.wikipedia = wikipedia;
        this.jdbc = jdbc;
    }

    public List<Destination> featured() {
        Map<String, City> cityByName = new HashMap<>();
        for (City c : cities.findAll()) {
            cityByName.putIfAbsent(c.getName(), c);
        }
        // Градови со внесени Wikivoyage извори
        Set<Long> withAssistant = new HashSet<>(jdbc.queryForList(
                "SELECT DISTINCT (metadata->>'cityId')::bigint FROM vector_store", Long.class));

        Map<String, String> images = images();
        List<Destination> result = new ArrayList<>();
        for (Preset p : PRESETS) {
            City city = cityByName.get(p.name());
            Long cityId = city == null ? null : city.getId();
            result.add(new Destination(p.name(), p.country(), cityId,
                    city == null ? null : city.getLat(), city == null ? null : city.getLon(),
                    cityId != null && withAssistant.contains(cityId), images.get(p.wikiTitle())));
        }
        // Градовите со асистент први
        result.sort(Comparator.comparing((Destination d) -> !d.hasAssistant()));
        return result;
    }

    private Map<String, String> images() {
        if (imageCache == null) {
            try {
                imageCache = wikipedia.pageImages(PRESETS.stream().map(Preset::wikiTitle).toList());
            } catch (RuntimeException e) {
                log.warn("Could not load destination images: {}", e.getMessage());
                return Map.of(); // не кешираме празно, за следниот пат пак да се проба
            }
        }
        return imageCache;
    }
}
