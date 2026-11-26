package mk.diplomska.travel.city;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CityRepository extends JpaRepository<City, Long> {

    Optional<City> findByOsmTypeAndOsmId(String osmType, Long osmId);
}
