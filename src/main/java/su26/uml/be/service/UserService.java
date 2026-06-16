package su26.uml.be.service;

import su26.uml.be.dto.request.UpdateUserRequest;
import su26.uml.be.dto.request.UserRegisterRequest;
import su26.uml.be.dto.response.ApiResponse;
import su26.uml.be.dto.response.MeResponse;
import su26.uml.be.dto.response.UserResponse;

import java.util.List;
import java.util.UUID;

public interface UserService {
    ApiResponse<UserResponse> registerUser(UserRegisterRequest request);
    ApiResponse<UserResponse> updateMe(String email, UpdateUserRequest request);
    ApiResponse<List<UserResponse>> getAllUsers();
    ApiResponse<UserResponse> getUserById(UUID userId);
    ApiResponse<MeResponse> getCurrentUser(String email);
}
