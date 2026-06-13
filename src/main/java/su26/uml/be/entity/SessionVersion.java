package su26.uml.be.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "session_versions")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SessionVersion extends BaseEntity {

    @Column(name = "version_number", nullable = false)
    Integer versionNumber;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "content_snapshot")
    String contentSnapshot;

    @ManyToOne
    @JoinColumn(name = "session_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    AnalysisSession session;
}
