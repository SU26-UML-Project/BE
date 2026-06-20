package su26.uml.be.service.Impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import su26.uml.be.dto.request.UserRegisterRequest;
import su26.uml.be.enums.UserStatus;
import su26.uml.be.dto.response.ApiResponse;
import su26.uml.be.dto.response.UserResponse;
import su26.uml.be.entity.Role;
import su26.uml.be.entity.User;
import su26.uml.be.exception.AppException;
import su26.uml.be.exception.ErrorCode;
import su26.uml.be.mapper.UserMapper;
import su26.uml.be.repository.RoleRepository;
import su26.uml.be.repository.UserRepository;
import su26.uml.be.service.AdminService;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
@Transactional
public class AdminServiceImpl implements AdminService {

    UserRepository userRepository;
    RoleRepository roleRepository;
    UserMapper userMapper;
    PasswordEncoder passwordEncoder;

    @Override
    public ApiResponse<UserResponse> registerAdmin(UserRegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail()))
            throw new AppException(ErrorCode.EMAIL_EXISTED);

        User user = userMapper.toUser(request);
        user.setPassword(passwordEncoder.encode(request.getPassword()));

        Role role = roleRepository.findByRoleName("ADMIN")
                .orElseThrow(() -> new AppException(ErrorCode.ROLE_NOT_FOUND));
        user.setRole(role);

        user.setStatus(UserStatus.ACTIVE);
        User savedUser = userRepository.save(user);

        return ApiResponse.success("Tạo tài khoản Admin thành công", userMapper.toUserResponse(savedUser));
    }

    @Override
    @Transactional
    public ApiResponse<Void> toggleUserStatus(UUID userId, String currentUserEmail) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        User currentUser = userRepository.findByEmail(currentUserEmail)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        if (user.getId().equals(currentUser.getId())) {
            throw new AppException(ErrorCode.DELETE_SELF_INVALID);
        }

        if ("ADMIN".equalsIgnoreCase(user.getRole().getRoleName())) {
            throw new AppException(ErrorCode.DELETE_OTHER_ADMIN_INVALID);
        }

        String message;
        if (user.getStatus() == UserStatus.ACTIVE) {
            user.setStatus(UserStatus.LOCKED);
            message = "Đã khóa tài khoản thành công";
        } else {
            user.setStatus(UserStatus.ACTIVE);
            message = "Đã mở khóa tài khoản thành công";
        }

        userRepository.save(user);

        return ApiResponse.success(message, null);
    }
}
