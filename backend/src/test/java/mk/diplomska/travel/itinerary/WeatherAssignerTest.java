package mk.diplomska.travel.itinerary;

import mk.diplomska.travel.place.Place;
import mk.diplomska.travel.place.PlaceCategory;
import mk.diplomska.travel.weather.DayWeather;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static mk.diplomska.travel.itinerary.TestPlaces.place;
import static org.junit.jupiter.api.Assertions.*;

class WeatherAssignerTest {

    private final WeatherAssigner assigner = new WeatherAssigner();

    private final LocalDate day1 = LocalDate.of(2026, 6, 1);
    private final LocalDate day2 = LocalDate.of(2026, 6, 2);

    private final List<Place> outdoor = List.of(
            place("Park", 41.0, 21.0, PlaceCategory.PARK),
            place("Viewpoint", 41.0, 21.0, PlaceCategory.VIEWPOINT));
    private final List<Place> indoor = List.of(
            place("Museum", 41.0, 21.0, PlaceCategory.MUSEUM),
            place("Gallery", 41.0, 21.0, PlaceCategory.GALLERY));

    @Test
    void rainyDayGetsIndoorPlaces() {
        Map<LocalDate, DayWeather> weather = Map.of(
                day1, new DayWeather(day1, 0, 25.0, 15.0, 5),   // сончево
                day2, new DayWeather(day2, 61, 18.0, 12.0, 90)); // дожд

        List<List<Place>> result = assigner.assign(List.of(indoor, outdoor), List.of(day1, day2), weather);

        assertEquals(outdoor, result.get(0));
        assertEquals(indoor, result.get(1));
    }

    @Test
    void withoutForecastOrderIsUnchanged() {
        List<List<Place>> clusters = List.of(outdoor, indoor);

        List<List<Place>> result = assigner.assign(clusters, List.of(day1, day2), Map.of());

        assertEquals(clusters, result);
    }
}
