package mk.diplomska.travel.city;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Резултат од пребарување град. Сè уште не е зачуван во базата:
 * се зачувува дури кога корисникот ќе го избере (POST /api/cities).
 */
public record CitySuggestion(
        @NotBlank String name,
        String country,
        String displayName,
        @NotBlank String osmType,
        @NotNull Long osmId,
        double lat,
        double lon,
        double south,
        double west,
        double north,
        double east
) {
}
