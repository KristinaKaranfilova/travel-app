package mk.diplomska.travel.user;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Регистрација и најава. */
@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public record AuthResult(String token, UserDto user) {
    }

    public record UserDto(Long id, String email, String fullName) {
        static UserDto from(AppUser u) {
            return new UserDto(u.getId(), u.getEmail(), u.getFullName());
        }
    }

    @Transactional
    public AuthResult register(String email, String password, String fullName) {
        String normalized = email.trim().toLowerCase();
        if (users.existsByEmailIgnoreCase(normalized)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Веќе постои корисник со оваа е-пошта");
        }
        // BCrypt: еднонасочна функција со „сол“, па истата лозинка дава различен хеш секој пат
        AppUser user = users.save(new AppUser(normalized, passwordEncoder.encode(password), fullName.trim()));
        return new AuthResult(jwtService.createToken(user), UserDto.from(user));
    }

    public AuthResult login(String email, String password) {
        AppUser user = users.findByEmailIgnoreCase(email.trim())
                .filter(u -> passwordEncoder.matches(password, u.getPasswordHash()))
                // иста порака за непостоечка е-пошта и за погрешна лозинка (не откриваме кои е-пошти постојат)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Погрешна е-пошта или лозинка"));
        return new AuthResult(jwtService.createToken(user), UserDto.from(user));
    }

    public UserDto get(Long userId) {
        return users.findById(userId)
                .map(UserDto::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Корисникот не постои"));
    }
}
