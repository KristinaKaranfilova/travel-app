package mk.diplomska.travel.user;

import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * Издава JWT токени по успешна најава.
 *
 * JWT е потпишан текст што содржи кој е корисникот (sub = id) и до кога важи.
 * Frontend-от го праќа во секое барање (header "Authorization: Bearer ..."),
 * а серверот само го проверува потписот, без да чува сесии.
 */
@Service
public class JwtService {

    static final Duration VALIDITY = Duration.ofDays(7);

    private final JwtEncoder encoder;

    public JwtService(JwtEncoder encoder) {
        this.encoder = encoder;
    }

    public String createToken(AppUser user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("travel-planner")
                .subject(String.valueOf(user.getId()))
                .claim("email", user.getEmail())
                .claim("name", user.getFullName())
                .issuedAt(now)
                .expiresAt(now.plus(VALIDITY))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
