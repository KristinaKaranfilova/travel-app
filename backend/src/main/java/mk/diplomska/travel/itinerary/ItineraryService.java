package mk.diplomska.travel.itinerary;

import mk.diplomska.travel.city.City;
import mk.diplomska.travel.city.CityService;
import mk.diplomska.travel.common.BackgroundTasks;
import mk.diplomska.travel.place.Interest;
import mk.diplomska.travel.place.Place;
import mk.diplomska.travel.place.PlaceCategory;
import mk.diplomska.travel.place.PlaceKind;
import mk.diplomska.travel.place.PlaceService;
import mk.diplomska.travel.weather.DayWeather;
import mk.diplomska.travel.weather.OpenMeteoClient;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Генерирање план за патување. Ги поврзува сите чекори од алгоритмот:
 *
 *  1. Избор на најдобрите N атракции (N = денови × места на ден)
 *  2. Групирање по денови (k-means)
 *  3. Доделување групи на денови според временската прогноза
 *  4. Редослед во секој ден (nearest neighbor + 2-opt)
 *  5. Распоред по часови со ручек, кафе и вечера
 */
@Service
public class ItineraryService {

    /** Најдолго патување што го поддржуваме. */
    public static final int MAX_DAYS = 7;

    private final CityService cityService;
    private final PlaceService placeService;
    private final OpenMeteoClient weatherClient;
    private final KMeansClusterer clusterer;
    private final WeatherAssigner weatherAssigner;
    private final RouteOptimizer routeOptimizer;
    private final ScheduleBuilder scheduleBuilder;
    private final BackgroundTasks background;

    public ItineraryService(CityService cityService, PlaceService placeService, OpenMeteoClient weatherClient,
                            KMeansClusterer clusterer, WeatherAssigner weatherAssigner,
                            RouteOptimizer routeOptimizer, ScheduleBuilder scheduleBuilder,
                            BackgroundTasks background) {
        this.cityService = cityService;
        this.placeService = placeService;
        this.weatherClient = weatherClient;
        this.clusterer = clusterer;
        this.weatherAssigner = weatherAssigner;
        this.routeOptimizer = routeOptimizer;
        this.scheduleBuilder = scheduleBuilder;
        this.background = background;
    }

    public TripPlan generate(Long cityId, LocalDate startDate, LocalDate endDate,
                             Set<Interest> interests, Pace pace) {
        City city = cityService.getById(cityId);
        List<LocalDate> dates = startDate.datesUntil(endDate.plusDays(1)).toList();

        // Прогнозата се бара паралелно со местата (наместо по нив)
        CompletableFuture<Map<LocalDate, DayWeather>> forecast = background.supply(
                () -> weatherClient.forecast(city.getLat(), city.getLon(), startDate, endDate));

        List<Place> all = placeService.getPlaces(cityId);
        List<Place> attractions = ofKind(all, PlaceKind.ATTRACTION);
        List<Place> restaurants = ofKind(all, PlaceKind.RESTAURANT);
        List<Place> cafes = ofKind(all, PlaceKind.CAFE);

        // 1. Избор
        int count = dates.size() * pace.placesPerDay();
        List<Place> selected = select(attractions, interests, count, city.getLat(), city.getLon());

        // 2. Групирање
        List<List<Place>> clusters = clusterer.cluster(selected, dates.size());

        // 3. Временска прогноза
        Map<LocalDate, DayWeather> weather = forecast.exceptionally(e -> Map.of()).join();
        List<List<Place>> clustersByDay = weatherAssigner.assign(clusters, dates, weather);

        // 4. и 5. Рута и распоред за секој ден
        Set<Place> usedFood = new HashSet<>();
        List<DayPlan> days = new ArrayList<>();
        for (int i = 0; i < dates.size(); i++) {
            List<Place> route = routeOptimizer.optimize(clustersByDay.get(i), city.getLat(), city.getLon());
            LocalDate date = dates.get(i);
            days.add(scheduleBuilder.build(i + 1, date, weather.get(date), route, restaurants, cafes, usedFood));
        }

        return new TripPlan(city.getId(), city.getName(), city.getLat(), city.getLon(),
                startDate, endDate, pace, List.copyOf(interests), days);
    }

    /**
     * Оценка на атракција:
     *  +3 ако има Wikipedia запис (мерка за популарност; OSM нема оценки)
     *  +2 ако категоријата одговара на интересите на корисникот
     */
    static int score(Place p, Set<Interest> interests) {
        int score = 0;
        if (p.isHasWikipedia()) {
            score += 3;
        }
        if (interests.contains(p.getCategory().interest())) {
            score += 2;
        }
        return score;
    }

    /**
     * Ги избира најдобрите count атракции.
     * Подредување: по оценка (опаѓачки), а при иста оценка поблиските до центарот први.
     * Разновидност: најмногу 1/3 од местата од иста категорија (на пр. да не се 10 цркви).
     * Ако нема доволно разновидни места, остатокот се пополнува без ова ограничување.
     */
    public static List<Place> select(List<Place> attractions, Set<Interest> interests, int count,
                              double centerLat, double centerLon) {
        List<Place> sorted = new ArrayList<>(attractions);
        sorted.sort(Comparator
                .comparingInt((Place p) -> score(p, interests)).reversed()
                .thenComparingDouble(p -> GeoUtils.distanceKm(p.getLat(), p.getLon(), centerLat, centerLon)));

        int maxPerCategory = Math.max(1, (int) Math.ceil(count / 3.0));
        Map<PlaceCategory, Integer> perCategory = new HashMap<>();
        List<Place> selected = new ArrayList<>();

        for (Place p : sorted) {
            if (selected.size() == count) {
                break;
            }
            int used = perCategory.getOrDefault(p.getCategory(), 0);
            if (used < maxPerCategory) {
                selected.add(p);
                perCategory.put(p.getCategory(), used + 1);
            }
        }
        for (Place p : sorted) {
            if (selected.size() == count) {
                break;
            }
            if (!selected.contains(p)) {
                selected.add(p);
            }
        }
        return selected;
    }

    private static List<Place> ofKind(List<Place> places, PlaceKind kind) {
        return places.stream().filter(p -> p.getKind() == kind).toList();
    }
}
