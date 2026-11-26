package mk.diplomska.travel.itinerary;

import mk.diplomska.travel.place.Place;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static mk.diplomska.travel.itinerary.TestPlaces.place;
import static org.junit.jupiter.api.Assertions.*;

class KMeansClustererTest {

    private final KMeansClusterer clusterer = new KMeansClusterer();

    @Test
    void twoDistantGroupsAreSeparated() {
        // Група „запад“ и група „исток“, околу 5 km една од друга
        Place w1 = place("W1", 41.000, 21.000);
        Place w2 = place("W2", 41.001, 21.001);
        Place w3 = place("W3", 41.002, 21.000);
        Place e1 = place("E1", 41.000, 21.060);
        Place e2 = place("E2", 41.001, 21.061);
        Place e3 = place("E3", 41.002, 21.060);

        List<List<Place>> clusters = clusterer.cluster(List.of(w1, e1, w2, e2, w3, e3), 2);

        assertEquals(2, clusters.size());
        Set<Place> first = Set.copyOf(clusters.get(0));
        Set<Place> second = Set.copyOf(clusters.get(1));
        Set<Place> west = Set.of(w1, w2, w3);
        Set<Place> east = Set.of(e1, e2, e3);
        assertTrue((first.equals(west) && second.equals(east)) || (first.equals(east) && second.equals(west)));
    }

    @Test
    void groupsAreBalanced() {
        // 5 места збиени заедно и 1 далеку: без балансирање би било 5 + 1
        List<Place> places = List.of(
                place("A", 41.000, 21.000),
                place("B", 41.001, 21.000),
                place("C", 41.000, 21.001),
                place("D", 41.001, 21.001),
                place("E", 41.002, 21.002),
                place("Far", 41.050, 21.050));

        List<List<Place>> clusters = clusterer.cluster(places, 2);

        assertEquals(3, clusters.get(0).size());
        assertEquals(3, clusters.get(1).size());
    }

    @Test
    void fewerPlacesThanDaysGivesEmptyDays() {
        List<List<Place>> clusters = clusterer.cluster(List.of(place("A", 41.0, 21.0)), 3);

        assertEquals(3, clusters.size());
        assertEquals(1, clusters.stream().mapToInt(List::size).sum());
    }

    @Test
    void noPlacesGivesEmptyDays() {
        List<List<Place>> clusters = clusterer.cluster(List.of(), 2);
        assertEquals(2, clusters.size());
        assertTrue(clusters.get(0).isEmpty());
    }
}
