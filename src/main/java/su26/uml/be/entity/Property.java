package su26.uml.be.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Entity
@Table(name = "properties")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Property extends BaseEntity {

    @Column(name = "property_name", nullable = false)
    String propertyName;

    @Column(name = "property_value", columnDefinition = "TEXT")
    String propertyValue;

    @ManyToOne
    @JoinColumn(name = "symbol_diagram_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    SymbolDiagram symbolDiagram;
}
