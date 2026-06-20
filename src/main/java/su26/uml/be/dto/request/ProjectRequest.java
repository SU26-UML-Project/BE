package su26.uml.be.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ProjectRequest {
    @NotBlank(message = "PROJECT_NAME_REQUIRED")
    @Size(max = 255, message = "PROJECT_NAME_TOO_LONG")
    String projectName;

    @Size(max = 1000, message = "DESCRIPTION_TOO_LONG")
    String description;

    String projectData;
}
