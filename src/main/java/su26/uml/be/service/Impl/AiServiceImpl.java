package su26.uml.be.service.Impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import su26.uml.be.config.anythingllm.AnythingLlmClient;
import su26.uml.be.config.anythingllm.AnythingLlmProperties;
import su26.uml.be.dto.request.AiCreateWorkspaceRequest;
import su26.uml.be.dto.request.AiDocumentDeleteRequest;
import su26.uml.be.dto.request.AiSystemConfigRequest;
import su26.uml.be.dto.request.AiWorkspaceUpdateRequest;
import su26.uml.be.dto.response.*;
import su26.uml.be.exception.AppException;
import su26.uml.be.exception.ErrorCode;
import su26.uml.be.service.AiService;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class AiServiceImpl implements AiService {

    AnythingLlmClient anythingLlmClient;
    AnythingLlmProperties properties;

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
    public ApiResponse<Void> createWorkspace(AiCreateWorkspaceRequest request) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("name", request.getName());
            anythingLlmClient.createWorkspace(body);
            return ApiResponse.success("Tạo workspace thành công");
        } catch (Exception e) {
            log.error("Failed to create workspace", e);
            throw new AppException(ErrorCode.AI_WORKSPACE_CREATE_FAILED);
        }
    }

    @Override
    public ApiResponse<Void> deleteWorkspace(String slug) {
        try {
            anythingLlmClient.deleteWorkspace(slug);
            return ApiResponse.success("Xoá workspace thành công");
        } catch (Exception e) {
            log.error("Failed to delete workspace", e);
            throw new AppException(ErrorCode.AI_WORKSPACE_DELETE_FAILED);
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
    public ApiResponse<AiVersionResponse> getVersion() {
        String version = properties.version();
        String llmProvider = null;
        String llmModel = null;

        try {
            Map<String, Object> raw = anythingLlmClient.getVersion();
            if (raw != null && raw.containsKey("version")) {
                version = str(raw.get("version"));
            }
        } catch (Exception e) {
            log.warn("Could not fetch version from AnythingLLM, using config default: {}", version);
        }

        try {
            Map<String, Object> config = anythingLlmClient.getSystemConfig();
            Map<String, Object> settings = (Map<String, Object>) config.get("settings");
            if (settings != null) {
                llmProvider = str(settings.get("LLMProvider"));
                llmModel = str(settings.get("LLMModel"));
            }
        } catch (Exception e) {
            log.warn("Could not fetch system config for version info");
        }

        String baseUrl = properties.baseUrl();
        String environment = (baseUrl != null && baseUrl.contains("onrender.com"))
                ? "Production" : "Development";

        return ApiResponse.success("OK", AiVersionResponse.builder()
                .version(version != null ? version : "unknown")
                .llmProvider(llmProvider)
                .model(llmModel)
                .environment(environment)
                .build());
    }

    @Override
    public ApiResponse<List<String>> getProviderModels(String provider, String basePath) {
        try {
            String p = provider != null ? provider.toLowerCase() : "";

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

            List<String> models;
            if ("ollama".equals(p)) {
                models = anythingLlmClient.fetchOllamaModels(basePath);
            } else if (List.of("openai", "azure", "lm studio", "localai").contains(p)) {
                models = anythingLlmClient.fetchOpenAiCompatibleModels(basePath);
            } else {
                models = KNOWN_PROVIDER_MODELS.getOrDefault(p, List.of());
            }

            return ApiResponse.success("OK", models);
        } catch (Exception e) {
            log.error("Failed to fetch models for provider {}", provider, e);
            throw new AppException(ErrorCode.AI_PROVIDER_MODELS_FAILED);
        }
    }

    private static final Map<String, List<String>> KNOWN_PROVIDER_MODELS = Map.ofEntries(
            Map.entry("anthropic", List.of("claude-3-opus-20240229", "claude-3-sonnet-20240229", "claude-3-haiku-20240307", "claude-2.1", "claude-2.0")),
            Map.entry("google", List.of("gemini-pro", "gemini-1.5-pro", "gemini-1.5-flash")),
            Map.entry("mistral", List.of("mistral-large-latest", "mistral-medium-latest", "mistral-small-latest")),
            Map.entry("groq", List.of("llama3-70b-8192", "llama3-8b-8192", "mixtral-8x7b-32768", "gemma2-9b-it")),
            Map.entry("together", List.of("mistralai/Mixtral-8x7B-Instruct-v0.1", "meta-llama/Llama-3-70b-chat-hf")),
            Map.entry("deepseek", List.of("deepseek-chat", "deepseek-coder")),
            Map.entry("openrouter", List.of("openrouter/auto", "meta-llama/llama-3-70b-instruct")),
            Map.entry("perplexity", List.of("llama-3-sonar-large-32k", "llama-3-sonar-small-32k")),
            Map.entry("cohere", List.of("command-r", "command-r-plus")),
            Map.entry("fireworks", List.of("accounts/fireworks/models/llama-v3-70b-instruct")),
            Map.entry("novita", List.of("llama3-70b-instruct"))
    );

    // ─── Private helpers ─────────────────────────────────────────

    // ─── Provider env var mapping ──────────────────────────────
    private static final Map<String, String> LLM_API_KEY_MAP = Map.ofEntries(
            Map.entry("openai", "OpenAiKey"),
            Map.entry("anthropic", "AnthropicApiKey"),
            Map.entry("azure", "AzureOpenAiKey"),
            Map.entry("google", "GeminiLLMApiKey"),
            Map.entry("gemini", "GeminiLLMApiKey"),
            Map.entry("mistral", "MistralApiKey"),
            Map.entry("groq", "GroqApiKey"),
            Map.entry("together", "TogetherAiApiKey"),
            Map.entry("deepseek", "DeepSeekApiKey"),
            Map.entry("openrouter", "OpenRouterApiKey"),
            Map.entry("perplexity", "PerplexityApiKey")
    );

    private static final Map<String, String> LLM_BASE_URL_MAP = Map.of(
            "ollama", "OllamaLLMBasePath",
            "openai", "OpenAiBasePath",
            "azure", "AzureOpenAiBasePath",
            "anthropic", "AnthropicBasePath"
    );

    private static final Map<String, String> LLM_MODEL_MAP = Map.of(
            "ollama", "OllamaLLMModelPref",
            "openai", "OpenAiModelPref",
            "anthropic", "AnthropicModelPref",
            "google", "GeminiLLMModelPref",
            "gemini", "GeminiLLMModelPref",
            "azure", "AzureOpenAiModelPref"
    );

    private static final Map<String, String> EMB_MODEL_MAP = Map.of(
            "openai", "OpenAiEmbeddingModelPref",
            "ollama", "OllamaEmbeddingModelPref",
            "azure", "AzureOpenAiEmbeddingModelPref"
    );

    private static final Map<String, String> VDB_ENDPOINT_MAP = Map.of(
            "qdrant", "QdrantEndpoint",
            "pinecone", "PineConeEndpoint",
            "chroma", "ChromaEndpoint",
            "weaviate", "WeaviateEndpoint",
            "milvus", "MilvusAddress"
    );

    private static final Map<String, String> VDB_API_KEY_MAP = Map.of(
            "qdrant", "QdrantApiKey",
            "chroma", "ChromaApiKey",
            "weaviate", "WeaviateApiKey",
            "pinecone", "PineConeKey"
    );

    private Map<String, Object> buildSystemEnvConfig(AiSystemConfigRequest request) {
        Map<String, Object> env = new HashMap<>();
        String provider = request.getLlmProvider() != null ? request.getLlmProvider().toLowerCase() : "";

        if (request.getLlmProvider() != null) {
            env.put("LLMProvider", request.getLlmProvider());
        }

        if (request.getBaseUrl() != null) {
            env.put(LLM_BASE_URL_MAP.getOrDefault(provider, "OpenAiBasePath"), request.getBaseUrl());
        }

        if (request.getApiKey() != null) {
            String key = LLM_API_KEY_MAP.get(provider);
            if (key != null) env.put(key, request.getApiKey());
        }

        if (request.getModel() != null) {
            env.put(LLM_MODEL_MAP.getOrDefault(provider, "OpenAiModelPref"), request.getModel());
        }

        if (request.getEmbeddingProvider() != null) {
            env.put("EmbeddingEngine", request.getEmbeddingProvider());
        }

        if (request.getEmbeddingModel() != null) {
            String embProvider = request.getEmbeddingProvider() != null ? request.getEmbeddingProvider().toLowerCase() : "";
            env.put(EMB_MODEL_MAP.getOrDefault(embProvider, "EmbeddingModelPref"), request.getEmbeddingModel());
        }

        if (request.getVectorDb() != null) {
            env.put("VectorDB", request.getVectorDb());
        }

        if (request.getVectorDbEndpoint() != null) {
            String vdb = request.getVectorDb() != null ? request.getVectorDb().toLowerCase() : "";
            env.put(VDB_ENDPOINT_MAP.getOrDefault(vdb, "QdrantEndpoint"), request.getVectorDbEndpoint());
        }

        if (request.getVectorDbApiKey() != null) {
            String vdb = request.getVectorDb() != null ? request.getVectorDb().toLowerCase() : "";
            String key = VDB_API_KEY_MAP.get(vdb);
            if (key != null) env.put(key, request.getVectorDbApiKey());
        }

        if (request.getDocumentChunkSize() != null) {
            env.put("DocumentChunkSize", request.getDocumentChunkSize());
        }
        if (request.getDocumentChunkOverlap() != null) {
            env.put("DocumentChunkOverlap", request.getDocumentChunkOverlap());
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

        // Read embedding model from provider-specific key, fallback to generic key
        String embProvider = str(settings.get("EmbeddingEngine"));
        String embModelPrefKey = EMB_MODEL_MAP.getOrDefault(
                embProvider != null ? embProvider.toLowerCase() : "",
                "EmbeddingModelPref"
        );
        String embeddingModel = str(settings.get(embModelPrefKey));
        if (embeddingModel == null) embeddingModel = str(settings.get("EmbeddingModelPref"));

        String llmProvider = str(settings.get("LLMProvider"));
        String model = str(settings.get("LLMModel"));
        if (model == null && llmProvider != null) {
            String modelKey = LLM_MODEL_MAP.get(llmProvider.toLowerCase());
            if (modelKey != null) model = str(settings.get(modelKey));
        }

        Integer chunkSize = null;
        try { Object o = settings.get("DocumentChunkSize"); if (o instanceof Number) chunkSize = ((Number) o).intValue(); } catch (Exception ignored) {}
        Integer chunkOverlap = null;
        try { Object o = settings.get("DocumentChunkOverlap"); if (o instanceof Number) chunkOverlap = ((Number) o).intValue(); } catch (Exception ignored) {}

        return AiSystemConfigResponse.builder()
                .llmProvider(llmProvider)
                .model(model)
                .embeddingProvider(embProvider)
                .embeddingModel(embeddingModel)
                .vectorDb(vectorDb)
                .vectorDbEndpoint(vectorDbEndpoint)
                .anythingLlmBaseUrl(properties.baseUrl())
                .documentChunkSize(chunkSize)
                .documentChunkOverlap(chunkOverlap)
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
        String envKey = LLM_API_KEY_MAP.get(provider.toLowerCase());
        if (envKey != null) return isTruthy(settings.get(envKey));
        return true; // providers without API key (e.g., Ollama) are always "configured"
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
