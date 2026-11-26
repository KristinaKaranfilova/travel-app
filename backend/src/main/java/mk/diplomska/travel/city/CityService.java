package mk.diplomska.travel.city;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class CityService {

    private final NominatimClient nominatim;
    private final CityRepository cities;

    public CityService(NominatimClient nominatim, CityRepository cities) {
        this.nominatim = nominatim;
        this.cities = cities;
    }

    /** Пребарување преку Nominatim. Резултатите НЕ се зачувуваат. */
    public List<CitySuggestion> search(String query) {
        return nominatim.search(query).stream()
                .map(CityService::toSuggestion)
                .toList();
    }

    /** Ако градот веќе е во базата, го враќа; инаку го зачувува. */
    @Transactional
    public City findOrCreate(CitySuggestion s) {
        return cities.findByOsmTypeAndOsmId(s.osmType(), s.osmId())
                .orElseGet(() -> cities.save(new City(
                        s.name(), s.country(), s.osmType(), s.osmId(),
                        s.lat(), s.lon(), s.south(), s.west(), s.north(), s.east())));
    }

    public City getById(Long id) {
        return cities.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Градот не постои: " + id));
    }

    private static CitySuggestion toSuggestion(NominatimClient.NominatimResult r) {
        List<String> bbox = r.boundingbox(); // [south, north, west, east]
        String country = r.address() == null ? null : r.address().get("country");
        return new CitySuggestion(
                r.name(),
                country,
                r.displayName(),
                r.osmType(),
                r.osmId(),
                Double.parseDouble(r.lat()),
                Double.parseDouble(r.lon()),
                Double.parseDouble(bbox.get(0)),
                Double.parseDouble(bbox.get(2)),
                Double.parseDouble(bbox.get(1)),
                Double.parseDouble(bbox.get(3)));
    }
}
