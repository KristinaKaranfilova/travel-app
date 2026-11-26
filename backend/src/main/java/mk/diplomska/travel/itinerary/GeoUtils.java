package mk.diplomska.travel.itinerary;

import mk.diplomska.travel.place.Place;

/**
 * Пресметки со географски координати.
 */
public final class GeoUtils {

    /** Радиус на Земјата во километри. */
    private static final double EARTH_RADIUS_KM = 6371.0;

    /**
     * Улиците не се права линија. Вистинската пешачка патека е во просек
     * околу 30% подолга од воздушното растојание.
     */
    public static final double STREET_FACTOR = 1.3;

    /** Просечна брзина на пешачење во km/h. */
    public static final double WALKING_SPEED_KMH = 4.5;

    private GeoUtils() {
    }

    /**
     * Haversine формула: најкратко растојание помеѓу две точки на сфера (воздушна линија).
     */
    public static double distanceKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_KM * c;
    }

    public static double distanceKm(Place a, Place b) {
        return distanceKm(a.getLat(), a.getLon(), b.getLat(), b.getLon());
    }

    /** Проценето време на пешачење во минути: растојание × 1.3 / 4.5 km/h. */
    public static int walkMinutes(double lat1, double lon1, double lat2, double lon2) {
        double km = distanceKm(lat1, lon1, lat2, lon2) * STREET_FACTOR;
        return (int) Math.round(km / WALKING_SPEED_KMH * 60);
    }

    public static int walkMinutes(Place a, Place b) {
        return walkMinutes(a.getLat(), a.getLon(), b.getLat(), b.getLon());
    }
}
