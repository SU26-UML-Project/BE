package su26.uml.be.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Entity
@Table(name = "sheets")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Sheet extends BaseEntity {

    @Column(name = "name", nullable = false)
    String name;

    @Column(name = "order_index")
    Integer orderIndex;

    @Column(name = "diagram_data", columnDefinition = "TEXT")
    String diagramData;

    @ManyToOne
    @JoinColumn(name = "project_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    Project project;
}
