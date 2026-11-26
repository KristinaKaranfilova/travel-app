package mk.diplomska.travel.itinerary;

import mk.diplomska.travel.place.Place;
import org.junit.jupiter.api.Test;

import java.util.List;

import static mk.diplomska.travel.itinerary.TestPlaces.place;
import static org.junit.jupiter.api.Assertions.*;

class RouteOptimizerTest {

    private final RouteOptimizer optimizer = new RouteOptimizer();

    @Test
    void placesOnALineAreVisitedInOrder() {
        Place a = place("A", 41.0, 21.00);
        Place b = place("B", 41.0, 21.01);
        Place c = place("C", 41.0, 21.02);
        Place d = place("D", 41.0, 21.03);

        // Почетна точка лево од A, места дадени измешано
        List<Place> route = optimizer.optimize(List.of(c, a, d, b), 41.0, 20.99);

        assertEquals(List.of(a, b, c, d), route);
    }

    @Test
    void twoOptRemovesCrossing() {
        // Правоаголник A-B-C-D (на ширина 41° степен должина е пократок од степен ширина,
        // па AB и CD се пократките страни). Редоследот A-C-B-D се вкрстува.
        Place a = place("A", 41.00, 21.00);
        Place b = place("B", 41.00, 21.01);
        Place c = place("C", 41.01, 21.01);
        Place d = place("D", 41.01, 21.00);
        List<Place> crossing = List.of(a, c, b, d);

        List<Place> improved = RouteOptimizer.twoOpt(crossing);

        assertTrue(RouteOptimizer.routeLengthKm(improved) < RouteOptimizer.routeLengthKm(crossing));
        // Оптималната отворена рута оди по двете кратки и една долга страна: A-B-C-D
        double optimal = RouteOptimizer.routeLengthKm(List.of(a, b, c, d));
        assertEquals(optimal, RouteOptimizer.routeLengthKm(improved), 1e-9);
    }

    @Test
    void optimizedRouteIsNeverLongerThanNearestNeighbor() {
        List<Place> places = List.of(
                place("1", 41.000, 21.000), place("2", 41.010, 21.030),
                place("3", 41.020, 21.005), place("4", 41.003, 21.025),
                place("5", 41.015, 21.015), place("6", 41.025, 21.028));

        List<Place> nn = RouteOptimizer.nearestNeighbor(places, 41.0, 21.0);
        List<Place> optimized = optimizer.optimize(places, 41.0, 21.0);

        assertTrue(RouteOptimizer.routeLengthKm(optimized) <= RouteOptimizer.routeLengthKm(nn));
        assertEquals(places.size(), optimized.size());
    }
}
