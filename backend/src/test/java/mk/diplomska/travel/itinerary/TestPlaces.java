package mk.diplomska.travel.itinerary;

import mk.diplomska.travel.city.City;
import mk.diplomska.travel.place.Place;
import mk.diplomska.travel.place.PlaceCategory;

/** Помошни методи за креирање места во тестовите. */
final class TestPlaces {

    static final City CITY = new City("Test", "MK", "relation", 1L,
            41.0, 21.0, 40.9, 20.9, 41.1, 21.1);

    private static long nextId = 1;

    private TestPlaces() {
    }

    static Place place(String name, double lat, double lon, PlaceCategory category) {
        return new Place(CITY, "node", nextId++, name, lat, lon, category, false, null);
    }

    static Place place(String name, double lat, double lon) {
        return place(name, lat, lon, PlaceCategory.MONUMENT);
    }

    static Place wikiPlace(String name, double lat, double lon, PlaceCategory category) {
        return new Place(CITY, "node", nextId++, name, lat, lon, category, true, null);
    }
}
