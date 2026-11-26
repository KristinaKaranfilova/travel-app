package mk.diplomska.travel.user;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    public record RegisterRequest(
            @NotBlank(message = "Внеси е-пошта") @Email(message = "Невалидна е-пошта") String email,
            @NotBlank(message = "Внеси лозинка")
            @Size(min = 8, max = 100, message = "Лозинката мора да има најмалку 8 знаци") String password,
            @NotBlank(message = "Внеси име") @Size(max = 100, message = "Името е предолго") String fullName) {
    }

    public record LoginRequest(@NotBlank(message = "Внеси е-пошта") String email,
                               @NotBlank(message = "Внеси лозинка") String password) {
    }

    /** POST /api/auth/register → {token, user} */
    @PostMapping("/register")
    public AuthService.AuthResult register(@Valid @RequestBody RegisterRequest r) {
        return authService.register(r.email(), r.password(), r.fullName());
    }

    /** POST /api/auth/login → {token, user} */
    @PostMapping("/login")
    public AuthService.AuthResult login(@Valid @RequestBody LoginRequest r) {
        return authService.login(r.email(), r.password());
    }

    /** GET /api/auth/me → тековниот корисник (според токенот) */
    @GetMapping("/me")
    public AuthService.UserDto me(@AuthenticationPrincipal Jwt jwt) {
        return authService.get(Long.valueOf(jwt.getSubject()));
    }
}
