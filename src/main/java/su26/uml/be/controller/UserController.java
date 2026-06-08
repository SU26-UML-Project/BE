package su26.uml.be.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import su26.uml.be.dto.request.UserRegisterRequest;
import su26.uml.be.dto.response.ApiResponse;
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
            description = "Creates a new user from the supplied details after validating uniqueness "
                    + "(username, email) and the assigned role. Public endpoint — no Bearer token required.",
            security = {}
    )
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            content = @Content(schema = @Schema(implementation = UserRegisterRequest.class),
                    examples = @ExampleObject(value = """
                            {
                              "username": "johndoe",
                              "password": "Passw0rd",
                              "fullName": "John Doe",
                              "email": "john.doe@gmail.com",
                              "phone": "0901234567",
                              "roleId": "3fa85f64-5717-4562-b3fc-2c963f66afa6"
                            }""")))
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "User registered successfully.",
                    content = @Content(schema = @Schema(implementation = UserResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "code": 200,
                                      "message": "Đăng ký thành công",
                                      "result": {
                                        "userID": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
                                        "username": "johndoe",
                                        "fullName": "John Doe",
                                        "email": "john.doe@gmail.com",
                                        "phone": "0901234567",
                                        "status": "ACTIVE",
                                        "role": {"roleID":"3fa85f64-5717-4562-b3fc-2c963f66afa6","roleName":"USER","description":"Standard application user"}
                                      }
                                    }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "Validation failed or duplicate username/email (USER_EXISTED, EMAIL_EXISTED, "
                            + "INVALID_PASSWORD, INVALID_EMAIL_FORMAT, INVALID_PHONE, ...).",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"),
                            examples = @ExampleObject(value = "{\"code\":1002,\"message\":\"Username đã được sử dụng, hãy sử dụng username khác!\",\"result\":null}"))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Assigned role does not exist (ROLE_NOT_FOUND).",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Unexpected server error.",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse")))
    })
    public ApiResponse<UserResponse> register(@Valid @RequestBody UserRegisterRequest request) {
        return userService.registerUser(request);
    }
}
