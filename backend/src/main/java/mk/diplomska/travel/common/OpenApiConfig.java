package mk.diplomska.travel.common;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * API документација (springdoc). Swagger UI: http://localhost:8080/swagger-ui.html
 *
 * Копчето „Authorize“ прима JWT токен (од /api/auth/login), за да може
 * да се пробаат и заштитените API директно од Swagger UI.
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER = "bearerAuth";

    @Bean
    OpenAPI travelOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Патоказ - Travel Planner API")
                        .version("1.0")
                        .description("Градови, места, план за патување, RAG чет-бот, знаменитости и најава (JWT)."))
                .components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
}
