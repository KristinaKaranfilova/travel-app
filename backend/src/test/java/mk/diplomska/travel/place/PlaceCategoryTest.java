package mk.diplomska.travel.place;

import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class PlaceCategoryTest {

    @Test
    void museumIsIndoorCulture() {
        PlaceCategory c = PlaceCategory.fromTags(Map.of("tourism", "museum")).orElseThrow();
        assertEquals(PlaceCategory.MUSEUM, c);
        assertTrue(c.indoor());
        assertEquals(Interest.CULTURE, c.interest());
        assertEquals(PlaceKind.ATTRACTION, c.kind());
    }

    @Test
    void specificTagWinsOverGenericAttraction() {
        // Самоил тврдина во OSM има и tourism=attraction и historic=castle
        Map<String, String> tags = Map.of("tourism", "attraction", "historic", "castle");
        assertEquals(Optional.of(PlaceCategory.CASTLE), PlaceCategory.fromTags(tags));
    }

    @Test
    void restaurantAndCafeAreFoodKinds() {
        assertEquals(PlaceKind.RESTAURANT,
                PlaceCategory.fromTags(Map.of("amenity", "restaurant")).orElseThrow().kind());
        assertEquals(PlaceKind.CAFE,
                PlaceCategory.fromTags(Map.of("amenity", "cafe")).orElseThrow().kind());
    }

    @Test
    void unknownTagsAreIgnored() {
        assertTrue(PlaceCategory.fromTags(Map.of("shop", "bakery")).isEmpty());
        assertTrue(PlaceCategory.fromTags(null).isEmpty());
    }

    @Test
    void overpassQueryUsesDotDecimalsRegardlessOfLocale() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY); // германски користи запирка: 41,11
            String query = OverpassClient.buildQuery(41.1172, 20.8016, 5000);
            assertTrue(query.contains("(around:5000,41.117200,20.801600)"), query);
        } finally {
            Locale.setDefault(original);
        }
    }
}
