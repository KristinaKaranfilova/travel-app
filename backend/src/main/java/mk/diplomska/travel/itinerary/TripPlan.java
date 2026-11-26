package mk.diplomska.travel.itinerary;

import mk.diplomska.travel.place.Interest;

import java.time.LocalDate;
import java.util.List;

/** Целиот генериран план. Се чува како JSON во колоната trip.plan. */
public record TripPlan(
        Long cityId,
        String cityName,
        double cityLat,
        double cityLon,
        LocalDate startDate,
        LocalDate endDate,
        Pace pace,
        List<Interest> interests,
        List<DayPlan> days
) {
}
