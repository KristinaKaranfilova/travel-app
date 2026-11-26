package mk.diplomska.travel.weather;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Дневна временска прогноза од Open-Meteo (бесплатно, без клуч).
 *
 * Прогнозата е достапна само до 16 дена однапред. За подоцнежни датуми
 * се враќа празна мапа и планот се прави без прилагодување на времето.
 */
@Component
public class OpenMeteoClient {

    private static final Logger log = LoggerFactory.getLogger(OpenMeteoClient.class);

    /** Последниот ден за кој Open-Meteo дава прогноза е денес + 15. */
    static final int MAX_FORECAST_DAYS_AHEAD = 15;

    private final RestClient http;

    public OpenMeteoClient(RestClient.Builder builder) {
        // Планот може да се направи и без прогноза, па не чекаме долго
        SimpleClientHttpRequestFactory timeouts = new SimpleClientHttpRequestFactory();
        timeouts.setConnectTimeout(Duration.ofSeconds(4));
        timeouts.setReadTimeout(Duration.ofSeconds(8));
        this.http = builder.requestFactory(timeouts).baseUrl("https://api.open-meteo.com").build();
    }

    public Map<LocalDate, DayWeather> forecast(double lat, double lon, LocalDate start, LocalDate end) {
        // Го земаме само делот од патувањето што е во рамките на прогнозата
        LocalDate today = LocalDate.now();
        LocalDate from = start.isBefore(today) ? today : start;
        LocalDate to = end.isAfter(today.plusDays(MAX_FORECAST_DAYS_AHEAD))
                ? today.plusDays(MAX_FORECAST_DAYS_AHEAD) : end;
        if (from.isAfter(to)) {
            return Map.of(); // патувањето е предалеку во иднината
        }

        try {
            Response response = http.get()
                    .uri(uri -> uri.path("/v1/forecast")
                            .queryParam("latitude", lat)
                            .queryParam("longitude", lon)
                            .queryParam("daily", "weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max")
                            .queryParam("timezone", "auto")
                            .queryParam("start_date", from)
                            .queryParam("end_date", to)
                            .build())
                    .retrieve()
                    .body(Response.class);
            return toMap(response);
        } catch (RestClientException e) {
            // Без прогноза планот сепак може да се направи
            log.warn("Open-Meteo request failed: {}", e.getMessage());
            return Map.of();
        }
    }

    private static Map<LocalDate, DayWeather> toMap(Response response) {
        Map<LocalDate, DayWeather> result = new HashMap<>();
        if (response == null || response.daily() == null || response.daily().time() == null) {
            return result;
        }
        Daily d = response.daily();
        for (int i = 0; i < d.time().size(); i++) {
            LocalDate date = LocalDate.parse(d.time().get(i));
            result.put(date, new DayWeather(date,
                    get(d.weatherCode(), i), get(d.tempMax(), i), get(d.tempMin(), i),
                    get(d.precipitationProbability(), i)));
        }
        return result;
    }

    private static <T> T get(List<T> list, int i) {
        return list == null || i >= list.size() ? null : list.get(i);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Response(Daily daily) {
    }

    /** Open-Meteo враќа низи: time[i] е датумот, а останатите низи на индекс i се вредностите за тој ден. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Daily(
            List<String> time,
            @JsonProperty("weather_code") List<Integer> weatherCode,
            @JsonProperty("temperature_2m_max") List<Double> tempMax,
            @JsonProperty("temperature_2m_min") List<Double> tempMin,
            @JsonProperty("precipitation_probability_max") List<Integer> precipitationProbability
    ) {
    }
}
