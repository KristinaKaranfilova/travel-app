package mk.diplomska.travel.trip;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/trips")
public class TripController {

    private final TripService tripService;

    public TripController(TripService tripService) {
        this.tripService = tripService;
    }

    /** POST /api/trips: генерира и зачувува нов план за најавениот корисник. */
    @PostMapping
    public TripResponse create(@Valid @RequestBody TripRequest request, @AuthenticationPrincipal Jwt jwt) {
        return tripService.create(request, userId(jwt));
    }

    /** GET /api/trips: патувањата на најавениот корисник. */
    @GetMapping
    public List<TripSummary> myTrips(@AuthenticationPrincipal Jwt jwt) {
        return tripService.listForUser(userId(jwt));
    }

    /** GET /api/trips/{id}: зачуван план (јавно, за линк за споделување). */
    @GetMapping("/{id}")
    public TripResponse get(@PathVariable UUID id) {
        return tripService.get(id);
    }

    /** DELETE /api/trips/{id}: бришење (само сопственикот). */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        tripService.delete(id, userId(jwt));
    }

    /** Во токенот, "sub" е id на корисникот. */
    private static Long userId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
