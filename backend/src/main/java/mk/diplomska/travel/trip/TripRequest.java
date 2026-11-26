package mk.diplomska.travel.trip;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import mk.diplomska.travel.itinerary.Pace;
import mk.diplomska.travel.place.Interest;

import java.time.LocalDate;
import java.util.Set;

/** Тело на POST /api/trips. */
public record TripRequest(
        @NotNull Long cityId,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        @NotEmpty Set<Interest> interests,
        @NotNull Pace pace
) {
}
