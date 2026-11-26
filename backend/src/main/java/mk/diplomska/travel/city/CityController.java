package mk.diplomska.travel.city;

import jakarta.validation.Valid;
import mk.diplomska.travel.place.PlaceService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/cities")
public class CityController {

    private final CityService cityService;
    private final PlaceService placeService;

    public CityController(CityService cityService, PlaceService placeService) {
        this.cityService = cityService;
        this.placeService = placeService;
    }

    /** GET /api/cities/search?q=ohrid */
    @GetMapping("/search")
    public List<CitySuggestion> search(@RequestParam("q") String query) {
        return cityService.search(query);
    }

    /**
     * POST /api/cities: зачувува избран град (ако веќе не постои) и го враќа со id.
     * Местата за градот веднаш почнуваат да се преземаат во позадина,
     * за да се подготвени додека корисникот ја пополнува формата.
     */
    @PostMapping
    public CityDto select(@Valid @RequestBody CitySuggestion suggestion) {
        City city = cityService.findOrCreate(suggestion);
        placeService.prefetch(city.getId());
        return CityDto.from(city);
    }
}
