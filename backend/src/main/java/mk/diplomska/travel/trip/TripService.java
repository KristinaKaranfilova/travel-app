package mk.diplomska.travel.trip;

import mk.diplomska.travel.city.City;
import mk.diplomska.travel.city.CityRepository;
import mk.diplomska.travel.itinerary.ItineraryService;
import mk.diplomska.travel.itinerary.TripPlan;
import mk.diplomska.travel.landmark.LandmarkService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class TripService {

    private final ItineraryService itineraryService;
    private final TripRepository trips;
    private final CityRepository cities;
    private final JsonMapper json;
    private final LandmarkService landmarks;

    public TripService(ItineraryService itineraryService, TripRepository trips, CityRepository cities, JsonMapper json,
                       LandmarkService landmarks) {
        this.itineraryService = itineraryService;
        this.trips = trips;
        this.cities = cities;
        this.json = json;
        this.landmarks = landmarks;
    }

    /**
     * Без @Transactional: генерирањето може да чека на Overpass (до една минута)
     * и не треба цело време да држи врска до базата. save() е своја трансакција.
     */
    public TripResponse create(TripRequest request, Long userId) {
        validateDates(request);

        TripPlan plan = itineraryService.generate(request.cityId(), request.startDate(), request.endDate(),
                request.interests(), request.pace());

        Trip trip = new Trip(
                userId,
                request.cityId(),
                request.startDate(),
                request.endDate(),
                request.pace().name(),
                request.interests().stream().map(Enum::name).toArray(String[]::new),
                json.writeValueAsString(plan)); // план → JSON текст
        trips.save(trip);
        return new TripResponse(trip.getId(), plan);
    }

    @Transactional(readOnly = true)
    public TripResponse get(UUID id) {
        Trip trip = trips.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Патувањето не постои"));
        TripPlan plan = json.readValue(trip.getPlan(), TripPlan.class); // JSON текст → план
        return new TripResponse(trip.getId(), plan);
    }

    /** Патувањата на корисникот (без целиот план, само основни податоци). */
    @Transactional(readOnly = true)
    public List<TripSummary> listForUser(Long userId) {
        List<Trip> userTrips = trips.findByUserIdOrderByCreatedAtDesc(userId);
        Map<Long, City> cityById = cities.findAllById(userTrips.stream().map(Trip::getCityId).distinct().toList())
                .stream().collect(Collectors.toMap(City::getId, Function.identity()));
        return userTrips.stream().map(t -> {
            City c = cityById.get(t.getCityId());
            // Насловна слика само од кешот на знаменитости (без надворешни повици)
            String image = c == null ? null : landmarks.cachedCoverImage(c.getLat(), c.getLon());
            return new TripSummary(t.getId(), t.getCityId(), c == null ? "" : c.getName(),
                    c == null ? "" : c.getCountry(), t.getStartDate(), t.getEndDate(), t.getPace(), t.getCreatedAt(),
                    image);
        }).toList();
    }

    /** Само сопственикот може да го избрише патувањето. */
    @Transactional
    public void delete(UUID id, Long userId) {
        Trip trip = trips.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Патувањето не постои"));
        if (!userId.equals(trip.getUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Ова патување не е твое");
        }
        trips.delete(trip);
    }

    private static void validateDates(TripRequest request) {
        if (request.endDate().isBefore(request.startDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Крајниот датум е пред почетниот");
        }
        long days = ChronoUnit.DAYS.between(request.startDate(), request.endDate()) + 1;
        if (days > ItineraryService.MAX_DAYS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Најмногу " + ItineraryService.MAX_DAYS + " дена");
        }
    }
}
