package su26.uml.be.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Schema(name = "AuthenticationResponse", description = "Result of a successful login.")
public class AuthenticationResponse {
    @Schema(description = "Signed HS512 JWT. Use as 'Authorization: Bearer <token>'.",
            example = "eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJqb2huZG9lIn0...")
    String token;

    @Schema(description = "True when authentication succeeded.", example = "true")
    boolean authenticated;

    String refreshToken;
}
