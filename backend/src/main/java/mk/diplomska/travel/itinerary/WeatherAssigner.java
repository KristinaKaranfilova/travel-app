package mk.diplomska.travel.itinerary;

import mk.diplomska.travel.place.Place;
import mk.diplomska.travel.weather.DayWeather;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/**
 * Одлучува која група места (од k-means) оди на кој ден, според прогнозата.
 *
 * Идеја: деновите со најголема веројатност за дожд ги добиваат групите
 * со најмногу затворени места (музеи, цркви, галерии).
 *
 *  1. Деновите се подредуваат по веројатност за дожд (од најголема).
 *  2. Групите се подредуваат по удел на затворени места (од најголем).
 *  3. Првиот ден од (1) ја добива првата група од (2), итн.
 *
 * Ако нема прогноза (патувањето е повеќе од 16 дена однапред),
 * групите остануваат по редот по кој ги дал k-means.
 */
@Component
public class WeatherAssigner {

    /** Враќа листа каде елементот i е групата за денот dates.get(i). */
    public List<List<Place>> assign(List<List<Place>> clusters, List<LocalDate> dates,
                                    Map<LocalDate, DayWeather> weather) {
        if (weather.isEmpty()) {
            return clusters;
        }

        List<Integer> daysByRain = IntStream.range(0, dates.size()).boxed()
                .sorted(Comparator.comparingInt((Integer d) -> rainChance(weather, dates.get(d))).reversed())
                .toList();

        List<List<Place>> clustersByIndoor = clusters.stream()
                .sorted(Comparator.comparingDouble(WeatherAssigner::indoorRatio).reversed())
                .toList();

        List<List<Place>> result = new ArrayList<>(clusters); // само за големина, ќе се препише
        for (int i = 0; i < daysByRain.size(); i++) {
            result.set(daysByRain.get(i), clustersByIndoor.get(i));
        }
        return result;
    }

    private static int rainChance(Map<LocalDate, DayWeather> weather, LocalDate date) {
        DayWeather w = weather.get(date);
        return w == null ? 0 : w.rainChance();
    }

    /** Удел на затворени места во групата (0.0 до 1.0). */
    static double indoorRatio(List<Place> cluster) {
        if (cluster.isEmpty()) {
            return 0;
        }
        long indoor = cluster.stream().filter(Place::isIndoor).count();
        return (double) indoor / cluster.size();
    }
}
