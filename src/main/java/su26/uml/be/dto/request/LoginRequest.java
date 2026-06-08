package su26.uml.be.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Schema(name = "LoginRequest", description = "Credentials used to obtain a JWT.")
public class LoginRequest {
    @NotBlank(message = "USERNAME_REQUIRED")
    @Schema(description = "Account username.", example = "johndoe", requiredMode = Schema.RequiredMode.REQUIRED)
    String username;

    @NotBlank(message = "PASSWORD_REQUIRED")
    @Schema(description = "Account password.", example = "Passw0rd", requiredMode = Schema.RequiredMode.REQUIRED)
    String password;
}
