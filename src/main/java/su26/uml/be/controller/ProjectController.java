package su26.uml.be.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import su26.uml.be.dto.request.ProjectRequest;
import su26.uml.be.dto.response.ApiResponse;
import su26.uml.be.dto.response.ProjectResponse;
import su26.uml.be.service.ProjectService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/projects")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Projects", description = "UML Project management APIs")
public class ProjectController {
    ProjectService projectService;

    @PostMapping
    @Operation(summary = "Create a new project", description = "Creates a new UML project and a default sheet.")
    public ApiResponse<ProjectResponse> createProject(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody ProjectRequest request) {
        return projectService.createProject(userDetails.getUsername(), request);
    }

    @GetMapping
    @Operation(summary = "Get all projects", description = "Returns all projects belonging to the authenticated user.")
    public ApiResponse<List<ProjectResponse>> getAllProjects(@AuthenticationPrincipal UserDetails userDetails) {
        return projectService.getAllUserProjects(userDetails.getUsername());
    }

    @GetMapping("/{projectId}")
    @Operation(summary = "Get project by ID", description = "Returns project details if the user owns it.")
    public ApiResponse<ProjectResponse> getProjectById(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID projectId) {
        return projectService.getProjectById(projectId, userDetails.getUsername());
    }

    @PatchMapping("/{projectId}")
    @Operation(summary = "Update project", description = "Updates project name and description.")
    public ApiResponse<ProjectResponse> updateProject(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID projectId,
            @Valid @RequestBody ProjectRequest request) {
        return projectService.updateProject(projectId, userDetails.getUsername(), request);
    }

    @DeleteMapping("/{projectId}")
    @Operation(summary = "Delete project", description = "Deletes a project and all its sheets.")
    public ApiResponse<Void> deleteProject(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID projectId) {
        return projectService.deleteProject(projectId, userDetails.getUsername());
    }
}
