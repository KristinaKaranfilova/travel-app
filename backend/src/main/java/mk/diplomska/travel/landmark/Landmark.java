package mk.diplomska.travel.landmark;

/**
 * Знаменитост со слика, за галеријата при избор на град.
 *
 * @param imageUrl   слика (Wikimedia Commons или Wikipedia), со ширина до 640px
 * @param pageUrl    страница на изворот (за наведување на авторството на сликата)
 * @param lat        географска ширина, за означување на мапата
 * @param lon        географска должина
 * @param wikidataId идентификатор на Wikidata (на пр. Q10285), ако е познат
 */
public record Landmark(String name, String description, String imageUrl, String pageUrl,
                       double lat, double lon, String wikidataId) {
}
