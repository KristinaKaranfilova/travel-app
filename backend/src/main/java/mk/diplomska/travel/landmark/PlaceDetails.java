package mk.diplomska.travel.landmark;

/**
 * Краток опис на место.
 *
 * @param found      false ако не е пронајден сигурен запис за ова место
 * @param language   јазик на описот: "mk" или "en"
 * @param sourceName извор на описот (Wikipedia или Wikidata)
 */
public record PlaceDetails(
        boolean found,
        String name,
        String description,
        String language,
        String imageUrl,
        String sourceName,
        String sourceUrl
) {
    public static PlaceDetails notFound(String name) {
        return new PlaceDetails(false, name, null, null, null, null, null);
    }
}
