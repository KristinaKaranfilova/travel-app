package mk.diplomska.travel.trip;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Зачувано патување. Самиот план е JSON текст во колоната plan (тип jsonb),
 * па не се потребни посебни табели за денови и ставки.
 *
 * id е UUID (случаен), за линкот /trip/{id} да не може да се погоди.
 */
@Entity
@Table(name = "trip")
public class Trip {

    @Id
    private UUID id;

    /** Сопственик на патувањето (id од app_user). */
    private Long userId;

    private Long cityId;
    private LocalDate startDate;
    private LocalDate endDate;
    private String pace;

    @JdbcTypeCode(SqlTypes.ARRAY)
    private String[] interests;

    @JdbcTypeCode(SqlTypes.JSON)
    private String plan;

    private LocalDateTime createdAt;

    protected Trip() {
        // потребно за JPA
    }

    public Trip(Long userId, Long cityId, LocalDate startDate, LocalDate endDate, String pace,
                String[] interests, String plan) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.cityId = cityId;
        this.startDate = startDate;
        this.endDate = endDate;
        this.pace = pace;
        this.interests = interests;
        this.plan = plan;
        this.createdAt = LocalDateTime.now();
    }

    public UUID getId() { return id; }
    public Long getUserId() { return userId; }
    public Long getCityId() { return cityId; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public String getPace() { return pace; }
    public String[] getInterests() { return interests; }
    public String getPlan() { return plan; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
