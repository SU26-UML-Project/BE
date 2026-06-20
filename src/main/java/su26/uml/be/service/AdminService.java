package su26.uml.be.service;

import su26.uml.be.dto.request.UserRegisterRequest;
import su26.uml.be.dto.response.ApiResponse;
import su26.uml.be.dto.response.UserResponse;

import java.util.UUID;

public interface AdminService {
    ApiResponse<UserResponse> registerAdmin(UserRegisterRequest request);
    ApiResponse<Void> toggleUserStatus(UUID userId, String currentUserEmail);
}
