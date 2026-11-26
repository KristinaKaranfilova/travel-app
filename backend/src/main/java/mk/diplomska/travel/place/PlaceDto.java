package mk.diplomska.travel.place;

/** Место како што се враќа на frontend-от. */
public record PlaceDto(
        Long id,
        String name,
        double lat,
        double lon,
        PlaceCategory category,
        PlaceKind kind,
        boolean indoor,
        boolean hasWikipedia,
        String openingHours,
        int visitMinutes
) {
    public static PlaceDto from(Place p) {
        return new PlaceDto(p.getId(), p.getName(), p.getLat(), p.getLon(), p.getCategory(),
                p.getKind(), p.isIndoor(), p.isHasWikipedia(), p.getOpeningHours(), p.getVisitMinutes());
    }
}
