package su26.uml.be.service.Impl;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import su26.uml.be.config.security.CookieUtils;
import su26.uml.be.dto.request.IntrospectRequest;
import su26.uml.be.dto.request.LoginRequest;
import su26.uml.be.dto.request.LogoutRequest;
import su26.uml.be.dto.response.AuthenticationResponse;
import su26.uml.be.dto.response.IntrospectResponse;
import su26.uml.be.entity.RefreshToken;
import su26.uml.be.entity.User;
import su26.uml.be.exception.AppException;
import su26.uml.be.exception.ErrorCode;
import su26.uml.be.repository.RefreshTokenRepository;
import su26.uml.be.repository.UserRepository;
import su26.uml.be.service.AuthenticationService;
import su26.uml.be.service.TokenBlacklistService;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.text.ParseException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AuthenticationServiceImpl implements AuthenticationService {
    UserRepository userRepository;
    PasswordEncoder passwordEncoder;
    TokenBlacklistService tokenBlacklistService;
    RefreshTokenRepository refreshTokenRepository;
    CookieUtils cookieUtils;

    static SecureRandom SECURE_RANDOM = new SecureRandom();

    @NonFinal
    @Value("${jwt.signerKey}")
    protected String SIGNER_KEY;

    @NonFinal
    @Value("${jwt.accessTokenExpiration:3600}")
    protected long ACCESS_TOKEN_EXPIRATION;

    @NonFinal
    @Value("${jwt.refreshTokenExpiration:604800}")
    protected long REFRESH_TOKEN_EXPIRATION;

    @Override
    public IntrospectResponse introspect(IntrospectRequest request) {
        try {
            var token = request.getToken();
            boolean isValid = true;

            try {
                verifyToken(token);
            } catch (AppException e) {
                isValid = false;
            }

            return IntrospectResponse.builder()
                    .valid(isValid)
                    .build();
        } catch (JOSEException | ParseException e) {
            log.error("Lỗi khi xác thực token", e);
            return IntrospectResponse.builder().valid(false).build();
        }
    }

    @Override
    @Transactional
    public void logout(LogoutRequest request, HttpServletRequest httpRequest, HttpServletResponse response)
            throws ParseException, JOSEException {
        // Thu hồi refresh token family + xoá cookie.
        cookieUtils.extractRefreshToken(httpRequest)
                .ifPresent(this::invalidateRefreshToken);
        cookieUtils.clearRefreshTokenCookie(response);

        // Nếu FE còn gửi kèm access token (đang giữ trong memory) thì blacklist luôn.
        if (request != null && request.getToken() != null && !request.getToken().isBlank()) {
            var signedToken = verifyToken(request.getToken());

            String jit = signedToken.getJWTClaimsSet().getJWTID();
            Date expiryDate = signedToken.getJWTClaimsSet().getExpirationTime();

            tokenBlacklistService.blacklist(jit, expiryDate);
        }
    }

    /** Logout: thu hồi toàn bộ family của refresh token đang dùng. */
    private void invalidateRefreshToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        refreshTokenRepository.findByTokenHash(sha256(refreshToken))
                .ifPresent(rt -> refreshTokenRepository.revokeFamily(rt.getFamilyId()));
    }

    @Override
    @Transactional
    public AuthenticationResponse refreshToken(HttpServletRequest httpRequest, HttpServletResponse response) {
        String rawToken = cookieUtils.extractRefreshToken(httpRequest)
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED));

        RefreshToken stored = refreshTokenRepository.findByTokenHash(sha256(rawToken))
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED));

        // Token đã bị thu hồi hoặc đã được dùng rồi (rotate) mà vẫn xuất hiện lại
        // ⇒ dấu hiệu bị đánh cắp/replay ⇒ thu hồi toàn bộ family để vô hiệu hoá
        // cả token của kẻ tấn công lẫn của nạn nhân, buộc đăng nhập lại.
        if (stored.isRevoked() || stored.isUsed()) {
            refreshTokenRepository.revokeFamily(stored.getFamilyId());
            log.warn("Phát hiện refresh token reuse (family={}). Đã thu hồi toàn bộ family.",
                    stored.getFamilyId());
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        if (stored.getExpiryTime().isBefore(Instant.now())) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        var user = userRepository.findById(stored.getUserId())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        if ("LOCKED".equalsIgnoreCase(user.getStatus())) {
            throw new AppException(ErrorCode.USER_INACTIVE);
        }

        // Rotation: đánh dấu token hiện tại đã dùng, phát token mới trong CÙNG family.
        stored.setUsed(true);
        refreshTokenRepository.save(stored);

        var token = generateToken(user);
        var refreshToken = createRefreshToken(user, stored.getFamilyId());

        // Refresh token mới đi qua cookie HttpOnly, access token trả trong body.
        cookieUtils.addRefreshTokenCookie(response, refreshToken);

        return AuthenticationResponse.builder()
                .token(token)
                .authenticated(true)
                .build();
    }

    @Override
    public boolean isAccountLocked(String identifier) {
        var normalized = identifier == null ? "" : identifier.trim();
        var userOpt = userRepository.findByUsername(normalized);
        if (userOpt.isEmpty()) {
            userOpt = userRepository.findByEmail(normalized);
        }
        return userOpt
                .map(user -> "LOCKED".equalsIgnoreCase(user.getStatus()))
                .orElse(false);
    }

    private SignedJWT verifyToken(String token) throws JOSEException, ParseException {
        JWSVerifier verifier = new MACVerifier(SIGNER_KEY.getBytes());
        SignedJWT signedJWT = SignedJWT.parse(token);

        Date expiryTime = signedJWT.getJWTClaimsSet().getExpirationTime();
        var verified = signedJWT.verify(verifier);

        if (!(verified && expiryTime.after(new Date()))) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        if (tokenBlacklistService.isBlacklisted(signedJWT.getJWTClaimsSet().getJWTID()))
            throw new AppException(ErrorCode.UNAUTHENTICATED);

        String tokenType = signedJWT.getJWTClaimsSet().getStringClaim("tokenType");
        if (!"ACCESS".equals(tokenType)) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        return signedJWT;
    }

    @Override
    @Transactional
    public AuthenticationResponse authenticate(LoginRequest request, HttpServletResponse response) {
        var user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        boolean authenticated = passwordEncoder.matches(request.getPassword(), user.getPassword());

        if (!authenticated) {
            throw new AppException(ErrorCode.INVALID_CREDENTIALS);
        }

        if ("LOCKED".equalsIgnoreCase(user.getStatus())) {
            throw new AppException(ErrorCode.USER_INACTIVE);
        }

        user.setLastActiveAt(LocalDateTime.now());
        userRepository.save(user);

        var token = generateToken(user);
        var refreshToken = generateRefreshToken(user);

        // Refresh token đi qua cookie HttpOnly, KHÔNG trả trong body (tránh lưu localStorage).
        cookieUtils.addRefreshTokenCookie(response, refreshToken);

        return AuthenticationResponse.builder()
                .token(token)
                .authenticated(true)
                .build();
    }

    @Override
    @Transactional
    public AuthenticationResponse generateTokenForOAuth2User(User user) {
        if ("LOCKED".equalsIgnoreCase(user.getStatus())) {
            throw new AppException(ErrorCode.USER_INACTIVE);
        }

        user.setLastActiveAt(LocalDateTime.now());
        userRepository.save(user);

        var token = generateToken(user);
        var refreshToken = generateRefreshToken(user);

        return AuthenticationResponse.builder()
                .token(token)
                .refreshToken(refreshToken)
                .authenticated(true)
                .build();
    }

    private String generateToken(User user) {
        try {
            JWSHeader header = new JWSHeader(JWSAlgorithm.HS512);

            JWTClaimsSet jwtClaimsSet = new JWTClaimsSet.Builder()
                    .subject(user.getUsername())
                    .issuer("DiaUML-Studio")
                    .issueTime(new Date())
                    .expirationTime(new Date(
                            Instant.now().plusSeconds(ACCESS_TOKEN_EXPIRATION).toEpochMilli()
                    ))
                    .claim("userId", user.getUserID())
                    .claim("fullName", user.getFullName())
                    .claim("email", user.getEmail())
                    .jwtID(UUID.randomUUID().toString())
                    .claim("scope", "ROLE_" + user.getRole().getRoleName())
                    .claim("role", "ROLE_" + user.getRole().getRoleName())
                    .claim("tokenType", "ACCESS")
                    .build();

            Payload payload = new Payload(jwtClaimsSet.toJSONObject());
            JWSObject jwsObject = new JWSObject(header, payload);

            jwsObject.sign(new MACSigner(SIGNER_KEY.getBytes()));
            return jwsObject.serialize();
        } catch (JOSEException e) {
            log.error("Cannot create token", e);
            throw new RuntimeException(e);
        }
    }

    /** Phát refresh token cho phiên đăng nhập mới (bắt đầu một family mới). */
    private String generateRefreshToken(User user) {
        return createRefreshToken(user, UUID.randomUUID());
    }

    /**
     * Tạo refresh token opaque trong một family cho trước và lưu hash xuống DB.
     * Trả về token gốc (chỉ client giữ — server chỉ lưu SHA-256).
     */
    private String createRefreshToken(User user, UUID familyId) {
        String rawToken = generateOpaqueToken();

        RefreshToken entity = RefreshToken.builder()
                .tokenHash(sha256(rawToken))
                .familyId(familyId)
                .userId(user.getUserID())
                .expiryTime(Instant.now().plusSeconds(REFRESH_TOKEN_EXPIRATION))
                .used(false)
                .revoked(false)
                .build();
        refreshTokenRepository.save(entity);

        return rawToken;
    }

    /** Sinh chuỗi ngẫu nhiên 256-bit, mã hoá Base64-url (không chứa username/PII). */
    private String generateOpaqueToken() {
        byte[] bytes = new byte[48];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                String h = Integer.toHexString(0xff & b);
                if (h.length() == 1) hex.append('0');
                hex.append(h);
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}