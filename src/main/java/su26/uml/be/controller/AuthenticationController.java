package su26.uml.be.controller;

import com.nimbusds.jose.JOSEException;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

import org.springframework.web.bind.annotation.*;
import su26.uml.be.dto.request.IntrospectRequest;
import su26.uml.be.dto.request.LoginRequest;
import su26.uml.be.dto.request.LogoutRequest;
import su26.uml.be.dto.response.ApiResponse;
import su26.uml.be.dto.response.AuthenticationResponse;
import su26.uml.be.dto.response.IntrospectResponse;
import su26.uml.be.repository.UserRepository;
import su26.uml.be.service.AuthenticationService;

import java.text.ParseException;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AuthenticationController {
    UserRepository userRepository;
    AuthenticationService authenticationService;

    @PostMapping("/login")
    ApiResponse<AuthenticationResponse> login(@RequestBody LoginRequest request) {
        var result = authenticationService.authenticate(request);
        return ApiResponse.<AuthenticationResponse>builder()
                .result(result)
                .build();
    }

//    @PostMapping("/refresh")
//    ApiResponse<AuthenticationResponse> refreshToken(@RequestBody RefreshTokenRequest request)
//            throws ParseException, JOSEException {
//        var result = authenticationService.refreshToken(request);
//        return ApiResponse.<AuthenticationResponse>builder()
//                .result(result)
//                .build();
//    }

    @PostMapping("/introspect")
    ApiResponse<IntrospectResponse> introspect(@RequestBody IntrospectRequest request)
            throws ParseException, JOSEException {
        var result = authenticationService.introspect(request);
        return ApiResponse.<IntrospectResponse>builder()
                .result(result)
                .build();
    }

    @PostMapping("/logout")
    ApiResponse<Void> logout(@RequestBody LogoutRequest request)
            throws ParseException, JOSEException {
        authenticationService.logout(request);
        return ApiResponse.<Void>builder()
                .build();
    }

    @GetMapping("/account-status")
    ApiResponse<Map<String, Object>> accountStatus(@RequestParam("identifier") String identifier) {
        var normalized = identifier == null ? "" : identifier.trim();

        var userOpt = userRepository.findByUsername(normalized);
        if (userOpt.isEmpty()) {
            userOpt = userRepository.findByEmail(normalized);
        }

        boolean locked = userOpt
                .map(user -> "LOCKED".equalsIgnoreCase(user.getStatus()))
                .orElse(false);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("locked", locked);

        return ApiResponse.<Map<String, Object>>builder()
                .result(result)
                .build();
    }
}
