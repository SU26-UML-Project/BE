package su26.uml.be.service.Impl;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import su26.uml.be.config.anythingllm.AnythingLlmClient;
import su26.uml.be.config.anythingllm.AnythingLlmProperties;
import su26.uml.be.dto.request.AiDocumentDeleteRequest;
import su26.uml.be.dto.request.AiSystemConfigRequest;
import su26.uml.be.dto.request.AiWorkspaceUpdateRequest;
import su26.uml.be.dto.response.*;
import su26.uml.be.exception.AppException;
import su26.uml.be.exception.ErrorCode;
import su26.uml.be.service.AiAdminService;

import java.util.*;
import java.util.stream.Collectors;

@Service
@FieldDefaults(level = AccessLevel.PRIVATE)
@Slf4j
public class AiAdminServiceImpl implements AiAdminService {

    final AnythingLlmClient anythingLlmClient;
    final AnythingLlmProperties properties;

    public AiAdminServiceImpl(AnythingLlmClient anythingLlmClient,
                               AnythingLlmProperties properties) {
        this.anythingLlmClient = anythingLlmClient;
        this.properties = properties;
    }

    @Override
    public ApiResponse<AiSystemConfigResponse> getSystemConfig() {
        try {
            Map<String, Object> raw = anythingLlmClient.getSystemConfig();
            return ApiResponse.success("OK", mapToSystemConfig(raw));
        } catch (Exception e) {
            log.error("Failed to fetch system config from AnythingLLM", e);
            throw new AppException(ErrorCode.AI_SYSTEM_CONFIG_FAILED);
        }
    }

    @Override
    public ApiResponse<AiSystemConfigResponse> updateSystemConfig(AiSystemConfigRequest request) {
        try {
            Map<String, Object> envConfig = buildSystemEnvConfig(request);
            anythingLlmClient.updateSystemConfig(envConfig);
            return getSystemConfig();
        } catch (Exception e) {
            log.error("Failed to update system config", e);
            throw new AppException(ErrorCode.AI_UPDATE_CONFIG_FAILED);
        }
    }

    @Override
    public ApiResponse<List<String>> getSupportedProviders() {
        List<String> providers = anythingLlmClient.getSystemPreferences();
        return ApiResponse.success("OK", providers);
    }

    @Override
    public ApiResponse<AiTestConnectionResponse> testConnection() {
        long start = System.currentTimeMillis();
        try {
            Map<String, Object> result = anythingLlmClient.testConnection();
            long latency = System.currentTimeMillis() - start;
            boolean connected = result != null
                    && result.containsKey("online")
                    && Boolean.TRUE.equals(result.get("online"));
            return ApiResponse.success("OK", AiTestConnectionResponse.builder()
                    .connected(connected)
                    .latencyMs(latency)
                    .build());
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - start;
            return ApiResponse.success("OK", AiTestConnectionResponse.builder()
                    .connected(false)
                    .latencyMs(latency)
                    .build());
        }
    }

