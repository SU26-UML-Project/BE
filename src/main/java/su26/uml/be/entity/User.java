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
    @Column(name = "UserID", updatable = false, nullable = false)
    private UUID userID;

    @Column(name = "UserName", nullable = false, unique = true)
    String username;

    @Column(name = "Password", nullable = false)
    String password;

    @Column(name = "FullName", nullable = false, length = 100)
    String fullName;

    @Column(name = "Email", nullable = false, unique = true)
    String email;

    @Column(name = "Phone", nullable = false)
    String phone;

    @ManyToOne
    @JoinColumn(name = "RoleID", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    Role role;

    @Column(name = "Status", nullable = false)
    String status;

    @Column(name = "AvatarUrl", length = 500)
    String avatarUrl;

    @Column(name = "LastActiveAt")
    LocalDateTime lastActiveAt;

    @Column(name = "LastPasswordChangeAt")
    LocalDateTime lastPasswordChangeAt;

    @CreationTimestamp
    @Column(name = "CreatedAt", updatable = false)
    LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "UpdatedAt")
    LocalDateTime updatedAt;

//    // ── NEW: AWS Cognito user identifier ─────────────────────────────────────
//    // Populated by CognitoUserSyncFilter on the first authenticated request.
//    // Used to link Cognito identities to existing local accounts and to prevent
//    // duplicate JIT provisioning when a user's email changes in Cognito.
//    //
//    // DDL note: hibernate.ddl-auto=update will ADD this column automatically on
//    // the next application start — no manual migration required.
//    @Column(name = "CognitoSub", unique = true)
//    String cognitoSub;
}
