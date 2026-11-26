package mk.diplomska.travel.city;

import jakarta.validation.Valid;
import mk.diplomska.travel.chat.GuideService;
import mk.diplomska.travel.place.PlaceService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/cities")
public class CityController {

    private final CityService cityService;
    private final PlaceService placeService;
    private final GuideService guideService;

    public CityController(CityService cityService, PlaceService placeService, GuideService guideService) {
        this.cityService = cityService;
        this.placeService = placeService;
        this.guideService = guideService;
    }

    /** GET /api/cities/search?q=ohrid */
    @GetMapping("/search")
    public List<CitySuggestion> search(@RequestParam("q") String query) {
        return cityService.search(query);
    }

    /**
     * POST /api/cities: зачувува избран град (ако веќе не постои) и го враќа со id.
     * Местата за градот и туристичкиот водич за асистентот веднаш почнуваат да се
     * подготвуваат во позадина, за да се готови додека корисникот ја пополнува формата.
     */
    @PostMapping
    public CityDto select(@Valid @RequestBody CitySuggestion suggestion) {
        City city = cityService.findOrCreate(suggestion);
        placeService.prefetch(city.getId());
        guideService.prefetch(city.getId());
        return CityDto.from(city);
    }
}
