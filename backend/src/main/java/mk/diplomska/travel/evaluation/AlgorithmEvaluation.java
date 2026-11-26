package mk.diplomska.travel.evaluation;

import mk.diplomska.travel.city.City;
import mk.diplomska.travel.city.CityService;
import mk.diplomska.travel.itinerary.ItineraryService;
import mk.diplomska.travel.itinerary.KMeansClusterer;
import mk.diplomska.travel.itinerary.RouteOptimizer;
import mk.diplomska.travel.place.Interest;
import mk.diplomska.travel.place.Place;
import mk.diplomska.travel.place.PlaceKind;
import mk.diplomska.travel.place.PlaceService;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Евалуација на алгоритмот за план (за поглавјето „Евалуација“ во трудот).
 *
 * Сценарио за секој град: 3 дена, 7 места на ден (интензивно темпо), сите интереси.
 *
 * 1. Редослед во денот: колку е долга рутата кај
 *      - случаен редослед (просек од 100 обиди)
 *      - nearest neighbor
 *      - nearest neighbor + 2-opt (нашиот пристап)
 *      - оптимално решение (brute force: сите 7! = 5040 редоследи)
 *
 * 2. Групирање по денови: вкупно пешачење за сите денови кај
 *      - случајна поделба (просек од 100 обиди)
 *      - поделба по редослед на оценка (првите 7 во ден 1, итн.)
 *      - k-means со балансирање (нашиот пристап)
 *    Во сите три случаи редоследот во денот е со NN + 2-opt, за да се споредува само групирањето.
 */
@Component
public class AlgorithmEvaluation {

    static final int DAYS = 3;
    static final int PLACES_PER_DAY = 7;
    static final int RANDOM_RUNS = 100;

    private final CityService cityService;
    private final PlaceService placeService;
    private final KMeansClusterer clusterer;
    private final RouteOptimizer optimizer;

    public AlgorithmEvaluation(CityService cityService, PlaceService placeService,
                               KMeansClusterer clusterer, RouteOptimizer optimizer) {
        this.cityService = cityService;
        this.placeService = placeService;
        this.clusterer = clusterer;
        this.optimizer = optimizer;
    }

