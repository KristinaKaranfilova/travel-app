package mk.diplomska.travel.user;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * Безбедност: кои API се јавни, а кои бараат најава.
 *
 * Јавни: регистрација/најава, пребарување градови, знаменитости, популарни дестинации,
 *        преглед на план по линк (за споделување).
 * Со најава: правење план, „мои патувања“, чет-бот, сè останато.
 *
 * Нема сесии на серверот (STATELESS): секое барање носи JWT токен.
 */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // CSRF заштитата е за cookies; ние користиме токен во header, па не е потребна
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/register", "/api/auth/login", "/api/health", "/error").permitAll()
                        .requestMatchers("/api/cities/search", "/api/landmarks", "/api/destinations/featured",
                                "/api/places/details").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/trips/*").permitAll() // линк за споделување
                        .requestMatchers("/api/admin/**").permitAll() // само за развој (внесување извори)
                        // API документација (Swagger UI и OpenAPI JSON)
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .anyRequest().authenticated())
                // Проверка на JWT токенот од header-от "Authorization: Bearer ..."
                .oauth2ResourceServer(oauth -> oauth.jwt(Customizer.withDefaults()));
        return http.build();
    }

    /** BCrypt за хеширање лозинки. */
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** Таен клуч за потпишување на токените (HS256). Од secrets.properties: JWT_SECRET. */
    @Bean
    SecretKey jwtSecretKey(@Value("${app.jwt.secret}") String secret) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("JWT_SECRET мора да има најмалку 32 знаци");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey key) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(key));
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey key) {
        return NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    }
}
