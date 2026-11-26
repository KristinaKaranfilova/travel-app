package mk.diplomska.travel.landmark;

import mk.diplomska.travel.common.BackgroundTasks;
import mk.diplomska.travel.place.PlaceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * „Загревање“ на кешот при старт, во позадина: сликите за популарните дестинации,
 * нивните знаменитости и местата. Така првиот корисник не чека на Wikidata/Overpass
 * за градовите што најчесто се избираат.
 *
 * Се исклучува со app.warmup.enabled=false (во тестовите и евалуацијата).
 */
@Component
@ConditionalOnProperty(name = "app.warmup.enabled", havingValue = "true", matchIfMissing = true)
public class CacheWarmup {

    private static final Logger log = LoggerFactory.getLogger(CacheWarmup.class);

    private final DestinationService destinations;
    private final LandmarkService landmarks;
    private final PlaceService places;
    private final BackgroundTasks background;

    public CacheWarmup(DestinationService destinations, LandmarkService landmarks, PlaceService places,
                       BackgroundTasks background) {
        this.destinations = destinations;
        this.landmarks = landmarks;
        this.places = places;
        this.background = background;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void warmUp() {
        background.run(() -> {
            long started = System.currentTimeMillis();
            int warmed = 0;
            for (DestinationService.Destination d : destinations.featured()) {
                if (d.cityId() == null || d.lat() == null || d.lon() == null) {
                    continue;
                }
                try {
                    landmarks.getLandmarks(d.lat(), d.lon());
                    places.prefetch(d.cityId());
                    warmed++;
                } catch (RuntimeException e) {
                    log.debug("Warm-up skipped {}: {}", d.name(), e.getMessage());
                }
            }
            log.info("Cache warm-up: {} destinations in {} ms", warmed, System.currentTimeMillis() - started);
        });
    }
}
