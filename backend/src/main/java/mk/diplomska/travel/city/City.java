package mk.diplomska.travel.city;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Град што корисник го избрал. Податоците доаѓаат од Nominatim (OpenStreetMap).
 * south/west/north/east се границите на градот (bounding box).
 */
@Entity
@Table(name = "city")
public class City {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String country;
    private String osmType;
    private Long osmId;

    private double lat;
    private double lon;
    private double south;
    private double west;
    private double north;
    private double east;

    /** Кога последен пат се преземени местата од Overpass. null = никогаш. */
    private LocalDateTime placesFetchedAt;

    protected City() {
        // потребно за JPA
    }

    public City(String name, String country, String osmType, Long osmId,
                double lat, double lon, double south, double west, double north, double east) {
        this.name = name;
        this.country = country;
        this.osmType = osmType;
        this.osmId = osmId;
        this.lat = lat;
        this.lon = lon;
        this.south = south;
        this.west = west;
        this.north = north;
        this.east = east;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getCountry() { return country; }
    public String getOsmType() { return osmType; }
    public Long getOsmId() { return osmId; }
    public double getLat() { return lat; }
    public double getLon() { return lon; }
    public double getSouth() { return south; }
    public double getWest() { return west; }
    public double getNorth() { return north; }
    public double getEast() { return east; }
    public LocalDateTime getPlacesFetchedAt() { return placesFetchedAt; }

    public void setPlacesFetchedAt(LocalDateTime placesFetchedAt) {
        this.placesFetchedAt = placesFetchedAt;
    }
}
