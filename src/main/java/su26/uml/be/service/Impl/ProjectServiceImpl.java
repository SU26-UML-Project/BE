package su26.uml.be.service.Impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import su26.uml.be.dto.request.DeleteProjectRequest;
import su26.uml.be.dto.request.ProjectRequest;
import su26.uml.be.dto.response.ApiResponse;
import su26.uml.be.dto.response.ProjectResponse;
import su26.uml.be.entity.Project;
import su26.uml.be.entity.ProjectVersion;
import su26.uml.be.entity.User;
import su26.uml.be.exception.AppException;
import su26.uml.be.exception.ErrorCode;
import su26.uml.be.mapper.ProjectMapper;
import su26.uml.be.repository.ProjectRepository;
import su26.uml.be.repository.ProjectVersionRepository;
import su26.uml.be.repository.UserRepository;
import su26.uml.be.service.ProjectService;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
@Transactional
public class ProjectServiceImpl implements ProjectService {
    ProjectRepository projectRepository;
    UserRepository userRepository;
    ProjectVersionRepository projectVersionRepository;
    ProjectMapper projectMapper;

    @Override
    public ApiResponse<ProjectResponse> createProject(String email, ProjectRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        Project project = projectMapper.toProject(request);
        project.setUser(user);
        
        // Khởi tạo dữ liệu XML mặc định cho draw.io nếu chưa có
        if (project.getProjectData() == null || project.getProjectData().isEmpty()) {
            project.setProjectData(""); // Hoặc một template XML trống của draw.io
        }
        
        Project savedProject = projectRepository.save(project);

        log.info("Project created: {} for user: {}", savedProject.getId(), email);
        return ApiResponse.success("Tạo dự án thành công", projectMapper.toProjectResponse(savedProject));
    }

    @Override
    public ApiResponse<ProjectResponse> updateProject(UUID projectId, String email, ProjectRequest request) {
        Project project = getProjectAndValidateOwnership(projectId, email);
        projectMapper.updateProject(request, project);
        Project updatedProject = projectRepository.save(project);

        return ApiResponse.success("Cập nhật dự án thành công", projectMapper.toProjectResponse(updatedProject));
    }

    @Override
    public ApiResponse<Void> deleteProject(DeleteProjectRequest request, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));
        boolean isAdmin = user.getRole().getRoleName().equals("ADMIN");

        List<Project> projects = projectRepository.findAllByIdIn(request.getIds());
        
        for (Project project : projects) {
            if (!isAdmin && !project.getUser().getEmail().equals(email)) {
                throw new AppException(ErrorCode.PROJECT_ACCESS_DENIED);
            }
            
            saveProjectVersion(project);
            
            project.setDeleted(true);
            project.setUpdatedAt(LocalDateTime.now());
        }
        
        projectRepository.saveAll(projects);
        
        log.info("Bulk soft-deleted {} projects by user/admin: {}", projects.size(), email);
        return ApiResponse.success("Xóa các dự án thành công");
    }

    @Override
    public ApiResponse<ProjectResponse> getProjectById(UUID projectId, String email) {
        Project project = getProjectAndValidateOwnership(projectId, email);
        return ApiResponse.success("Lấy thông tin dự án thành công", projectMapper.toProjectResponse(project));
    }

    @Override
    public ApiResponse<List<ProjectResponse>> getAllUserProjects(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        List<Project> projects = projectRepository.findAllByUserAndIsDeletedFalse(user);
        return ApiResponse.success("Lấy danh sách dự án thành công", projectMapper.toProjectResponseList(projects));
    }

    @Override
    @Transactional(readOnly = true)
    public ApiResponse<List<ProjectResponse>> getAllProjectsForAdmin() {
        List<Project> projects = projectRepository.findAllByIsDeletedFalse();
        return ApiResponse.success("Lấy danh sách tất cả dự án thành công (Admin)", projectMapper.toProjectResponseList(projects));
    }

    private Project getProjectAndValidateOwnership(UUID projectId, String email) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new AppException(ErrorCode.PROJECT_NOT_FOUND));

        if (project.isDeleted()) {
            throw new AppException(ErrorCode.PROJECT_NOT_FOUND);
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        boolean isAdmin = user.getRole().getRoleName().equals("ADMIN");

        if (!isAdmin && !project.getUser().getEmail().equals(email)) {
            throw new AppException(ErrorCode.PROJECT_ACCESS_DENIED);
        }
        return project;
    }

    private void saveProjectVersion(Project project) {
        Integer lastVersion = projectVersionRepository.findFirstByProjectOrderByVersionNumberDesc(project)
                .map(ProjectVersion::getVersionNumber)
                .orElse(0);

        ProjectVersion version = ProjectVersion.builder()
                .project(project)
                .projectSnapshot(project.getProjectData())
                .versionNumber(lastVersion + 1)
                .build();
        
        projectVersionRepository.save(version);
    }
}
