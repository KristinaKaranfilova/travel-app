package mk.diplomska.travel.place;

import java.util.Map;
import java.util.Optional;

/**
 * Категорија на место и сите правила поврзани со неа, на едно место.
 *
 * За секоја категорија:
 *  - kind:         атракција, ресторан или кафуле
 *  - indoor:       дали е затворен простор (важно за дождливи денови)
 *  - visitMinutes: проценето време на посета
 *  - interest:     на кој интерес на корисникот одговара (null за храна)
 */
public enum PlaceCategory {

    //           kind                  indoor  minutes  interest
    MUSEUM      (PlaceKind.ATTRACTION, true,   90,      Interest.CULTURE),
    GALLERY     (PlaceKind.ATTRACTION, true,   60,      Interest.CULTURE),
    ATTRACTION  (PlaceKind.ATTRACTION, false,  45,      Interest.CULTURE),
    CASTLE      (PlaceKind.ATTRACTION, false,  60,      Interest.HISTORY),
    MONUMENT    (PlaceKind.ATTRACTION, false,  15,      Interest.HISTORY),
    RUINS       (PlaceKind.ATTRACTION, false,  45,      Interest.HISTORY),
    PARK        (PlaceKind.ATTRACTION, false,  60,      Interest.NATURE),
    VIEWPOINT   (PlaceKind.ATTRACTION, false,  20,      Interest.NATURE),
    CHURCH      (PlaceKind.ATTRACTION, true,   30,      Interest.RELIGION),
    RESTAURANT  (PlaceKind.RESTAURANT, true,   60,      null),
    CAFE        (PlaceKind.CAFE,       true,   30,      null);

    private final PlaceKind kind;
    private final boolean indoor;
    private final int visitMinutes;
    private final Interest interest;

    PlaceCategory(PlaceKind kind, boolean indoor, int visitMinutes, Interest interest) {
        this.kind = kind;
        this.indoor = indoor;
        this.visitMinutes = visitMinutes;
        this.interest = interest;
    }

    public PlaceKind kind() { return kind; }
    public boolean indoor() { return indoor; }
    public int visitMinutes() { return visitMinutes; }
    public Interest interest() { return interest; }

    /**
     * Ја одредува категоријата од OSM таговите на објектот.
     * Редоследот е важен: поконкретните тагови се проверуваат први
     * (на пр. место со tourism=attraction и historic=castle е CASTLE).
     */
    public static Optional<PlaceCategory> fromTags(Map<String, String> tags) {
        if (tags == null) {
            return Optional.empty();
        }
        String amenity = tags.getOrDefault("amenity", "");
        String tourism = tags.getOrDefault("tourism", "");
        String historic = tags.getOrDefault("historic", "");
        String leisure = tags.getOrDefault("leisure", "");

        if (amenity.equals("restaurant")) return Optional.of(RESTAURANT);
        if (amenity.equals("cafe")) return Optional.of(CAFE);
        if (tourism.equals("museum")) return Optional.of(MUSEUM);
        if (tourism.equals("gallery")) return Optional.of(GALLERY);
        if (tourism.equals("viewpoint")) return Optional.of(VIEWPOINT);
        if (historic.equals("castle") || historic.equals("fort")) return Optional.of(CASTLE);
        if (historic.equals("ruins") || historic.equals("archaeological_site")) return Optional.of(RUINS);
        if (historic.equals("monument") || historic.equals("city_gate")) return Optional.of(MONUMENT);
        if (amenity.equals("place_of_worship")) return Optional.of(CHURCH);
        if (leisure.equals("park")) return Optional.of(PARK);
        if (tourism.equals("attraction")) return Optional.of(ATTRACTION);
        return Optional.empty();
    }
}
