package mk.diplomska.travel.itinerary;

import mk.diplomska.travel.weather.DayWeather;

import java.time.LocalDate;
import java.util.List;

/**
 * План за еден ден.
 *
 * @param weather   прогноза; null ако денот е предалеку во иднината
 * @param items     распоред по време (посети + оброци)
 * @param extras    места што не собраа во денот („ако ви остане време“)
 * @param walkingKm вкупно пешачење за денот (со фактор за улици)
 */
public record DayPlan(
        int dayNumber,
        LocalDate date,
        DayWeather weather,
        List<ScheduleItem> items,
        List<ScheduleItem> extras,
        double walkingKm
) {
}
