package mk.diplomska.travel.itinerary;

import mk.diplomska.travel.place.Place;
import mk.diplomska.travel.weather.DayWeather;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Претвора подредена листа места во распоред по часови (симулација на денот).
 *
 *  - Денот почнува во 09:00.
 *  - За секое место: време = време + пешачење + времетраење на посетата.
 *  - По 12:30, пред следното место: ручек во најблискиот ресторан.
 *  - По 16:00 (ако е бил ручек): кафе во најблиското кафуле.
 *  - Место што би завршило по 19:00 оди во „ако ви остане време“.
 *  - На крај: вечера во најблискиот ресторан, не порано од 19:00.
 *
 * Ист ресторан/кафуле не се препорачува двапати во патувањето (usedFood).
 */
@Component
public class ScheduleBuilder {

    static final LocalTime DAY_START = LocalTime.of(9, 0);
    static final LocalTime LUNCH_AFTER = LocalTime.of(12, 30);
    static final LocalTime COFFEE_AFTER = LocalTime.of(16, 0);
    static final LocalTime VISITS_END = LocalTime.of(19, 0);
    static final LocalTime DINNER_NOT_BEFORE = LocalTime.of(19, 0);

    public DayPlan build(int dayNumber, LocalDate date, DayWeather weather, List<Place> route,
                         List<Place> restaurants, List<Place> cafes, Set<Place> usedFood) {
        List<ScheduleItem> items = new ArrayList<>();
        List<ScheduleItem> extras = new ArrayList<>();
        LocalTime time = DAY_START;
        Place current = null;
        boolean hadLunch = false;
        boolean hadCoffee = false;

        for (Place place : route) {
            // Пауза за оброк пред следната атракција, ако е време
            if (!hadLunch && !time.isBefore(LUNCH_AFTER)) {
                hadLunch = true;
                Place restaurant = nearestUnused(restaurants, usedFood, current);
                if (restaurant != null) {
                    time = addStop(items, ItemType.LUNCH, restaurant, current, time, null);
                    current = restaurant;
                }
            } else if (hadLunch && !hadCoffee && !time.isBefore(COFFEE_AFTER)) {
                hadCoffee = true;
                Place cafe = nearestUnused(cafes, usedFood, current);
                if (cafe != null) {
                    time = addStop(items, ItemType.COFFEE, cafe, current, time, null);
                    current = cafe;
                }
            }

            // Дали посетата собира пред крајот на денот?
            int walk = current == null ? 0 : GeoUtils.walkMinutes(current, place);
            LocalTime end = time.plusMinutes(walk + place.getVisitMinutes());
            if (end.isAfter(VISITS_END)) {
                extras.add(ScheduleItem.extra(place));
                continue;
            }
            time = addStop(items, ItemType.VISIT, place, current, time, null);
            current = place;
        }

        // Краток ден што завршил пред 12:30: ручекот е по последното место
        if (!hadLunch && current != null) {
            Place restaurant = nearestUnused(restaurants, usedFood, current);
            if (restaurant != null) {
                time = addStop(items, ItemType.LUNCH, restaurant, current, time, null);
                current = restaurant;
            }
        }

        // Вечера
        if (current != null) {
            Place restaurant = nearestUnused(restaurants, usedFood, current);
            if (restaurant != null) {
                addStop(items, ItemType.DINNER, restaurant, current, time, DINNER_NOT_BEFORE);
            }
        }

        return new DayPlan(dayNumber, date, weather, items, extras, walkingKm(items));
    }

    /**
     * Додава ставка во распоредот и го враќа времето кога таа завршува.
     *
     * @param notBefore ако не е null, ставката не почнува пред ова време (за вечера)
     */
    private static LocalTime addStop(List<ScheduleItem> items, ItemType type, Place place,
                                     Place current, LocalTime time, LocalTime notBefore) {
        int walk = current == null ? 0 : GeoUtils.walkMinutes(current, place);
        LocalTime start = time.plusMinutes(walk);
        if (notBefore != null && start.isBefore(notBefore)) {
            start = notBefore;
        }
        LocalTime end = start.plusMinutes(place.getVisitMinutes());
        items.add(ScheduleItem.of(type, place, start, end, walk));
        return end;
    }

    /** Најблискиот ресторан/кафуле до тековната позиција, што уште не е препорачан. */
    static Place nearestUnused(List<Place> options, Set<Place> used, Place current) {
        if (current == null) {
            return null;
        }
        Place best = null;
        for (Place p : options) {
            if (used.contains(p)) {
                continue;
            }
            if (best == null || GeoUtils.distanceKm(current, p) < GeoUtils.distanceKm(current, best)) {
                best = p;
            }
        }
        if (best != null) {
            used.add(best);
        }
        return best;
    }

    private static double walkingKm(List<ScheduleItem> items) {
        double km = 0;
        for (int i = 1; i < items.size(); i++) {
            ScheduleItem a = items.get(i - 1);
            ScheduleItem b = items.get(i);
            km += GeoUtils.distanceKm(a.lat(), a.lon(), b.lat(), b.lon());
        }
        double withStreets = km * GeoUtils.STREET_FACTOR;
        return Math.round(withStreets * 10) / 10.0; // една децимала
    }
}
