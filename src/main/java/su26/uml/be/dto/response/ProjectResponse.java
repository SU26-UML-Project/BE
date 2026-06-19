package su26.uml.be.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Schema(name = "ProjectResponse", description = "UML Project details.")
public class ProjectResponse {
    @Schema(description = "Unique project identifier.")
    UUID id;

    @Schema(description = "Project name.")
    String projectName;

    @Schema(description = "Project description.")
    String description;

    @Schema(description = "Owner ID.")
    UUID userId;

    @Schema(description = "Draw.io XML data.")
    String projectData;

    @Schema(description = "Creation timestamp.")
    LocalDateTime createdAt;

    @Schema(description = "Last update timestamp.")
    LocalDateTime updatedAt;
}
