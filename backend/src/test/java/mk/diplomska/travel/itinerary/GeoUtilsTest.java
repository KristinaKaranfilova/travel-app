package mk.diplomska.travel.itinerary;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GeoUtilsTest {

    @Test
    void distanceBetweenSkopjeAndOhridIsAbout110Km() {
        double km = GeoUtils.distanceKm(41.9962, 21.4319, 41.1170, 20.8018);
        assertTrue(km > 105 && km < 115, "km = " + km);
    }

    @Test
    void distanceToSamePointIsZero() {
        assertEquals(0.0, GeoUtils.distanceKm(41.0, 21.0, 41.0, 21.0), 1e-9);
    }

    @Test
    void oneKilometerTakesAbout17MinutesToWalk() {
        // 0.009 степени географска ширина ≈ 1 km
        // 1 km × 1.3 / 4.5 km/h × 60 = 17.3 минути
        int minutes = GeoUtils.walkMinutes(41.0, 21.0, 41.009, 21.0);
        assertEquals(17, minutes);
    }
}
