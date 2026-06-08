package su26.uml.be.service.Impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import su26.uml.be.entity.Role;
import su26.uml.be.entity.User;
import su26.uml.be.exception.AppException;
import su26.uml.be.exception.ErrorCode;
import su26.uml.be.repository.RoleRepository;
import su26.uml.be.repository.UserRepository;

import su26.uml.be.config.security.CustomOAuth2User;

import java.util.Collections;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    UserRepository userRepository;
    RoleRepository roleRepository;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);
        try {
            return processOAuth2User(oAuth2User);
        } catch (AppException e) {
            log.warn("OAuth2 xử lý thất bại: {}", e.getErrorCode().getMessage());
            throw new OAuth2AuthenticationException(e.getErrorCode().getMessage());
        } catch (Exception e) {
            log.error("Lỗi không xác định khi xử lý OAuth2 user", e);
            throw new OAuth2AuthenticationException(ErrorCode.OAUTH2_PROCESSING_ERROR.getMessage());
        }
    }

    private OAuth2User processOAuth2User(OAuth2User oAuth2User) {
        Map<String, Object> attributes = oAuth2User.getAttributes();

        String email = (String) attributes.get("email");
        Boolean emailVerified = (Boolean) attributes.get("email_verified");

        if (email == null || Boolean.FALSE.equals(emailVerified)) {
            throw new AppException(ErrorCode.OAUTH2_EMAIL_NOT_VERIFIED);
        }

        String googleId = (String) attributes.get("sub");
        String name = (String) attributes.get("name");
        String picture = (String) attributes.get("picture");

        User user = userRepository.findByEmail(email)
                .map(existing -> linkGoogleAccount(existing, googleId, picture))
                .orElseGet(() -> createGoogleUser(email, googleId, name, picture));

        return new CustomOAuth2User(Collections.emptyList(), attributes, "email", user);
    }

    private User linkGoogleAccount(User user, String googleId, String picture) {
        boolean updated = false;

        if (user.getGoogleId() == null) {
            user.setGoogleId(googleId);
            updated = true;
        }
        if (!"GOOGLE".equals(user.getProvider())) {
            user.setProvider("GOOGLE");
            updated = true;
        }
        if (picture != null && user.getAvatarUrl() == null) {
            user.setAvatarUrl(picture);
            updated = true;
        }

        return updated ? userRepository.save(user) : user;
    }

    private User createGoogleUser(String email, String googleId, String name, String picture) {
        Role defaultRole = roleRepository.findByRoleName("USER")
                .orElseGet(() -> roleRepository.save(
                        Role.builder()
                                .roleName("USER")
                                .description("Người dùng thông thường")
                                .build()
                ));

        String baseUsername = email.split("@")[0];
        String username = userRepository.existsByUsername(baseUsername)
                ? baseUsername + "_" + googleId.substring(0, 6)
                : baseUsername;

        User newUser = User.builder()
                .username(username)
                .email(email)
                .fullName(name != null ? name : baseUsername)
                .googleId(googleId)
                .provider("GOOGLE")
                .avatarUrl(picture)
                .status("ACTIVE")
                .role(defaultRole)
                .build();

        User saved = userRepository.save(newUser);
        log.info("Tạo user mới từ Google OAuth2: {}", email);
        return saved;
    }
}
