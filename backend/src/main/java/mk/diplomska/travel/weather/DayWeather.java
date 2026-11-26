package mk.diplomska.travel.weather;

import java.time.LocalDate;

/**
 * Прогноза за еден ден.
 *
 * @param weatherCode              WMO код (0 = ведро, 61 = дожд, 71 = снег ...), frontend-от го претвора во икона
 * @param precipitationProbability веројатност за врнежи во проценти (0-100)
 */
public record DayWeather(
        LocalDate date,
        Integer weatherCode,
        Double tempMax,
        Double tempMin,
        Integer precipitationProbability
) {
    /** Од колку проценти веројатност за врнежи денот го сметаме за дождлив. */
    public static final int RAINY_THRESHOLD = 50;

    public int rainChance() {
        return precipitationProbability == null ? 0 : precipitationProbability;
    }

    public boolean isRainy() {
        return rainChance() >= RAINY_THRESHOLD;
    }
}
