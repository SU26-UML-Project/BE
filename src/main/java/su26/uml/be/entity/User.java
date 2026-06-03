package su26.uml.be.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "users")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class User {
    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "user_id", updatable = false, nullable = false)
    UUID userID;

    @Column(name = "user_name", nullable = false, unique = true)
    String username;

    @Column(name = "password", nullable = false)
    String password;

    @Column(name = "full_name", nullable = false, length = 100)
    String fullName;

    @Column(name = "email", nullable = false, unique = true)
    String email;

    @Column(name = "phone", nullable = false)
    String phone;

    @ManyToOne
    @JoinColumn(name = "role_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    Role role;

    @Column(name = "status", nullable = false)
    String status;

    @Column(name = "avatar_url", length = 500)
    String avatarUrl;

    @Column(name = "last_active_at")
    LocalDateTime lastActiveAt;

    @Column(name = "last_password_change_at")
    LocalDateTime lastPasswordChangeAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    LocalDateTime updatedAt;

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
