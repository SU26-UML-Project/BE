package su26.uml.be.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "sheet_versions")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SheetVersion extends BaseEntity {

    @Column(name = "version_number", nullable = false)
    Integer versionNumber;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "sheet_snapshot")
    String sheetSnapshot;

    @ManyToOne
    @JoinColumn(name = "sheet_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    Sheet sheet;
}
