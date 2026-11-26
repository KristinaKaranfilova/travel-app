package mk.diplomska.travel.city;

/** Град како што се враќа на frontend-от. */
public record CityDto(Long id, String name, String country, double lat, double lon) {

    public static CityDto from(City city) {
        return new CityDto(city.getId(), city.getName(), city.getCountry(), city.getLat(), city.getLon());
    }
}
