package mk.diplomska.travel.place;

import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class PlaceController {

    private final PlaceService placeService;

    public PlaceController(PlaceService placeService) {
        this.placeService = placeService;
    }

    /**
     * GET /api/cities/{cityId}/places          → сите места
     * GET /api/cities/{cityId}/places?kind=CAFE → само кафулиња
     */
    @GetMapping("/api/cities/{cityId}/places")
    public List<PlaceDto> places(@PathVariable Long cityId,
                                 @RequestParam(required = false) PlaceKind kind) {
        List<Place> result = kind == null
                ? placeService.getPlaces(cityId)
                : placeService.getPlaces(cityId, kind);
        return result.stream().map(PlaceDto::from).toList();
    }

    /**
     * GET /api/cities/{cityId}/places/status → дали местата се подготвени (и колку ги има).
     * Ако не се, преземањето почнува во позадина; frontend-от повторно прашува на секои неколку секунди.
     */
    @GetMapping("/api/cities/{cityId}/places/status")
    public PlaceService.Status status(@PathVariable Long cityId) {
        return placeService.status(cityId);
    }
}
