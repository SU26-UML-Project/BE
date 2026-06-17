package su26.uml.be.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import su26.uml.be.enums.UserStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class User extends BaseEntity {

    @Column(name = "user_name", nullable = false, unique = true)
    String username;

    @Column(name = "password")
    String password;

    @Column(name = "full_name", nullable = false, length = 100)
    String fullName;

    @Column(name = "email", nullable = false, unique = true)
    String email;

    @Column(name = "phone")
    String phone;

    @Column(name = "provider", length = 20)
    String provider;

    @Column(name = "google_id", unique = true)
    String googleId;

    @ManyToOne
    @JoinColumn(name = "role_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    Role role;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    UserStatus status;

    @Column(name = "deletion_date")
    LocalDateTime deletionDate;

    @Column(name = "avatar_url", length = 500)
String avatarUrl;

    @Column(name = "dob")
    LocalDate dob;

    @Column(name = "last_active_at")
    LocalDateTime lastActiveAt;

    @Column(name = "last_password_change_at")
    LocalDateTime lastPasswordChangeAt;

//    // ── NEW: AWS Cognito user identifier ─────────────────────────────────────
//    // Populated by CognitoUserSyncFilter on the first authenticated request.
//    // Used to link Cognito identities to existing local accounts and to prevent
//    // duplicate JIT provisioning when a user's email changes in Cognito.
//    //
//    // DDL note: hibernate.ddl-auto=update will ADD this column automatically on
//    // the next application start — no manual migration required.
//    @Column(name = "cognito_sub", unique = true)
//    String cognitoSub;
}
