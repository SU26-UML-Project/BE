package su26.uml.be.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Entity
@Table(name = "diagram_types")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class DiagramType extends BaseEntity {

    @Column(name = "type_name", nullable = false, unique = true)
    String typeName;

    @Column(name = "description", columnDefinition = "TEXT")
    String description;
}
