package mk.diplomska.travel.itinerary;

import mk.diplomska.travel.place.Place;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Групирање места по денови со k-means, каде k = број на денови.
 * Целта: местата во еден ден да се блиску едно до друго.
 *
 * Чекори:
 *  1. Почетни центри: k места што се најдалеку едно од друго.
 *  2. Повторувај:
 *     а) секое место оди во групата со најблизок центар
 *     б) центарот на секоја група = просек на координатите на нејзините места
 *     додека нема промена (или 100 повторувања).
 *  3. Балансирање: ниту еден ден да нема повеќе од ceil(n/k) места.
 */
@Component
public class KMeansClusterer {

    private static final int MAX_ITERATIONS = 100;

    public List<List<Place>> cluster(List<Place> places, int k) {
        int n = places.size();
        int groups = Math.min(k, n); // не може да има повеќе групи од места

        List<List<Place>> clusters = new ArrayList<>();
        if (groups > 0) {
            double[][] centers = initialCenters(places, groups);
            int[] assignment = new int[n];
            Arrays.fill(assignment, -1);

            for (int iteration = 0; iteration < MAX_ITERATIONS; iteration++) {
                // а) доделување на најблискиот центар
                boolean changed = false;
                for (int i = 0; i < n; i++) {
                    int nearest = nearestCenter(places.get(i), centers);
                    if (nearest != assignment[i]) {
                        assignment[i] = nearest;
                        changed = true;
                    }
                }
                if (!changed) {
                    break; // стабилно решение
                }
                // б) нови центри
                centers = recomputeCenters(places, assignment, centers);
            }

            for (int c = 0; c < groups; c++) {
                clusters.add(new ArrayList<>());
            }
            for (int i = 0; i < n; i++) {
                clusters.get(assignment[i]).add(places.get(i));
            }
            balance(clusters, centers, (int) Math.ceil((double) n / groups));
        }

        // Ако има помалку места од денови, останатите денови се празни.
        while (clusters.size() < k) {
            clusters.add(new ArrayList<>());
        }
        return clusters;
    }

    /**
     * Почетни центри (детерминистички):
     * прво место е најдалечното од просекот на сите,
     * секое следно е местото најдалеку од веќе избраните центри.
     */
    static double[][] initialCenters(List<Place> places, int k) {
        double meanLat = places.stream().mapToDouble(Place::getLat).average().orElse(0);
        double meanLon = places.stream().mapToDouble(Place::getLon).average().orElse(0);

        List<Place> chosen = new ArrayList<>();
        Place first = places.get(0);
        for (Place p : places) {
            if (GeoUtils.distanceKm(p.getLat(), p.getLon(), meanLat, meanLon)
                    > GeoUtils.distanceKm(first.getLat(), first.getLon(), meanLat, meanLon)) {
                first = p;
            }
        }
        chosen.add(first);

        while (chosen.size() < k) {
            Place farthest = null;
            double farthestDistance = -1;
            for (Place p : places) {
                if (chosen.contains(p)) {
                    continue;
                }
                double distanceToChosen = Double.MAX_VALUE;
                for (Place c : chosen) {
                    distanceToChosen = Math.min(distanceToChosen, GeoUtils.distanceKm(p, c));
                }
                if (distanceToChosen > farthestDistance) {
                    farthestDistance = distanceToChosen;
                    farthest = p;
                }
            }
            chosen.add(farthest);
        }

        double[][] centers = new double[k][2];
        for (int c = 0; c < k; c++) {
            centers[c][0] = chosen.get(c).getLat();
            centers[c][1] = chosen.get(c).getLon();
        }
        return centers;
    }

    static int nearestCenter(Place p, double[][] centers) {
        int best = 0;
        double bestDistance = Double.MAX_VALUE;
        for (int c = 0; c < centers.length; c++) {
            double d = GeoUtils.distanceKm(p.getLat(), p.getLon(), centers[c][0], centers[c][1]);
            if (d < bestDistance) {
                bestDistance = d;
                best = c;
            }
        }
        return best;
    }

    private static double[][] recomputeCenters(List<Place> places, int[] assignment, double[][] oldCenters) {
        int k = oldCenters.length;
        double[][] sums = new double[k][2];
        int[] counts = new int[k];
        for (int i = 0; i < places.size(); i++) {
            sums[assignment[i]][0] += places.get(i).getLat();
            sums[assignment[i]][1] += places.get(i).getLon();
            counts[assignment[i]]++;
        }
        double[][] centers = new double[k][2];
        for (int c = 0; c < k; c++) {
            if (counts[c] == 0) {
                centers[c] = oldCenters[c]; // празна група го задржува стариот центар
            } else {
                centers[c][0] = sums[c][0] / counts[c];
                centers[c][1] = sums[c][1] / counts[c];
            }
        }
        return centers;
    }

    /**
     * k-means не гарантира еднакви групи (на пр. 8 места во еден ден, 2 во друг).
     * Додека некоја група е преполна, нејзиното најоддалечено место
     * се преместува во најблиската група што има слободно место.
     */
    static void balance(List<List<Place>> clusters, double[][] centers, int capacity) {
        for (int c = 0; c < clusters.size(); c++) {
            List<Place> cluster = clusters.get(c);
            while (cluster.size() > capacity) {
                Place farthest = cluster.get(0);
                for (Place p : cluster) {
                    if (distanceToCenter(p, centers[c]) > distanceToCenter(farthest, centers[c])) {
                        farthest = p;
                    }
                }

                int target = -1;
                for (int other = 0; other < clusters.size(); other++) {
                    if (other == c || clusters.get(other).size() >= capacity) {
                        continue;
                    }
                    if (target == -1
                            || distanceToCenter(farthest, centers[other]) < distanceToCenter(farthest, centers[target])) {
                        target = other;
                    }
                }

                cluster.remove(farthest);
                clusters.get(target).add(farthest);
            }
        }
    }

    private static double distanceToCenter(Place p, double[] center) {
        return GeoUtils.distanceKm(p.getLat(), p.getLon(), center[0], center[1]);
    }
}
