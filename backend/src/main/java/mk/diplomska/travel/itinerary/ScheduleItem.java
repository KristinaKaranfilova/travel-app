package mk.diplomska.travel.itinerary;

import mk.diplomska.travel.place.Place;
import mk.diplomska.travel.place.PlaceCategory;

import java.time.LocalTime;

/**
 * Една ставка во дневниот распоред.
 *
 * @param walkMinutes колку минути пешачење од претходната ставка
 * @param start       почеток (null за EXTRA)
 * @param end         крај (null за EXTRA)
 */
public record ScheduleItem(
        ItemType type,
        Long placeId,
        String name,
        PlaceCategory category,
        double lat,
        double lon,
        boolean indoor,
        String openingHours,
        LocalTime start,
        LocalTime end,
        int walkMinutes
) {
    public static ScheduleItem of(ItemType type, Place p, LocalTime start, LocalTime end, int walkMinutes) {
        return new ScheduleItem(type, p.getId(), p.getName(), p.getCategory(), p.getLat(), p.getLon(),
                p.isIndoor(), p.getOpeningHours(), start, end, walkMinutes);
    }

    public static ScheduleItem extra(Place p) {
        return of(ItemType.EXTRA, p, null, null, 0);
    }
}
