package su26.uml.be.service;

import su26.uml.be.dto.request.UserRegisterRequest;
import su26.uml.be.dto.response.ApiResponse;
import su26.uml.be.dto.response.UserResponse;

public interface UserService {
    ApiResponse<UserResponse> registerUser(UserRegisterRequest request);
}
