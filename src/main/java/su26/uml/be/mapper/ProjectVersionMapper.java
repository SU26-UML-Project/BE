package su26.uml.be.mapper;

import org.mapstruct.Mapping;
import org.mapstruct.Mapper;
import su26.uml.be.entity.Project;
import su26.uml.be.entity.ProjectVersion;

@Mapper(componentModel = "spring")
public interface ProjectVersionMapper {

    @Mapping(target = "project", source = "project")
    @Mapping(target = "projectSnapshot", source = "project.projectData")
    @Mapping(target = "versionNumber", source = "versionNumber")
    ProjectVersion toProjectVersion(Project project, Integer versionNumber);
}
