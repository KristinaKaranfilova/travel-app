package mk.diplomska.travel.trip;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TripRepository extends JpaRepository<Trip, UUID> {

    /** Патувањата на корисникот, најновите први. */
    List<Trip> findByUserIdOrderByCreatedAtDesc(Long userId);
}
