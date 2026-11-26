package mk.diplomska.travel.place;

import mk.diplomska.travel.city.City;
import mk.diplomska.travel.city.CityService;
import mk.diplomska.travel.common.BackgroundTasks;
import mk.diplomska.travel.place.OverpassClient.OverpassElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Места за даден град, со кеширање во базата.
 *
 * Првиот пат кога ќе се побара град, местата се преземаат од Overpass
 * и се зачувуваат. Следните барања се читаат од базата.
 * По 30 дена податоците се сметаат за стари и повторно се преземаат.
 *
 * Брзина: преземањето од Overpass трае и до една минута. Затоа почнува однапред
 * ({@link #prefetch}), веднаш штом корисникот ќе избере град, а не дури кога ќе побара план.
 * Ако за ист град веќе тече преземање, следните барања го чекаат истото (не праќаат ново).
 */
@Service
public class PlaceService {

    private static final Logger log = LoggerFactory.getLogger(PlaceService.class);

    /** Колку долго кешот е валиден. */
    static final int CACHE_DAYS = 30;

    /** Радиус околу центарот на градот во кој бараме места (5 km пешачка зона). */
    static final int SEARCH_RADIUS_METERS = 5000;

    /** Состојба на местата за градот, за frontend-от да покаже напредок. */
    public record Status(boolean ready, boolean loading, long attractions, long restaurants, long cafes, String error) {
    }

    private final OverpassClient overpass;
    private final PlaceRepository places;
    private final CityService cityService;
    private final BackgroundTasks background;
    private final TransactionTemplate tx;

    /** Преземања што тековно траат, по id на градот. */
    private final Map<Long, CompletableFuture<Void>> inFlight = new ConcurrentHashMap<>();
    /** Последната грешка по град (на пр. Overpass е преоптоварен), за статусот. */
    private final Map<Long, String> lastError = new ConcurrentHashMap<>();

    public PlaceService(OverpassClient overpass, PlaceRepository places, CityService cityService,
                        BackgroundTasks background, TransactionTemplate tx) {
        this.overpass = overpass;
        this.places = places;
        this.cityService = cityService;
        this.background = background;
        this.tx = tx;
    }

    public List<Place> getPlaces(Long cityId) {
        await(ensureFresh(cityId));
        return places.findByCityId(cityId);
    }

    public List<Place> getPlaces(Long cityId, PlaceKind kind) {
        await(ensureFresh(cityId));
        return places.findByCityIdAndKind(cityId, kind);
    }

    /** Почни го преземањето во позадина (ако е потребно), без да се чека. */
    public void prefetch(Long cityId) {
        ensureFresh(cityId);
    }

    /** Дали местата се подготвени. Ако не се и ништо не тече, го почнува преземањето. */
    public Status status(Long cityId) {
        CompletableFuture<Void> future = ensureFresh(cityId);
        if (!future.isDone()) {
            return new Status(false, true, 0, 0, 0, null);
        }
        if (future.isCompletedExceptionally()) {
            return new Status(false, false, 0, 0, 0, lastError.get(cityId));
        }
        return new Status(true, false,
                places.countByCityIdAndKind(cityId, PlaceKind.ATTRACTION),
                places.countByCityIdAndKind(cityId, PlaceKind.RESTAURANT),
                places.countByCityIdAndKind(cityId, PlaceKind.CAFE),
                null);
    }

    /** Завршена задача ако кешот е свеж; инаку тековното (или ново) преземање. */
    private CompletableFuture<Void> ensureFresh(Long cityId) {
        CompletableFuture<Void> running = inFlight.get(cityId);
        if (running != null) {
            return running;
        }
        if (isFresh(cityService.getById(cityId))) {
            return CompletableFuture.completedFuture(null);
        }

        CompletableFuture<Void> future = new CompletableFuture<>();
        running = inFlight.putIfAbsent(cityId, future);
        if (running != null) {
            return running; // друго барање штотуку го почна
        }
        lastError.remove(cityId);
        background.run(() -> {
            try {
                refresh(cityId);
                future.complete(null);
            } catch (RuntimeException e) {
                lastError.put(cityId, e.getMessage());
                future.completeExceptionally(e);
            } finally {
                inFlight.remove(cityId, future);
            }
        });
        return future;
    }

    private static boolean isFresh(City city) {
        LocalDateTime fetchedAt = city.getPlacesFetchedAt();
        return fetchedAt != null && fetchedAt.isAfter(LocalDateTime.now().minusDays(CACHE_DAYS));
    }

    private void refresh(Long cityId) {
        City city = cityService.getById(cityId);
        log.info("Fetching places from Overpass for city id={}", cityId);
        long started = System.currentTimeMillis();

        // Бавниот HTTP повик е НАДВОР од трансакцијата, за да не држи врска до базата
        List<OverpassElement> elements = overpass.fetchPlaces(city.getLat(), city.getLon(), SEARCH_RADIUS_METERS);

        int saved = Objects.requireNonNull(tx.execute(status -> {
            City managed = cityService.getById(cityId);
            List<Place> newPlaces = elements.stream()
                    .map(e -> toPlace(managed, e))
                    .filter(Objects::nonNull)
                    .toList();
            places.deleteByCityId(cityId);
            places.saveAll(newPlaces);
            managed.setPlacesFetchedAt(LocalDateTime.now());
            return newPlaces.size();
        }));
        log.info("Saved {} places for city id={} in {} ms", saved, cityId, System.currentTimeMillis() - started);
    }

    /** Го чека преземањето; грешката (на пр. 503 од Overpass) се пренесува непроменета. */
    private static void await(CompletableFuture<Void> future) {
        try {
            future.join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof RuntimeException cause) {
                throw cause;
            }
            throw e;
        }
    }

    /** Претвора OSM објект во Place. Враќа null ако објектот не е корисен. */
    private static Place toPlace(City city, OverpassElement e) {
        Map<String, String> tags = e.tags();
        Double lat = e.latitude();
        Double lon = e.longitude();
        if (tags == null || lat == null || lon == null) {
            return null;
        }
        String name = tags.get("name");
        if (name == null || name.isBlank()) {
            return null;
        }
        return PlaceCategory.fromTags(tags)
                .map(category -> new Place(
                        city, e.type(), e.id(), name, lat, lon, category,
                        tags.containsKey("wikipedia") || tags.containsKey("wikidata"),
                        tags.get("opening_hours")))
                .orElse(null);
    }
}
