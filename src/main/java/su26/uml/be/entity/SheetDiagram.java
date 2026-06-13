package su26.uml.be.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "sheet_diagrams")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SheetDiagram extends BaseEntity {

    @Column(name = "title")
    String title;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "diagram_data")
    String diagramData;

    @ManyToOne
    @JoinColumn(name = "sheet_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    Sheet sheet;

    @ManyToOne
    @JoinColumn(name = "diagram_type_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    DiagramType diagramType;
}
