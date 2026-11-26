package mk.diplomska.travel.health;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Едноставна проверка дека backend-от работи и дека може да се поврзе со базата.
 * Frontend-от го повикува при старт (Фаза 1).
 */
@RestController
public class HealthController {

    private final JdbcTemplate jdbc;

    public HealthController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/api/health")
    public Map<String, Object> health() {
        String dbVersion = jdbc.queryForObject("SELECT version()", String.class);
        String pgvector = jdbc.queryForObject(
                "SELECT extversion FROM pg_extension WHERE extname = 'vector'", String.class);
        return Map.of(
                "status", "OK",
                "database", dbVersion,
                "pgvector", pgvector
        );
    }
}
