package su26.uml.be.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.UuidGenerator;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "roles")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Role {
    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "RoleID")
    UUID roleID;

    @Column(name = "RoleName", nullable = false, unique = true)
    String roleName;

    @Column(name = "Description", length = 1000)
    String description;

    @OneToMany(mappedBy = "role", cascade = CascadeType.ALL, orphanRemoval = true)
    List<User> users = new ArrayList<>();
}
