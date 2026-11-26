package mk.diplomska.travel.trip;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/** Кратко за патувањето, за листата „Мои патувања“. */
public record TripSummary(
        UUID id,
        Long cityId,
        String cityName,
        String country,
        LocalDate startDate,
        LocalDate endDate,
        String pace,
        LocalDateTime createdAt,
        String imageUrl          // насловна слика (може да е null)
) {
}