    /** Ја враќа содржината на results/algorithm.md */
    public String run(List<Long> cityIds) {
        StringBuilder routeTable = new StringBuilder("""
                | Град | Случаен (km) | Nearest neighbor (km) | NN + 2-opt (km) | Оптимално (km) | NN отстапување | NN+2-opt отстапување |
                |---|---|---|---|---|---|---|
                """);
        StringBuilder clusterTable = new StringBuilder("""
                | Град | Случајна поделба (km) | По оценка (km) | k-means (km) | Најдолг ден, k-means (km) |
                |---|---|---|---|---|
                """);
        StringBuilder timing = new StringBuilder();
        StringBuilder skipped = new StringBuilder();

        double totalNn = 0, total2opt = 0, totalOptimal = 0;
        long nnNanos = 0, twoOptNanos = 0, bruteNanos = 0;
        int routes = 0;

        for (Long cityId : cityIds) {
            City city = cityService.getById(cityId);
            List<Place> attractions;
            try {
                attractions = placeService.getPlaces(cityId, PlaceKind.ATTRACTION);
            } catch (RuntimeException e) {
                skipped.append("- ").append(city.getName()).append(": местата не можат да се преземат (")
                        .append(e.getMessage()).append(")\n");
                continue;
            }
            List<Place> selected = ItineraryService.select(attractions, EnumSet.allOf(Interest.class),
                    DAYS * PLACES_PER_DAY, city.getLat(), city.getLon());
            if (selected.size() < DAYS * PLACES_PER_DAY) {
                skipped.append("- ").append(city.getName()).append(": само ").append(selected.size())
                        .append(" атракции\n");
                continue;
            }

            // ---------- 1. Редослед во денот ----------
            List<List<Place>> days = clusterer.cluster(selected, DAYS);
            Random random = new Random(42); // фиксно семе = исти резултати при секое извршување
            double sumRandom = 0, sumNn = 0, sum2opt = 0, sumOptimal = 0;
            for (List<Place> day : days) {
                sumRandom += averageRandomRoute(day, random);

                long t0 = System.nanoTime();
                List<Place> nn = RouteOptimizer.nearestNeighbor(day, city.getLat(), city.getLon());
                long t1 = System.nanoTime();
                List<Place> twoOpt = RouteOptimizer.twoOpt(nn);
                long t2 = System.nanoTime();
                double optimal = bruteForceShortest(day);
                long t3 = System.nanoTime();

                nnNanos += t1 - t0;
                twoOptNanos += t2 - t0;
                bruteNanos += t3 - t2;
                routes++;

                sumNn += RouteOptimizer.routeLengthKm(nn);
                sum2opt += RouteOptimizer.routeLengthKm(twoOpt);
                sumOptimal += optimal;
            }
            totalNn += sumNn;
            total2opt += sum2opt;
            totalOptimal += sumOptimal;
            routeTable.append(String.format(Locale.ROOT, "| %s | %.2f | %.2f | %.2f | %.2f | %s | %s |%n",
                    city.getName(), sumRandom, sumNn, sum2opt, sumOptimal,
                    gap(sumNn, sumOptimal), gap(sum2opt, sumOptimal)));

            // ---------- 2. Групирање по денови ----------
            double randomSplit = 0;
            for (int run = 0; run < RANDOM_RUNS; run++) {
                List<Place> shuffled = new ArrayList<>(selected);
                Collections.shuffle(shuffled, random);
                randomSplit += totalWalk(chunks(shuffled), city);
            }
            randomSplit /= RANDOM_RUNS;
            double byScore = totalWalk(chunks(selected), city); // selected е подреден по оценка
            double kmeans = totalWalk(days, city);
            double longestDay = days.stream()
                    .mapToDouble(d -> RouteOptimizer.routeLengthKm(optimizer.optimize(d, city.getLat(), city.getLon())))
                    .max().orElse(0);
            clusterTable.append(String.format(Locale.ROOT, "| %s | %.2f | %.2f | %.2f | %.2f |%n",
                    city.getName(), randomSplit, byScore, kmeans, longestDay));
        }

        if (routes > 0) {
            timing.append(String.format(Locale.ROOT, """
                    Просечно време по ден (%d денови, по %d места):
                    - Nearest neighbor: %.3f ms
                    - Nearest neighbor + 2-opt: %.3f ms
                    - Brute force (оптимално, %d редоследи): %.3f ms
                    """, routes, PLACES_PER_DAY,
                    nnNanos / 1e6 / routes, twoOptNanos / 1e6 / routes,
                    factorial(PLACES_PER_DAY), bruteNanos / 1e6 / routes));
        }

        return """
                # Евалуација на алгоритмот за план

                Сценарио: %d дена × %d атракции на ден, сите интереси. Растојанијата се воздушни (haversine),
                збир за сите денови во градот. „Отстапување“ = колку %% е подолга рутата од оптималната.

                ## 1. Редослед на посета во денот

                %s
                Вкупно за сите градови: NN отстапува %s, NN + 2-opt отстапува %s од оптималното.

                %s
                ## 2. Групирање по денови

                Вкупно пешачење за сите %d дена (редоследот во секој ден е NN + 2-opt):

                %s
                %s""".formatted(DAYS, PLACES_PER_DAY, routeTable, gap(totalNn, totalOptimal),
                gap(total2opt, totalOptimal), timing, DAYS, clusterTable,
                skipped.isEmpty() ? "" : "\n## Прескокнати градови\n\n" + skipped);
    }

    /** Ги дели местата на денови по редослед: првите 7 во ден 1, следните 7 во ден 2... */
    private static List<List<Place>> chunks(List<Place> places) {
        List<List<Place>> days = new ArrayList<>();
        for (int i = 0; i < places.size(); i += PLACES_PER_DAY) {
            days.add(places.subList(i, Math.min(i + PLACES_PER_DAY, places.size())));
        }
        return days;
    }

    private double totalWalk(List<List<Place>> days, City city) {
        return days.stream()
                .mapToDouble(d -> RouteOptimizer.routeLengthKm(optimizer.optimize(d, city.getLat(), city.getLon())))
                .sum();
    }

    private static double averageRandomRoute(List<Place> day, Random random) {
        double sum = 0;
        for (int run = 0; run < RANDOM_RUNS; run++) {
            List<Place> shuffled = new ArrayList<>(day);
            Collections.shuffle(shuffled, random);
            sum += RouteOptimizer.routeLengthKm(shuffled);
        }
        return sum / RANDOM_RUNS;
    }

    /** Ги пробува сите редоследи и ја враќа најкратката должина (оптимално решение). */
    static double bruteForceShortest(List<Place> places) {
        return permute(new ArrayList<>(places), 0);
    }

    private static double permute(List<Place> route, int fixed) {
        if (fixed == route.size()) {
            return RouteOptimizer.routeLengthKm(route);
        }
        double best = Double.MAX_VALUE;
        for (int i = fixed; i < route.size(); i++) {
            Collections.swap(route, fixed, i);
            best = Math.min(best, permute(route, fixed + 1));
            Collections.swap(route, fixed, i);
        }
        return best;
    }

    private static String gap(double value, double optimal) {
        if (optimal == 0) {
            return "0%";
        }
        return String.format(Locale.ROOT, "%.1f%%", (value - optimal) / optimal * 100);
    }

    private static long factorial(int n) {
        long f = 1;
        for (int i = 2; i <= n; i++) {
            f *= i;
        }
        return f;
    }
}
