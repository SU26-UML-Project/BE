package su26.uml.be.service.Impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import su26.uml.be.dto.request.*;
import su26.uml.be.enums.UserStatus;
import su26.uml.be.dto.response.ApiResponse;
import su26.uml.be.dto.response.DeleteAccountResponse;
import su26.uml.be.dto.response.MeResponse;
import su26.uml.be.dto.response.UserResponse;

import java.time.LocalDateTime;
import su26.uml.be.entity.Role;
import su26.uml.be.entity.User;
import su26.uml.be.exception.AppException;
import su26.uml.be.exception.ErrorCode;
import su26.uml.be.mapper.UserMapper;
import su26.uml.be.repository.RoleRepository;
import su26.uml.be.repository.UserRepository;
import su26.uml.be.service.EmailService;
import su26.uml.be.service.OtpService;
import su26.uml.be.service.RefreshTokenService;
import su26.uml.be.service.UserService;


import java.security.SecureRandom;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
@Transactional
public class UserServiceImpl implements UserService {
    UserRepository userRepository;
    RoleRepository roleRepository;

    EmailService emailService;
    OtpService otpService;
    RefreshTokenService refreshTokenService;

    UserMapper userMapper;

    PasswordEncoder passwordEncoder;

    static SecureRandom SECURE_RANDOM = new SecureRandom();

    @Override
    public ApiResponse<UserResponse> registerUser(UserRegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail()))
            throw new AppException(ErrorCode.EMAIL_EXISTED);

        User user = userMapper.toUser(request);
        user.setPassword(passwordEncoder.encode(request.getPassword()));

        Role role = roleRepository.findByRoleName("USER")
                .orElseThrow(() -> new AppException(ErrorCode.DEFAULT_ROLE_NOT_FOUND));
        user.setRole(role);

        user.setStatus(UserStatus.ACTIVE);
        User savedUser = userRepository.save(user);

        UserResponse userResponse = userMapper.toUserResponse(savedUser);
//        resolveAvatar(userResponse);
        return ApiResponse.success("Đăng ký tài khoản thành công", userResponse);
    }

    @Override
    public ApiResponse<UserResponse> updateMe(String email, UpdateUserRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        userMapper.updateUser(request, user);
        User savedUser = userRepository.save(user);

        return ApiResponse.success("Cập nhật thông tin thành công", userMapper.toUserResponse(savedUser));
    }

    @Override
    public ApiResponse<DeleteAccountResponse> requestDeleteAccount(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        if (user.getStatus() == UserStatus.PENDING_DELETE)
            throw new AppException(ErrorCode.ACCOUNT_ALREADY_PENDING_DELETE);

        if (user.getStatus() == UserStatus.LOCKED)
            throw new AppException(ErrorCode.USER_INACTIVE);

        LocalDateTime deletionDate = LocalDateTime.now().plusMinutes(5);
        user.setStatus(UserStatus.PENDING_DELETE);
        user.setDeletionDate(deletionDate);
        User savedUser = userRepository.save(user);

        return ApiResponse.success("Yêu cầu xóa tài khoản đã được ghi nhận",
                userMapper.toDeleteAccountResponse(savedUser, 30L,
                        "Tài khoản sẽ bị xóa vĩnh viễn sau 30 ngày. Bạn có thể khôi phục trước thời hạn này."));
    }

    @Override
    public ApiResponse<DeleteAccountResponse> restoreAccount(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        if (user.getStatus() != UserStatus.PENDING_DELETE)
            throw new AppException(ErrorCode.ACCOUNT_NOT_PENDING_DELETE);

        user.setStatus(UserStatus.ACTIVE);
        user.setDeletionDate(null);
        User savedUser = userRepository.save(user);

        return ApiResponse.success("Tài khoản đã được khôi phục thành công",
                userMapper.toDeleteAccountResponse(savedUser, null,
                        "Tài khoản của bạn đã được khôi phục và hoạt động bình thường."));
    }

    @Override
    @Transactional(readOnly = true)
    public ApiResponse<List<UserResponse>> getAllUsers() {
        List<User> users = userRepository.findAll();

        if (users.isEmpty()) {
            throw new AppException(ErrorCode.USER_LIST_EMPTY);
        }

        List<UserResponse> userResponses = userMapper.toUserResponseList(users);
//        resolveAvatar(userResponses);
        return ApiResponse.success("Lấy danh sách người dùng thành công", userResponses);
    }

    @Override
    @Transactional(readOnly = true)
    public ApiResponse<UserResponse> getUserById(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        UserResponse userResponse = userMapper.toUserResponse(user);
//        resolveAvatar(userResponse);
        return ApiResponse.success("Lấy thông tin Người dùng thành công", userResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public ApiResponse<MeResponse> getCurrentUser(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        MeResponse meResponse = userMapper.toMeResponse(user);

        return ApiResponse.success("Lấy thông tin người dùng hiện tại thành công", meResponse);
    }

    @Override
    public ApiResponse<String> forgotPassword(ForgotPasswordRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new AppException(ErrorCode.EMAIL_NOT_FOUND));

        String otpCode = generateOtp();

        // Lưu OTP (đã hash) trong Redis kèm TTL — thay cho bảng password_reset_tokens.
        otpService.storeOtp(user.getEmail(), otpCode);
        emailService.sendForgotPasswordOtpEmail(user.getEmail(), otpCode, user.getFullName());
        return ApiResponse.success("Mã OTP đã được gửi đến email của bạn", null);
    }

    @Override
    public ApiResponse<String> verifyOtp(VerifyOtpRequest request) {
        userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new AppException(ErrorCode.EMAIL_NOT_FOUND));

        // Ném AppException nếu OTP sai/hết hạn/vượt số lần thử.
        otpService.verifyOtp(request.getEmail(), request.getOtpCode());
        otpService.markVerified(request.getEmail());

        return ApiResponse.success("Mã OTP hợp lệ");
    }

    @Override
    @Transactional
    public ApiResponse<String> resetPassword(ResetPasswordRequest request) {
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new AppException(ErrorCode.PASSWORDS_NOT_MATCH);
        }

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new AppException(ErrorCode.EMAIL_NOT_FOUND));

        // Chỉ cho đổi mật khẩu khi OTP đã được verify ở bước trước (cờ ngắn hạn trong Redis).
        if (!otpService.isVerified(request.getEmail())) {
            throw new AppException(ErrorCode.OTP_NOT_VERIFIED);
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setLastPasswordChangeAt(LocalDateTime.now());
        userRepository.save(user);

        // OTP dùng một lần: xoá toàn bộ trạng thái OTP sau khi đổi thành công.
        otpService.invalidate(request.getEmail());

        // Vô hiệu hoá mọi phiên đăng nhập cũ sau khi đổi mật khẩu.
        refreshTokenService.revokeAllTokens(user.getId().toString());
        refreshTokenService.setLogoutTime(user.getEmail());

        return ApiResponse.success("Đặt lại mật khẩu thành công");
    }

    private String generateOtp() {
        int otp = 100000 + SECURE_RANDOM.nextInt(900000);
        return String.valueOf(otp);
    }
}
