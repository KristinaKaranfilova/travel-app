package mk.diplomska.travel.itinerary;

import mk.diplomska.travel.place.Interest;
import mk.diplomska.travel.place.Place;
import mk.diplomska.travel.place.PlaceCategory;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static mk.diplomska.travel.itinerary.TestPlaces.place;
import static mk.diplomska.travel.itinerary.TestPlaces.wikiPlace;
import static org.junit.jupiter.api.Assertions.*;

class ItineraryServiceTest {

    @Test
    void scoreRewardsWikipediaAndMatchingInterest() {
        Place museumWithWiki = wikiPlace("M", 41.0, 21.0, PlaceCategory.MUSEUM);
        Place parkNoWiki = place("P", 41.0, 21.0, PlaceCategory.PARK);
        Set<Interest> culture = Set.of(Interest.CULTURE);

        assertEquals(5, ItineraryService.score(museumWithWiki, culture)); // 3 + 2
        assertEquals(0, ItineraryService.score(parkNoWiki, culture));
    }

    @Test
    void selectionLimitsOneCategoryToAThird() {
        List<Place> places = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            places.add(wikiPlace("Church" + i, 41.0, 21.0, PlaceCategory.CHURCH)); // високи оценки
        }
        places.add(place("Museum", 41.0, 21.0, PlaceCategory.MUSEUM));
        places.add(place("Castle", 41.0, 21.0, PlaceCategory.CASTLE));

        List<Place> selected = ItineraryService.select(places, Set.of(Interest.RELIGION), 6, 41.0, 21.0);

        assertEquals(6, selected.size());
        assertTrue(selected.stream().anyMatch(p -> p.getName().equals("Museum")));
        assertTrue(selected.stream().anyMatch(p -> p.getName().equals("Castle")));
    }

    @Test
    void selectionPrefersPlacesCloserToCenterWhenScoresAreEqual() {
        Place near = place("Near", 41.001, 21.0, PlaceCategory.MUSEUM);
        Place far = place("Far", 41.03, 21.0, PlaceCategory.MUSEUM);

        List<Place> selected = ItineraryService.select(List.of(far, near), Set.of(Interest.CULTURE), 1, 41.0, 21.0);

        assertEquals(List.of(near), selected);
    }
}
