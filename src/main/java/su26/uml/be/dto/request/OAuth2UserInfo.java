package su26.uml.be.dto.request;

import lombok.*;
import lombok.experimental.FieldDefaults;
import su26.uml.be.enums.UserStatus;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class OAuth2UserInfo {
    String username;
    String email;
    String fullName;
    String googleId;
    String provider;
    String avatarUrl;
    UserStatus status;
}
