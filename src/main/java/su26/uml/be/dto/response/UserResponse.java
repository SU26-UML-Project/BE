package su26.uml.be.dto.response;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;

import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserResponse {
    UUID userID;
    String username;
    String fullName;
    String email;
    String phone;
    String password;
    String status;
    String avatarUrl;
    LocalDateTime createdAt;
    LocalDateTime lastPasswordChangeAt;
    RoleResponse role;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class RoleResponse {
        UUID roleID;
        String roleName;
        String description;
    }
}