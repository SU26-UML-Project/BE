package su26.uml.be.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import su26.uml.be.config.swagger.SwaggerExamples;
import su26.uml.be.dto.request.UserRegisterRequest;
import su26.uml.be.dto.response.UserResponse;
import su26.uml.be.service.UserService;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Users", description = "User registration and account management.")
public class UserController {
    UserService userService;

    @PostMapping("/register")
    @Operation(
            summary = "Register a new user account",
            description = "Creates a new user after validating field constraints, username/email uniqueness, " +
                    "and the existence of the assigned role."
    )
    @SecurityRequirements({})
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            content = @Content(schema = @Schema(implementation = UserRegisterRequest.class),
                    examples = @ExampleObject(value = SwaggerExamples.REGISTER_REQUEST)))
    @ApiResponse(responseCode = "200", description = "User registered successfully.",
            content = @Content(schema = @Schema(implementation = su26.uml.be.dto.response.ApiResponse.class),
                    examples = @ExampleObject(value = SwaggerExamples.REGISTER_RESPONSE)))
    public su26.uml.be.dto.response.ApiResponse<UserResponse> register(@Valid @RequestBody UserRegisterRequest request) {
        return userService.registerUser(request);
    }
}
