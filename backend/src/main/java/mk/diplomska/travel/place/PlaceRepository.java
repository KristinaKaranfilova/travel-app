package mk.diplomska.travel.place;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PlaceRepository extends JpaRepository<Place, Long> {

    List<Place> findByCityId(Long cityId);

    List<Place> findByCityIdAndKind(Long cityId, PlaceKind kind);

    long countByCityIdAndKind(Long cityId, PlaceKind kind);

    /** Ги брише сите места на градот со едно SQL барање (при освежување на кешот). */
    @Modifying
    @Query("delete from Place p where p.city.id = :cityId")
    void deleteByCityId(@Param("cityId") Long cityId);
}
