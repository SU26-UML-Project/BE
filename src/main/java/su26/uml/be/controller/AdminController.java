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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import su26.uml.be.config.swagger.SwaggerExamples;
import su26.uml.be.dto.request.UserRegisterRequest;
import su26.uml.be.dto.response.UserResponse;
import su26.uml.be.service.AdminService;

import java.util.UUID;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin", description = "Admin-only management operations.")
public class AdminController {

    AdminService adminService;

    @PostMapping("/register")
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

    @DeleteMapping("/{userId}")
    @Operation(
            summary = "Lock or unlock a user account",
            description = "Toggles the target user's status between ACTIVE and LOCKED. " +
                    "Admins cannot lock their own account or another ADMIN account."
    )
    @ApiResponse(responseCode = "200", description = "User status toggled successfully.")
    public su26.uml.be.dto.response.ApiResponse<Void> toggleUserStatus(
            @PathVariable UUID userId,
            @AuthenticationPrincipal UserDetails userDetails) {
        return adminService.toggleUserStatus(userId, userDetails.getUsername());
    }
}
