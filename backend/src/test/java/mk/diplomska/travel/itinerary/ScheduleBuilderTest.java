package mk.diplomska.travel.itinerary;

import mk.diplomska.travel.place.Place;
import mk.diplomska.travel.place.PlaceCategory;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static mk.diplomska.travel.itinerary.TestPlaces.place;
import static org.junit.jupiter.api.Assertions.*;

class ScheduleBuilderTest {

    private final ScheduleBuilder builder = new ScheduleBuilder();
    private final LocalDate date = LocalDate.of(2026, 6, 1);

    @Test
    void lunchIsInsertedAfterHalfPastTwelveAtNearestRestaurant() {
        // 3 музеи на исто место: 09:00-10:30, 10:30-12:00, 12:00-13:30, па уште еден
        List<Place> route = List.of(
                place("M1", 41.0, 21.0, PlaceCategory.MUSEUM),
                place("M2", 41.0, 21.0, PlaceCategory.MUSEUM),
                place("M3", 41.0, 21.0, PlaceCategory.MUSEUM),
                place("M4", 41.0, 21.0, PlaceCategory.MUSEUM));
        Place near = place("Near", 41.0, 21.001, PlaceCategory.RESTAURANT);
        Place far = place("Far", 41.05, 21.05, PlaceCategory.RESTAURANT);

        DayPlan day = builder.build(1, date, null, route, List.of(far, near), List.of(), new HashSet<>());

        List<ItemType> types = day.items().stream().map(ScheduleItem::type).toList();
        assertEquals(List.of(ItemType.VISIT, ItemType.VISIT, ItemType.VISIT,
                ItemType.LUNCH, ItemType.VISIT, ItemType.DINNER), types);

        ScheduleItem lunch = day.items().get(3);
        assertEquals("Near", lunch.name());
        assertFalse(lunch.start().isBefore(LocalTime.of(12, 30)));
    }

    @Test
    void sameRestaurantIsNotRecommendedTwice() {
        Place only = place("Only", 41.0, 21.001, PlaceCategory.RESTAURANT);
        Set<Place> used = new HashSet<>();
        List<Place> route = List.of(place("A", 41.0, 21.0));

        DayPlan day = builder.build(1, date, null, route, List.of(only), List.of(), used);

        // Ресторанот е искористен за ручек, па за вечера нема друг
        long meals = day.items().stream().filter(i -> i.type() != ItemType.VISIT).count();
        assertEquals(1, meals);
        assertTrue(used.contains(only));
    }

    @Test
    void placesThatDoNotFitGoToExtras() {
        // 10 музеи × 90 минути = 15 часа, не собираат помеѓу 09:00 и 19:00
        List<Place> route = new java.util.ArrayList<>();
        for (int i = 0; i < 10; i++) {
            route.add(place("M" + i, 41.0, 21.0, PlaceCategory.MUSEUM));
        }

        DayPlan day = builder.build(1, date, null, route, List.of(), List.of(), new HashSet<>());

        long visits = day.items().stream().filter(i -> i.type() == ItemType.VISIT).count();
        assertTrue(visits < 10);
        assertEquals(10, visits + day.extras().size());
        assertTrue(day.items().stream().allMatch(i -> !i.end().isAfter(LocalTime.of(19, 0))));
    }

    @Test
    void dinnerIsNotBeforeSevenPm() {
        List<Place> route = List.of(place("A", 41.0, 21.0));
        List<Place> restaurants = List.of(
                place("R1", 41.0, 21.001, PlaceCategory.RESTAURANT),
                place("R2", 41.0, 21.002, PlaceCategory.RESTAURANT));

        DayPlan day = builder.build(1, date, null, route, restaurants, List.of(), new HashSet<>());

        ScheduleItem dinner = day.items().get(day.items().size() - 1);
        assertEquals(ItemType.DINNER, dinner.type());
        assertEquals(LocalTime.of(19, 0), dinner.start());
    }
}
