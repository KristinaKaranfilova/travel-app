package mk.diplomska.travel.landmark;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class LandmarkController {

    private final LandmarkService landmarkService;
    private final DestinationService destinationService;
    private final PlaceDetailsService placeDetailsService;

    public LandmarkController(LandmarkService landmarkService, DestinationService destinationService,
                              PlaceDetailsService placeDetailsService) {
        this.landmarkService = landmarkService;
        this.destinationService = destinationService;
        this.placeDetailsService = placeDetailsService;
    }

    /** GET /api/landmarks?lat=41.11&lon=20.80 → најпознатите знаменитости со слики */
    @GetMapping("/api/landmarks")
    public List<Landmark> landmarks(@RequestParam double lat, @RequestParam double lon) {
        return landmarkService.getLandmarks(lat, lon);
    }

    /** GET /api/destinations/featured → популарни дестинации за почетната страна */
    @GetMapping("/api/destinations/featured")
    public List<DestinationService.Destination> featured() {
        return destinationService.featured();
    }

    /** GET /api/places/details?name=Плаошник&lat=41.11&lon=20.79 → краток опис на местото */
    @GetMapping("/api/places/details")
    public PlaceDetails details(
            @RequestParam @NotBlank @Size(max = 200) String name,
            @RequestParam @DecimalMin("-90") @DecimalMax("90") double lat,
            @RequestParam @DecimalMin("-180") @DecimalMax("180") double lon,
            @RequestParam(required = false) @Pattern(regexp = "Q\\d{1,12}") String wikidataId) {
        return placeDetailsService.get(name, lat, lon, wikidataId);
    }
}
