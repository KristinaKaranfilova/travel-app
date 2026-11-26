package mk.diplomska.travel.place;

import jakarta.persistence.*;
import mk.diplomska.travel.city.City;

/** Место од OpenStreetMap: атракција, ресторан или кафуле. */
@Entity
@Table(name = "place")
public class Place {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "city_id")
    private City city;

    private String osmType;
    private Long osmId;
    private String name;
    private double lat;
    private double lon;

    @Enumerated(EnumType.STRING)
    private PlaceCategory category;

    @Enumerated(EnumType.STRING)
    private PlaceKind kind;

    private boolean indoor;

    /** Дали има Wikipedia/Wikidata запис. Го користиме како мерка за популарност. */
    private boolean hasWikipedia;

    /** Работно време во OSM формат, на пр. "Tu-Su 09:00-17:00". Може да е null. */
    private String openingHours;

    private int visitMinutes;

    protected Place() {
        // потребно за JPA
    }

    public Place(City city, String osmType, Long osmId, String name, double lat, double lon,
                 PlaceCategory category, boolean hasWikipedia, String openingHours) {
        this.city = city;
        this.osmType = osmType;
        this.osmId = osmId;
        this.name = name;
        this.lat = lat;
        this.lon = lon;
        this.category = category;
        this.kind = category.kind();
        this.indoor = category.indoor();
        this.visitMinutes = category.visitMinutes();
        this.hasWikipedia = hasWikipedia;
        this.openingHours = openingHours;
    }

    public Long getId() { return id; }
    public City getCity() { return city; }
    public String getOsmType() { return osmType; }
    public Long getOsmId() { return osmId; }
    public String getName() { return name; }
    public double getLat() { return lat; }
    public double getLon() { return lon; }
    public PlaceCategory getCategory() { return category; }
    public PlaceKind getKind() { return kind; }
    public boolean isIndoor() { return indoor; }
    public boolean isHasWikipedia() { return hasWikipedia; }
    public String getOpeningHours() { return openingHours; }
    public int getVisitMinutes() { return visitMinutes; }
}
