package su26.uml.be.controller;

import com.nimbusds.jose.JOSEException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Authentication", description = "Login, token introspection, logout and account status checks.")
public class AuthenticationController {
    UserRepository userRepository;
    AuthenticationService authenticationService;

    @PostMapping("/login")
    @Operation(
            summary = "Authenticate and obtain a JWT",
            description = "Validates username/password and returns a signed HS512 JWT (valid for 1 hour). "
                    + "Public endpoint — no Bearer token required.",
            security = {}
    )
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            content = @Content(schema = @Schema(implementation = LoginRequest.class),
                    examples = @ExampleObject(value = "{\"username\": \"johndoe\", \"password\": \"Passw0rd\"}")))
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Authentication succeeded; JWT returned.",
                    content = @Content(schema = @Schema(implementation = AuthenticationResponse.class),
                            examples = @ExampleObject(value = "{\"code\":200,\"message\":null,\"result\":{\"token\":\"eyJhbGciOiJIUzUxMiJ9...\",\"authenticated\":true}}"))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Invalid credentials (USER_NOT_FOUND / INVALID_CREDENTIALS).",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Account is locked (USER_INACTIVE).",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Unexpected server error.",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse")))
    })
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
    @Operation(
            summary = "Introspect (validate) a JWT",
            description = "Returns whether the supplied token is structurally valid, correctly signed, "
                    + "unexpired and not blacklisted. Public endpoint.",
            security = {}
    )
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            content = @Content(schema = @Schema(implementation = IntrospectRequest.class),
                    examples = @ExampleObject(value = "{\"token\": \"eyJhbGciOiJIUzUxMiJ9...\"}")))
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Introspection result returned (valid: true|false).",
                    content = @Content(schema = @Schema(implementation = IntrospectResponse.class),
                            examples = @ExampleObject(value = "{\"code\":200,\"message\":null,\"result\":{\"valid\":true}}"))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Malformed token / unparseable request.",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Unexpected server error.",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse")))
    })
    ApiResponse<IntrospectResponse> introspect(@RequestBody IntrospectRequest request)
            throws ParseException, JOSEException {
        var result = authenticationService.introspect(request);
        return ApiResponse.<IntrospectResponse>builder()
                .result(result)
                .build();
    }

    @PostMapping("/logout")
    @Operation(
            summary = "Log out (invalidate a JWT)",
            description = "Blacklists the supplied token until its natural expiry so it can no longer be used. "
                    + "Public endpoint — the token itself identifies the session.",
            security = {}
    )
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            content = @Content(schema = @Schema(implementation = LogoutRequest.class),
                    examples = @ExampleObject(value = "{\"token\": \"eyJhbGciOiJIUzUxMiJ9...\"}")))
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Token invalidated.",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = "{\"code\":0,\"message\":null,\"result\":null}"))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Malformed / unparseable token.",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Token signature invalid or already expired (UNAUTHENTICATED).",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Unexpected server error.",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse")))
    })
    ApiResponse<Void> logout(@RequestBody LogoutRequest request)
            throws ParseException, JOSEException {
        authenticationService.logout(request);
        return ApiResponse.<Void>builder()
                .build();
    }

    @GetMapping("/account-status")
    @Operation(
            summary = "Check whether an account is locked",
            description = "Looks up a user by username or email and reports whether the account is in the LOCKED state. "
                    + "Public endpoint. Always returns 200, even when the identifier does not match any account "
                    + "(reported as locked: false) to avoid user enumeration.",
            security = {}
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Lock status returned.",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = "{\"code\":0,\"message\":null,\"result\":{\"locked\":false}}"))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Unexpected server error.",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse")))
    })
    ApiResponse<Map<String, Object>> accountStatus(
            @Parameter(description = "Username or email to look up.", required = true, example = "johndoe")
            @RequestParam("identifier") String identifier) {
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
