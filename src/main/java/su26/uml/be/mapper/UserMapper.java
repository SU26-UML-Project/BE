package su26.uml.be.mapper;

import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import su26.uml.be.dto.request.UpdateUserRequest;
import su26.uml.be.dto.request.UserRegisterRequest;
import su26.uml.be.dto.response.MeResponse;
import su26.uml.be.dto.response.UserResponse;
import su26.uml.be.entity.User;

import java.util.List;

@Mapper(componentModel = "spring")
public interface UserMapper {
    @Mapping(target = "username", source = "email")
    User toUser(UserRegisterRequest request);

    @Mapping(target = "role", source = "role")
    @Mapping(target = "createdAt", source = "createdAt")
    UserResponse toUserResponse(User user);

    List<UserResponse> toUserResponseList(List<User> users);

    @Mapping(target = "role", source = "role.roleName")
    MeResponse toMeResponse(User user);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateUser(UpdateUserRequest request, @MappingTarget User user);
}
