package su26.uml.be.service;

import su26.uml.be.dto.request.DeleteProjectRequest;
import su26.uml.be.dto.request.ProjectRequest;
import su26.uml.be.dto.response.ApiResponse;
import su26.uml.be.dto.response.ProjectResponse;

import java.util.List;
import java.util.UUID;

public interface ProjectService {
    ApiResponse<ProjectResponse> createProject(String email, ProjectRequest request);
    ApiResponse<ProjectResponse> updateProject(UUID projectId, String email, ProjectRequest request);
    ApiResponse<Void> deleteProject(DeleteProjectRequest request, String email);
    ApiResponse<ProjectResponse> getProjectById(UUID projectId, String email);
    ApiResponse<List<ProjectResponse>> getAllUserProjects(String email);
    ApiResponse<List<ProjectResponse>> getAllProjectsForAdmin();
}
