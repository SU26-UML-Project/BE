package su26.uml.be.service.Impl;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import su26.uml.be.dto.request.IntrospectRequest;
import su26.uml.be.dto.request.LoginRequest;
import su26.uml.be.dto.request.LogoutRequest;
import su26.uml.be.dto.request.RefreshTokenRequest;
import su26.uml.be.dto.response.AuthenticationResponse;
import su26.uml.be.dto.response.IntrospectResponse;
import su26.uml.be.entity.InvalidatedToken;
import su26.uml.be.entity.User;
import su26.uml.be.exception.AppException;
import su26.uml.be.exception.ErrorCode;
import su26.uml.be.repository.InvalidatedTokenRepository;
import su26.uml.be.repository.UserRepository;
import su26.uml.be.service.AuthenticationService;

import java.text.ParseException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AuthenticationServiceImpl implements AuthenticationService {
    UserRepository userRepository;
    PasswordEncoder passwordEncoder;
    InvalidatedTokenRepository invalidatedTokenRepository;

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
    public void logout(LogoutRequest request) throws ParseException, JOSEException {
        var signedToken = verifyToken(request.getToken());

        String jit = signedToken.getJWTClaimsSet().getJWTID();
        Date expiryDate = signedToken.getJWTClaimsSet().getExpirationTime();

        InvalidatedToken invalidatedToken = InvalidatedToken.builder()
                .iD(jit)
                .expiryTime(expiryDate)
                .build();

        invalidatedTokenRepository.save(invalidatedToken);
    }

    @Override
    public AuthenticationResponse refreshToken(RefreshTokenRequest request) {
        try {
            var signedToken = verifyToken(request.getToken(), true);

            var jit = signedToken.getJWTClaimsSet().getJWTID();
            var expiryTime = signedToken.getJWTClaimsSet().getExpirationTime();

            InvalidatedToken invalidatedToken = InvalidatedToken.builder()
                    .iD(jit)
                    .expiryTime(expiryTime)
                    .build();
            invalidatedTokenRepository.save(invalidatedToken);

            var username = signedToken.getJWTClaimsSet().getSubject();
            var user = userRepository.findByUsername(username)
                    .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

            var token = generateToken(user);
            var refreshToken = generateRefreshToken(user);

            return AuthenticationResponse.builder()
                    .token(token)
                    .refreshToken(refreshToken)
                    .authenticated(true)
                    .build();
        } catch (JOSEException | ParseException e) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
    }

    private SignedJWT verifyToken(String token) throws JOSEException, ParseException {
        JWSVerifier verifier = new MACVerifier(SIGNER_KEY.getBytes());
        SignedJWT signedJWT = SignedJWT.parse(token);

        Date expiryTime = signedJWT.getJWTClaimsSet().getExpirationTime();
        var verified = signedJWT.verify(verifier);

        if (!(verified && expiryTime.after(new Date()))) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        if (invalidatedTokenRepository.existsById(signedJWT.getJWTClaimsSet().getJWTID()))
            throw new AppException(ErrorCode.UNAUTHENTICATED);

        String tokenType = signedJWT.getJWTClaimsSet().getStringClaim("tokenType");
        if (!"ACCESS".equals(tokenType)) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        return signedJWT;
    }

    private SignedJWT verifyToken(String token, boolean isRefresh) throws JOSEException, ParseException {
        JWSVerifier verifier = new MACVerifier(SIGNER_KEY.getBytes());
        SignedJWT signedJWT = SignedJWT.parse(token);

        Date expiryTime = signedJWT.getJWTClaimsSet().getExpirationTime();
        var verified = signedJWT.verify(verifier);

        if (!(verified && expiryTime.after(new Date()))) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        if (invalidatedTokenRepository.existsById(signedJWT.getJWTClaimsSet().getJWTID()))
            throw new AppException(ErrorCode.UNAUTHENTICATED);

        String expectedType = isRefresh ? "REFRESH" : "ACCESS";
        String tokenType = signedJWT.getJWTClaimsSet().getStringClaim("tokenType");
        if (!expectedType.equals(tokenType)) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        return signedJWT;
    }

    @Override
    public AuthenticationResponse authenticate(LoginRequest request) {
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
                    .claim("userID", user.getUserID())
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

    private String generateRefreshToken(User user) {
        try {
            JWSHeader header = new JWSHeader(JWSAlgorithm.HS512);

            JWTClaimsSet jwtClaimsSet = new JWTClaimsSet.Builder()
                    .subject(user.getUsername())
                    .issuer("DiaUML-Studio")
                    .issueTime(new Date())
                    .expirationTime(new Date(
                            Instant.now().plusSeconds(REFRESH_TOKEN_EXPIRATION).toEpochMilli()
                    ))
                    .jwtID(UUID.randomUUID().toString())
                    .claim("tokenType", "REFRESH")
                    .build();

            Payload payload = new Payload(jwtClaimsSet.toJSONObject());
            JWSObject jwsObject = new JWSObject(header, payload);

            jwsObject.sign(new MACSigner(SIGNER_KEY.getBytes()));
            return jwsObject.serialize();
        } catch (JOSEException e) {
            log.error("Cannot create refresh token", e);
            throw new RuntimeException(e);
        }
    }
}