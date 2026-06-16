package su26.uml.be.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import su26.uml.be.config.swagger.SwaggerExamples;
import su26.uml.be.dto.request.UserRegisterRequest;
import su26.uml.be.dto.response.UserResponse;
import su26.uml.be.service.AdminService;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Admin", description = "Admin-only management operations.")
public class AdminController {

    AdminService adminService;

    @PostMapping("/register")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Create a new admin account",
            description = "Creates a new user with the ADMIN role. Restricted to existing admins."
    )
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            content = @Content(schema = @Schema(implementation = UserRegisterRequest.class),
                    examples = @ExampleObject(value = SwaggerExamples.ADMIN_REGISTER_REQUEST)))
    @ApiResponse(responseCode = "200", description = "Admin account created successfully.",
            content = @Content(schema = @Schema(implementation = su26.uml.be.dto.response.ApiResponse.class),
                    examples = @ExampleObject(value = SwaggerExamples.ADMIN_REGISTER_RESPONSE)))
    public su26.uml.be.dto.response.ApiResponse<UserResponse> registerAdmin(
            @Valid @RequestBody UserRegisterRequest request) {
        return adminService.registerAdmin(request);
    }
}
