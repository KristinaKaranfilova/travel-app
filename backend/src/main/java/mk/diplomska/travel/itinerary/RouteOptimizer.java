package mk.diplomska.travel.itinerary;

import mk.diplomska.travel.place.Place;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Редослед на посета на местата во еден ден, за што пократко пешачење.
 * (Ова е варијанта на проблемот на патувачкиот трговец, TSP.)
 *
 *  1. Nearest neighbor: почни од местото најблиску до почетната точка,
 *     потоа секогаш оди до најблиското непосетено место.
 *  2. 2-opt подобрување: пробај да превртиш секој дел од рутата;
 *     ако рутата стане пократка, задржи ја промената. Повторувај
 *     додека има подобрување. Ова ги отстранува „вкрстувањата“ на патот.
 */
@Component
public class RouteOptimizer {

    public List<Place> optimize(List<Place> places, double startLat, double startLon) {
        return twoOpt(nearestNeighbor(places, startLat, startLon));
    }

    public static List<Place> nearestNeighbor(List<Place> places, double startLat, double startLon) {
        List<Place> remaining = new ArrayList<>(places);
        List<Place> route = new ArrayList<>();
        double currentLat = startLat;
        double currentLon = startLon;

        while (!remaining.isEmpty()) {
            Place nearest = remaining.get(0);
            for (Place p : remaining) {
                if (GeoUtils.distanceKm(currentLat, currentLon, p.getLat(), p.getLon())
                        < GeoUtils.distanceKm(currentLat, currentLon, nearest.getLat(), nearest.getLon())) {
                    nearest = p;
                }
            }
            route.add(nearest);
            remaining.remove(nearest);
            currentLat = nearest.getLat();
            currentLon = nearest.getLon();
        }
        return route;
    }

    public static List<Place> twoOpt(List<Place> route) {
        List<Place> best = new ArrayList<>(route);
        double bestLength = routeLengthKm(best);

        boolean improved = true;
        while (improved) {
            improved = false;
            for (int i = 0; i < best.size() - 1; i++) {
                for (int j = i + 1; j < best.size(); j++) {
                    List<Place> candidate = new ArrayList<>(best);
                    Collections.reverse(candidate.subList(i, j + 1)); // преврти го делот од i до j
                    double length = routeLengthKm(candidate);
                    if (length < bestLength - 1e-9) {
                        best = candidate;
                        bestLength = length;
                        improved = true;
                    }
                }
            }
        }
        return best;
    }

    /** Вкупна должина на рутата (збир на растојанијата помеѓу соседни места). */
    public static double routeLengthKm(List<Place> route) {
        double total = 0;
        for (int i = 0; i < route.size() - 1; i++) {
            total += GeoUtils.distanceKm(route.get(i), route.get(i + 1));
        }
        return total;
    }
}
