package su26.uml.be.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import su26.uml.be.dto.request.AiDocumentDeleteRequest;
import su26.uml.be.dto.request.AiSystemConfigRequest;
import su26.uml.be.dto.request.AiWorkspaceUpdateRequest;
import su26.uml.be.dto.response.*;
import su26.uml.be.service.AiAdminService;

import java.util.List;

@RestController
@RequestMapping("/admin/ai")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "AI Admin", description = "Admin AnythingLLM configuration.")
public class AiAdminController {

    AiAdminService aiAdminService;

    @GetMapping("/config")
    @Operation(summary = "Get system config", description = "Get current LLM provider, model, vector DB settings from AnythingLLM.")
    public ApiResponse<AiSystemConfigResponse> getSystemConfig() {
        return aiAdminService.getSystemConfig();
    }

    @PutMapping("/config")
    @Operation(summary = "Update system config", description = "Update LLM provider, model, vector DB settings on AnythingLLM.")
    public ApiResponse<AiSystemConfigResponse> updateSystemConfig(
            @Valid @RequestBody AiSystemConfigRequest request) {
        return aiAdminService.updateSystemConfig(request);
    }

    @GetMapping("/providers")
    @Operation(summary = "Get supported providers", description = "Get list of supported LLM providers from AnythingLLM.")
    public ApiResponse<List<String>> getSupportedProviders() {
        return aiAdminService.getSupportedProviders();
    }

    @PostMapping("/test")
    @Operation(summary = "Test connection", description = "Ping AnythingLLM to check connectivity.")
    public ApiResponse<AiTestConnectionResponse> testConnection() {
        return aiAdminService.testConnection();
    }

    @GetMapping("/workspace")
    @Operation(summary = "Get workspace", description = "Get current workspace details including RAG settings. Optional ?slug= query param, defaults to configured workspace.")
    public ApiResponse<AiWorkspaceResponse> getWorkspace(
            @RequestParam(required = false) String slug) {
        return aiAdminService.getWorkspaceBySlug(slug);
    }

    @PutMapping("/workspace")
    @Operation(summary = "Update workspace", description = "Update workspace model, chat mode, and RAG settings. Optional ?slug= query param, defaults to configured workspace.")
    public ApiResponse<AiWorkspaceResponse> updateWorkspace(
            @Valid @RequestBody AiWorkspaceUpdateRequest request,
            @RequestParam(required = false) String slug) {
        return aiAdminService.updateWorkspace(request, slug);
    }

    @GetMapping("/providers/{provider}/models")
    @Operation(summary = "Get provider models", description = "Fetch available models for a given LLM provider from AnythingLLM. Optional ?basePath= query param.")
    public ApiResponse<List<String>> getProviderModels(
            @PathVariable String provider,
            @RequestParam(required = false) String basePath) {
        return aiAdminService.getProviderModels(provider, basePath);
    }

    @GetMapping("/workspaces")
    @Operation(summary = "List workspaces", description = "List all workspaces from AnythingLLM.")
    public ApiResponse<List<AiWorkspaceListItem>> getWorkspaces() {
        return aiAdminService.getWorkspaces();
    }

    @GetMapping("/documents")
    @Operation(summary = "List documents", description = "List documents in a workspace. Optional ?workspace= query param, defaults to configured workspace.")
    public ApiResponse<List<AiDocumentResponse>> getDocuments(
            @RequestParam(required = false) String workspace) {
        return aiAdminService.getDocuments(workspace);
    }

    @PostMapping(value = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload document", description = "Upload a file to a workspace. Optional ?workspace= query param, defaults to configured workspace.")
    public ApiResponse<Void> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String workspace) {
        return aiAdminService.uploadDocument(file, workspace);
    }

    @DeleteMapping("/documents")
    @Operation(summary = "Delete document", description = "Delete a document from the system by its document name.")
    public ApiResponse<Void> deleteDocument(@Valid @RequestBody AiDocumentDeleteRequest request) {
        return aiAdminService.deleteDocument(request);
    }

    @PostMapping("/documents/re-embed")
    @Operation(summary = "Re-embed documents", description = "Re-embed documents in a workspace. Optional ?workspace= query param, defaults to configured workspace.")
    public ApiResponse<Void> reEmbedDocuments(
            @RequestParam(required = false) String workspace) {
        return aiAdminService.reEmbedDocuments(workspace);
    }
}
