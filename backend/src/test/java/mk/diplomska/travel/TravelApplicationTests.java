package mk.diplomska.travel;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/** Проверува дека целата апликација стартува (бара база од docker-compose). */
@SpringBootTest(properties = {
        "GEMINI_API_KEY=test-key-not-used",
        "JWT_SECRET=test-secret-for-unit-tests-only-0123456789",
        "app.warmup.enabled=false"})
class TravelApplicationTests {

	@Test
	void contextLoads() {
	}

}
