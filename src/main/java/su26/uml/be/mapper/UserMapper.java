package su26.uml.be.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import su26.uml.be.dto.request.UserRegisterRequest;
import su26.uml.be.dto.response.MeResponse;
import su26.uml.be.dto.response.UserResponse;
import su26.uml.be.entity.User;

import java.util.List;

@Mapper(componentModel = "spring")
public interface UserMapper {
    User toUser(UserRegisterRequest request);

    @Mapping(target = "role", source = "role")
    @Mapping(target = "createdAt", source = "createdAt")
//    @Mapping(target = "avatarUrl", source = "avatarUrl")
    UserResponse toUserResponse(User user);

    List<UserResponse> toUserResponseList(List<User> users);

    @Mapping(target = "role", source = "role.roleName")
    MeResponse toMeResponse(User user);
}
