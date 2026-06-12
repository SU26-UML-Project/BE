package su26.uml.be.controller;

import java.text.ParseException;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nimbusds.jose.JOSEException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import su26.uml.be.config.swagger.SwaggerExamples;
import su26.uml.be.dto.request.IntrospectRequest;
import su26.uml.be.dto.request.LoginRequest;
import su26.uml.be.dto.request.LogoutRequest;
import su26.uml.be.dto.response.AuthenticationResponse;
import su26.uml.be.dto.response.IntrospectResponse;
import su26.uml.be.service.AuthenticationService;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Authentication", description = "Login, token introspection, logout and account status checks.")
public class AuthenticationController {
    AuthenticationService authenticationService;

    @PostMapping("/login")
    @Operation(
            summary = "Authenticate and obtain a JWT",
            description = "Validates email/password and returns a signed HS512 JWT (valid for 1 hour)."
    )
    @SecurityRequirements({})
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            content = @Content(schema = @Schema(implementation = LoginRequest.class),
                    examples = @ExampleObject(value = SwaggerExamples.LOGIN_REQUEST)))
    @ApiResponse(responseCode = "200", description = "Authentication succeeded; JWT returned.",
            content = @Content(schema = @Schema(implementation = su26.uml.be.dto.response.ApiResponse.class),
                    examples = @ExampleObject(value = SwaggerExamples.LOGIN_RESPONSE)))
    su26.uml.be.dto.response.ApiResponse<AuthenticationResponse> login(@RequestBody LoginRequest request,
                                                                       HttpServletResponse response) {
        return su26.uml.be.dto.response.ApiResponse.<AuthenticationResponse>builder()
                .result(authenticationService.authenticate(request, response))
                .build();
    }

    @PostMapping("/refresh")
    @Operation(
            summary = "Refresh access token",
            description = "Reads the REFRESH token from the HttpOnly cookie, rotates it (the old one is " +
                    "immediately blacklisted) and returns a new ACCESS token in the body plus a new REFRESH " +
                    "token in a fresh HttpOnly cookie. Missing/expired/already-used cookie returns 401."
    )
    @SecurityRequirements({})
    @ApiResponse(responseCode = "200", description = "New access token returned; refresh token set as cookie.",
            content = @Content(schema = @Schema(implementation = su26.uml.be.dto.response.ApiResponse.class),
                    examples = @ExampleObject(value = SwaggerExamples.REFRESH_RESPONSE)))
    su26.uml.be.dto.response.ApiResponse<AuthenticationResponse> refreshToken(HttpServletRequest httpRequest,
                                                                              HttpServletResponse response) {
        return su26.uml.be.dto.response.ApiResponse.<AuthenticationResponse>builder()
                .result(authenticationService.refreshToken(httpRequest, response))
                .build();
    }

    @PostMapping("/introspect")
    @Operation(
            summary = "Introspect (validate) a JWT",
            description = "Returns whether the supplied token is structurally valid, correctly signed, " +
                    "unexpired and not blacklisted."
    )
    @SecurityRequirements({})
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            content = @Content(schema = @Schema(implementation = IntrospectRequest.class),
                    examples = @ExampleObject(value = SwaggerExamples.INTROSPECT_REQUEST)))
    @ApiResponse(responseCode = "200", description = "Introspection result (valid: true|false).",
            content = @Content(schema = @Schema(implementation = su26.uml.be.dto.response.ApiResponse.class),
                    examples = @ExampleObject(value = SwaggerExamples.INTROSPECT_RESPONSE)))
    su26.uml.be.dto.response.ApiResponse<IntrospectResponse> introspect(@RequestBody IntrospectRequest request)
            throws ParseException, JOSEException {
        var result = authenticationService.introspect(request);
        return su26.uml.be.dto.response.ApiResponse.<IntrospectResponse>builder()
                .result(result)
                .build();
    }

    @PostMapping("/logout")
    @Operation(
            summary = "Log out (invalidate a JWT)",
            description = "Blacklists the supplied token until its natural expiry so it can no longer be used."
    )
    @SecurityRequirements({})
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            content = @Content(schema = @Schema(implementation = LogoutRequest.class),
                    examples = @ExampleObject(value = SwaggerExamples.LOGOUT_REQUEST)))
    @ApiResponse(responseCode = "200", description = "Token invalidated.",
            content = @Content(schema = @Schema(implementation = su26.uml.be.dto.response.ApiResponse.class),
                    examples = @ExampleObject(value = SwaggerExamples.LOGOUT_RESPONSE)))
    su26.uml.be.dto.response.ApiResponse<Void> logout(
            @RequestBody(required = false) LogoutRequest request, HttpServletRequest httpRequest, HttpServletResponse response)
            throws ParseException, JOSEException {
        authenticationService.logout(request, httpRequest, response);

        return su26.uml.be.dto.response.ApiResponse.<Void>builder()
                .build();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/account-status")
    @Operation(
            summary = "Check whether an account is locked",
            description = "Looks up a user by username or email and reports whether the account is in the LOCKED state. " +
                    "Always returns 200 — unknown identifiers are reported as locked: false to avoid user enumeration."
    )
    @ApiResponse(responseCode = "200", description = "Lock status returned.",
            content = @Content(schema = @Schema(implementation = su26.uml.be.dto.response.ApiResponse.class),
                    examples = @ExampleObject(value = SwaggerExamples.ACCOUNT_STATUS_RESPONSE)))
    su26.uml.be.dto.response.ApiResponse<Map<String, Object>> accountStatus(
            @Parameter(description = "Username or email address to look up.", required = true, example = "johndoe")
            @RequestParam("identifier") String identifier) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("LOCKED", authenticationService.isAccountLocked(identifier));

        return su26.uml.be.dto.response.ApiResponse.<Map<String, Object>>builder()
                .result(result)
                .build();
    }
}