    @Override
    public ApiResponse<AiWorkspaceResponse> getWorkspaceBySlug(String slug) {
        try {
            String resolved = slug != null && !slug.isBlank() ? slug : properties.workspaceSlug();
            Map<String, Object> raw = anythingLlmClient.getWorkspaceBySlug(resolved);
            Map<String, Object> workspace = extractWorkspace(raw);
            return ApiResponse.success("OK", mapToWorkspaceResponse(workspace));
        } catch (Exception e) {
            log.error("Failed to fetch workspace from AnythingLLM", e);
            throw new AppException(ErrorCode.AI_WORKSPACE_NOT_FOUND);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public ApiResponse<AiWorkspaceResponse> updateWorkspace(AiWorkspaceUpdateRequest request, String slug) {
        try {
            String resolved = slug != null && !slug.isBlank() ? slug : properties.workspaceSlug();
            Map<String, Object> settings = new HashMap<>();
            if (request.getModel() != null) settings.put("chatModel", request.getModel());
            if (request.getChatProvider() != null) settings.put("chatProvider", request.getChatProvider());
            if (request.getChatMode() != null) settings.put("chatMode", request.getChatMode());
            if (request.getTemperature() != null) settings.put("openAiTemp", request.getTemperature());
            if (request.getTopN() != null) settings.put("topN", request.getTopN());
            if (request.getSimilarityThreshold() != null) settings.put("similarityThreshold", request.getSimilarityThreshold());
            if (request.getOpenAiHistory() != null) settings.put("openAiHistory", request.getOpenAiHistory());
            if (request.getOpenAiPrompt() != null) settings.put("openAiPrompt", request.getOpenAiPrompt());
            if (request.getQueryRefusalResponse() != null) settings.put("queryRefusalResponse", request.getQueryRefusalResponse());

            Map<String, Object> raw = anythingLlmClient.updateWorkspace(settings, resolved);
            Map<String, Object> workspace;
            if (raw != null && raw.containsKey("workspace")) {
                Object ws = raw.get("workspace");
                if (ws instanceof List) {
                    workspace = (Map<String, Object>) ((List<?>) ws).get(0);
                } else if (ws instanceof Map) {
                    workspace = (Map<String, Object>) ws;
                } else {
                    workspace = raw;
                }
            } else {
                workspace = raw;
            }
            return ApiResponse.success("Cập nhật workspace thành công", mapToWorkspaceResponse(workspace));
        } catch (Exception e) {
            log.error("Failed to update workspace", e);
            throw new AppException(ErrorCode.AI_WORKSPACE_UPDATE_FAILED);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public ApiResponse<List<AiWorkspaceListItem>> getWorkspaces() {
        try {
            Map<String, Object> raw = anythingLlmClient.getWorkspaces();
            List<Map<String, Object>> workspaces = (List<Map<String, Object>>) raw.getOrDefault("workspaces", List.of());
            List<AiWorkspaceListItem> result = workspaces.stream()
                    .map(ws -> AiWorkspaceListItem.builder()
                            .slug(str(ws.get("slug")))
                            .name(str(ws.get("name")))
                            .build())
                    .collect(Collectors.toList());
            return ApiResponse.success("OK", result);
        } catch (Exception e) {
            log.error("Failed to fetch workspaces from AnythingLLM", e);
            throw new AppException(ErrorCode.AI_WORKSPACE_NOT_FOUND);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public ApiResponse<List<AiDocumentResponse>> getDocuments(String workspaceSlug) {
        try {
            String slug = workspaceSlug != null && !workspaceSlug.isBlank()
                    ? workspaceSlug : properties.workspaceSlug();
            Map<String, Object> raw = anythingLlmClient.getWorkspaceBySlug(slug);
            Map<String, Object> workspace = extractWorkspace(raw);
            List<Map<String, Object>> docs = (List<Map<String, Object>>) workspace.getOrDefault("documents", List.of());
            List<AiDocumentResponse> result = docs.stream()
                    .map(this::mapToDocumentResponse)
                    .collect(Collectors.toList());
            return ApiResponse.success("OK", result);
        } catch (Exception e) {
            log.error("Failed to fetch documents", e);
            throw new AppException(ErrorCode.AI_SYSTEM_CONFIG_FAILED);
        }
    }

    @Override
    public ApiResponse<Void> uploadDocument(MultipartFile file, String workspaceSlug) {
        try {
            String slug = workspaceSlug != null && !workspaceSlug.isBlank()
                    ? workspaceSlug : properties.workspaceSlug();
            anythingLlmClient.uploadDocument(file, slug);
            return ApiResponse.success("Tải document lên thành công");
        } catch (Exception e) {
            log.error("Failed to upload document", e);
            throw new AppException(ErrorCode.AI_DOCUMENT_UPLOAD_FAILED);
        }
    }

    @Override
    public ApiResponse<Void> deleteDocument(AiDocumentDeleteRequest request) {
        try {
            anythingLlmClient.deleteDocument(request.getDocumentPath());
            return ApiResponse.success("Xoá document thành công");
        } catch (Exception e) {
            log.error("Failed to delete document", e);
            throw new AppException(ErrorCode.AI_DOCUMENT_DELETE_FAILED);
        }
    }

    @Override
    public ApiResponse<Void> reEmbedDocuments(String workspaceSlug) {
        try {
            String slug = workspaceSlug != null && !workspaceSlug.isBlank()
                    ? workspaceSlug : properties.workspaceSlug();
            anythingLlmClient.reEmbedDocuments(slug);
            return ApiResponse.success("Re-embed documents thành công");
        } catch (Exception e) {
            log.error("Failed to re-embed documents", e);
            throw new AppException(ErrorCode.AI_RE_EMBED_FAILED);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public ApiResponse<List<String>> getProviderModels(String provider, String basePath) {
        try {
            if (basePath == null || basePath.isBlank()) {
                try {
                    Map<String, Object> config = anythingLlmClient.getSystemConfig();
                    Map<String, Object> settings = (Map<String, Object>) config.get("settings");
                    if (settings != null) {
                        String path = str(settings.get("OLLAMA_BASE_PATH"));
                        if (path != null && !path.isBlank()) basePath = path;
                    }
                } catch (Exception e) {
                    log.warn("Could not read OLLAMA_BASE_PATH from system config", e);
                }
            }

            Map<String, Object> result = anythingLlmClient.getCustomModels(provider, null, basePath);
            List<Map<String, Object>> models = (List<Map<String, Object>>) result.getOrDefault("models", List.of());
            List<String> modelIds = models.stream()
                    .map(m -> str(m.get("id")))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            return ApiResponse.success("OK", modelIds);
        } catch (Exception e) {
            log.error("Failed to fetch models for provider {}", provider, e);
            throw new AppException(ErrorCode.AI_PROVIDER_MODELS_FAILED);
        }
    }

    // ─── Private helpers ─────────────────────────────────────────

    private Map<String, Object> buildSystemEnvConfig(AiSystemConfigRequest request) {
        Map<String, Object> env = new HashMap<>();
        String provider = request.getLlmProvider() != null ? request.getLlmProvider().toLowerCase() : "";

        if (request.getLlmProvider() != null) {
            env.put("LLMProvider", request.getLlmProvider());
        }

        if (request.getBaseUrl() != null) {
            if ("ollama".equals(provider)) {
                env.put("OllamaLLMBasePath", request.getBaseUrl());
            }
        }

        if (request.getApiKey() != null) {
            switch (provider) {
                case "openai" -> env.put("OpenAiKey", request.getApiKey());
                case "anthropic" -> env.put("AnthropicApiKey", request.getApiKey());
                case "azure" -> env.put("AzureOpenAiKey", request.getApiKey());
                case "google", "gemini" -> env.put("GeminiLLMApiKey", request.getApiKey());
            }
        }

        if (request.getModel() != null) {
            switch (provider) {
                case "ollama" -> env.put("OllamaLLMModelPref", request.getModel());
                case "openai" -> env.put("OpenAiModelPref", request.getModel());
                case "anthropic" -> env.put("AnthropicModelPref", request.getModel());
                case "google", "gemini" -> env.put("GeminiLLMModelPref", request.getModel());
                case "azure" -> env.put("AzureOpenAiModelPref", request.getModel());
                default -> env.put("OpenAiModelPref", request.getModel());
            }
        }

        if (request.getVectorDb() != null) {
            env.put("VectorDB", request.getVectorDb());
        }

        if (request.getVectorDbEndpoint() != null) {
            String vdb = request.getVectorDb() != null ? request.getVectorDb().toLowerCase() : "";
            switch (vdb) {
                case "qdrant" -> env.put("QdrantEndpoint", request.getVectorDbEndpoint());
                case "pinecone" -> env.put("PineConeEndpoint", request.getVectorDbEndpoint());
                case "chroma" -> env.put("ChromaEndpoint", request.getVectorDbEndpoint());
                case "weaviate" -> env.put("WeaviateEndpoint", request.getVectorDbEndpoint());
                case "milvus" -> env.put("MilvusAddress", request.getVectorDbEndpoint());
                default -> env.put("QdrantEndpoint", request.getVectorDbEndpoint());
            }
        }

        if (request.getVectorDbApiKey() != null) {
            String vdb = request.getVectorDb() != null ? request.getVectorDb().toLowerCase() : "";
            switch (vdb) {
                case "qdrant" -> env.put("QdrantApiKey", request.getVectorDbApiKey());
                case "chroma" -> env.put("ChromaApiKey", request.getVectorDbApiKey());
                case "weaviate" -> env.put("WeaviateApiKey", request.getVectorDbApiKey());
                case "pinecone" -> env.put("PineConeKey", request.getVectorDbApiKey());
                default -> env.put("QdrantApiKey", request.getVectorDbApiKey());
            }
        }

        return env;
    }

    @SuppressWarnings("unchecked")
    private AiSystemConfigResponse mapToSystemConfig(Map<String, Object> raw) {
        if (raw == null) return new AiSystemConfigResponse();
        Map<String, Object> settings = (Map<String, Object>) raw.get("settings");
        if (settings == null) return new AiSystemConfigResponse();

        String vectorDb = str(settings.get("VectorDB"));
        String vectorDbEndpoint = resolveVectorDbEndpoint(vectorDb, settings);

        return AiSystemConfigResponse.builder()
                .llmProvider(str(settings.get("LLMProvider")))
                .model(str(settings.get("LLMModel")))
                .vectorDb(vectorDb)
                .vectorDbEndpoint(vectorDbEndpoint)
                .hasApiKey(hasApiKeyConfigured(settings))
                .build();
    }

    private String resolveVectorDbEndpoint(String vectorDb, Map<String, Object> settings) {
        if (vectorDb == null) return null;
        return switch (vectorDb.toLowerCase()) {
            case "qdrant" -> str(settings.get("QdrantEndpoint"));
            case "pinecone" -> str(settings.get("PineConeEndpoint"));
            case "chroma" -> str(settings.get("ChromaEndpoint"));
            case "weaviate" -> str(settings.get("WeaviateEndpoint"));
            case "milvus" -> str(settings.get("MilvusEndpoint"));
            case "astra" -> str(settings.get("AstraDBEndpoint"));
            default -> null;
        };
    }

    private boolean hasApiKeyConfigured(Map<String, Object> settings) {
        String provider = str(settings.get("LLMProvider"));
        if (provider == null) return false;
        return switch (provider.toLowerCase()) {
            case "openai" -> isTruthy(settings.get("OpenAiKey"));
            case "anthropic" -> isTruthy(settings.get("AnthropicApiKey"));
            case "google", "gemini" -> isTruthy(settings.get("GeminiLLMApiKey"));
            case "azure" -> isTruthy(settings.get("AzureOpenAiKey"));
            case "mistral" -> isTruthy(settings.get("MistralApiKey"));
            case "groq" -> isTruthy(settings.get("GroqApiKey"));
            case "together" -> isTruthy(settings.get("TogetherAiApiKey"));
            case "deepseek" -> isTruthy(settings.get("DeepSeekApiKey"));
            case "openrouter" -> isTruthy(settings.get("OpenRouterApiKey"));
            case "perplexity" -> isTruthy(settings.get("PerplexityApiKey"));
            default -> true;
        };
    }

    private boolean isTruthy(Object value) {
        if (value instanceof Boolean b) return b;
        if (value instanceof String s) return !s.isEmpty() && !"false".equalsIgnoreCase(s);
        return false;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractWorkspace(Map<String, Object> raw) {
        if (raw == null) return Map.of();
        if (raw.containsKey("workspace")) {
            Object ws = raw.get("workspace");
            if (ws instanceof List && !((List<?>) ws).isEmpty()) {
                return (Map<String, Object>) ((List<?>) ws).get(0);
            } else if (ws instanceof Map) {
                return (Map<String, Object>) ws;
            }
        }
        return raw;
    }

    @SuppressWarnings("unchecked")
    private AiWorkspaceResponse mapToWorkspaceResponse(Map<String, Object> ws) {
        if (ws == null || ws.isEmpty()) return new AiWorkspaceResponse();

        Object tempObj = ws.get("openAiTemp");
        Double temperature = tempObj instanceof Number ? ((Number) tempObj).doubleValue() : null;

        Object topNObj = ws.get("topN");
        Integer topN = topNObj instanceof Number ? ((Number) topNObj).intValue() : null;

        Object simObj = ws.get("similarityThreshold");
        Double similarityThreshold = simObj instanceof Number ? ((Number) simObj).doubleValue() : null;

        Object historyObj = ws.get("openAiHistory");
        Integer openAiHistory = historyObj instanceof Number ? ((Number) historyObj).intValue() : null;

        List<Map<String, Object>> rawDocs = (List<Map<String, Object>>) ws.getOrDefault("documents", List.of());
        List<AiDocumentResponse> documents = rawDocs.stream()
                .map(this::mapToDocumentResponse)
                .collect(Collectors.toList());

        return AiWorkspaceResponse.builder()
                .slug(str(ws.get("slug")))
                .name(str(ws.get("name")))
                .chatModel(str(ws.get("chatModel")))
                .chatProvider(str(ws.get("chatProvider")))
                .chatMode(str(ws.get("chatMode")))
                .temperature(temperature)
                .topN(topN)
                .similarityThreshold(similarityThreshold)
                .openAiHistory(openAiHistory)
                .openAiPrompt(str(ws.get("openAiPrompt")))
                .queryRefusalResponse(str(ws.get("queryRefusalResponse")))
                .documentCount(documents.size())
                .documents(documents)
                .build();
    }

    @SuppressWarnings("unchecked")
    private AiDocumentResponse mapToDocumentResponse(Map<String, Object> doc) {
        if (doc == null) return new AiDocumentResponse();

        String docId = str(doc.get("docId"));
        if (docId == null || docId.isBlank()) docId = Optional.ofNullable(doc.get("id")).map(Object::toString).orElse(null);

        return AiDocumentResponse.builder()
                .docId(docId)
                .filename(str(doc.get("filename")))
                .docpath(str(doc.get("docpath")))
                .size(doc.get("size") instanceof Number ? ((Number) doc.get("size")).longValue() : null)
                .status("embedded")
                .uploadedAt(str(doc.get("createdAt")))
                .build();
    }

    private String str(Object value) {
        return value != null ? value.toString() : null;
    }
}
