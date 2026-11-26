package mk.diplomska.travel.user;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** Корисник на апликацијата. Се најавува со е-пошта и лозинка. */
@Entity
@Table(name = "app_user")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String email;

    /** BCrypt хеш на лозинката (самата лозинка никогаш не се зачувува). */
    private String passwordHash;

    private String fullName;

    private LocalDateTime createdAt;

    protected AppUser() {
        // потребно за JPA
    }

    public AppUser(String email, String passwordHash, String fullName) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getFullName() { return fullName; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
