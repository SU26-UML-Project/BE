package su26.uml.be.mapper;

import org.mapstruct.*;
import su26.uml.be.dto.request.ProjectRequest;
import su26.uml.be.dto.response.ProjectResponse;
import su26.uml.be.entity.Project;

import java.util.List;

@Mapper(componentModel = "spring", uses = {SheetMapper.class})
public interface ProjectMapper {
    Project toProject(ProjectRequest request);

    @Mapping(target = "userId", source = "user.id")
    ProjectResponse toProjectResponse(Project project);

    List<ProjectResponse> toProjectResponseList(List<Project> projects);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateProject(ProjectRequest request, @MappingTarget Project project);
}
