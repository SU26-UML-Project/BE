package su26.uml.be.dto.response;

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
@Schema(name = "MeResponse", description = "Identity of the currently authenticated user.")
public class MeResponse {
    @Schema(description = "Unique user identifier.", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    UUID id;

    @Schema(description = "Email address.", example = "john.doe@gmail.com")
    String email;

    @Schema(description = "Role name assigned to the user.", example = "USER")
    String role;
}
