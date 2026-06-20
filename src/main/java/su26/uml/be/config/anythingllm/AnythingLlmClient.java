package su26.uml.be.config.anythingllm;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import su26.uml.be.dto.response.AnythingLlmChatResponse;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class AnythingLlmClient {

    private final WebClient anythingLlmWebClient;
    private final AnythingLlmProperties properties;

    // ─── Chat (existing) ─────────────────────────────────────────
    public AnythingLlmChatResponse chat(String message, String sessionId, String mode) {
        Map<String, Object> body = new HashMap<>();
        body.put("message", message);
        body.put("mode", mode);
        body.put("sessionId", sessionId);
        body.put("reset", false);

        return anythingLlmWebClient.post()
                .uri("/v1/workspace/{slug}/chat", properties.workspaceSlug())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(AnythingLlmChatResponse.class)
                .block(Duration.ofSeconds(120));
    }

    // ─── System Config ───────────────────────────────────────────
    public Map<String, Object> getSystemConfig() {
        return anythingLlmWebClient.get()
                .uri("/v1/system")
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {})
                .block(Duration.ofSeconds(10));
    }

    @SuppressWarnings("unchecked")
    public java.util.List<String> getSystemPreferences() {
        try {
            Map<String, Object> raw = anythingLlmWebClient.get()
                    .uri("/v1/system/")
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .bodyToMono(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {})
                    .block(Duration.ofSeconds(10));
            if (raw == null) return defaultProviders();
            Map<String, Object> settings = (Map<String, Object>) raw.get("settings");
            if (settings == null) return defaultProviders();
            List<String> providers = deriveProviders(settings);
            return providers.isEmpty() ? defaultProviders() : providers;
        } catch (Exception e) {
            return defaultProviders();
        }
    }

    private java.util.List<String> defaultProviders() {
        return java.util.List.of(
            "ollama", "openai", "anthropic", "google", "mistral",
            "groq", "together", "deepseek", "openrouter", "perplexity",
            "azure", "cohere", "fireworks", "novita"
        );
    }

    private java.util.List<String> deriveProviders(Map<String, Object> settings) {
        java.util.List<String> result = new java.util.ArrayList<>();
        if (Boolean.TRUE.equals(settings.get("OpenAiKey")) || isNonEmpty(settings.get("OpenAiKey"))) result.add("openai");
        if (Boolean.TRUE.equals(settings.get("AnthropicApiKey")) || isNonEmpty(settings.get("AnthropicApiKey"))) result.add("anthropic");
        if (Boolean.TRUE.equals(settings.get("GeminiLLMApiKey")) || isNonEmpty(settings.get("GeminiLLMApiKey"))) result.add("google");
        if (Boolean.TRUE.equals(settings.get("MistralApiKey")) || isNonEmpty(settings.get("MistralApiKey"))) result.add("mistral");
        if (Boolean.TRUE.equals(settings.get("GroqApiKey")) || isNonEmpty(settings.get("GroqApiKey"))) result.add("groq");
        if (Boolean.TRUE.equals(settings.get("TogetherAiApiKey")) || isNonEmpty(settings.get("TogetherAiApiKey"))) result.add("together");
        if (Boolean.TRUE.equals(settings.get("DeepSeekApiKey")) || isNonEmpty(settings.get("DeepSeekApiKey"))) result.add("deepseek");
        if (Boolean.TRUE.equals(settings.get("OpenRouterApiKey")) || isNonEmpty(settings.get("OpenRouterApiKey"))) result.add("openrouter");
        if (Boolean.TRUE.equals(settings.get("PerplexityApiKey")) || isNonEmpty(settings.get("PerplexityApiKey"))) result.add("perplexity");
        if (Boolean.TRUE.equals(settings.get("FireworksAiLLMApiKey")) || isNonEmpty(settings.get("FireworksAiLLMApiKey"))) result.add("fireworks");
        if (Boolean.TRUE.equals(settings.get("NovitaLLMApiKey")) || isNonEmpty(settings.get("NovitaLLMApiKey"))) result.add("novita");
        String llmProvider = settings.get("LLMProvider") instanceof String ? (String) settings.get("LLMProvider") : "";
        if (!llmProvider.isEmpty() && !result.contains(llmProvider.toLowerCase())) {
            result.add(0, llmProvider.toLowerCase());
        }
        return result;
    }

    private boolean isNonEmpty(Object value) {
        return value instanceof String s && !s.isEmpty() && !"false".equalsIgnoreCase(s);
    }

    public Map<String, Object> updateSystemConfig(Map<String, Object> config) {
        return anythingLlmWebClient.post()
                .uri("/v1/system/update-env")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(config)
                .retrieve()
                .bodyToMono(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {})
                .block(Duration.ofSeconds(15));
    }

    public Map<String, Object> applySystemSettings() {
        return anythingLlmWebClient.post()
                .uri("/v1/system/update-settings")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {})
                .block(Duration.ofSeconds(15));
    }

    // ─── Connection Test ─────────────────────────────────────────
    public Map<String, Object> testConnection() {
        try {
            return anythingLlmWebClient.get()
                    .uri("/ping")
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .bodyToMono(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {})
                    .block(Duration.ofSeconds(5));
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("online", false);
            error.put("error", e.getMessage());
            return error;
        }
    }

    // ─── Workspaces ──────────────────────────────────────────────
    public Map<String, Object> getWorkspaces() {
        return anythingLlmWebClient.get()
                .uri("/v1/workspaces")
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {})
                .block(Duration.ofSeconds(10));
    }

    public Map<String, Object> getWorkspaceBySlug(String slug) {
        return anythingLlmWebClient.get()
                .uri("/v1/workspace/{slug}", slug)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {})
                .block(Duration.ofSeconds(10));
    }

    public Map<String, Object> updateWorkspace(Map<String, Object> settings) {
        return anythingLlmWebClient.post()
                .uri("/v1/workspace/{slug}/update", properties.workspaceSlug())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(settings)
                .retrieve()
                .bodyToMono(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {})
                .block(Duration.ofSeconds(15));
    }

    // ─── Documents ───────────────────────────────────────────────
    public Map<String, Object> uploadDocument(MultipartFile file, String workspaceSlug) {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        try {
            ByteArrayResource resource = new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename();
                }
            };
            builder.part("file", resource, MediaType.APPLICATION_OCTET_STREAM);
            builder.part("addToWorkspaces", workspaceSlug);
        } catch (Exception e) {
            throw new RuntimeException("Failed to read uploaded file", e);
        }

        return anythingLlmWebClient.post()
                .uri("/v1/document/upload")
                .accept(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .retrieve()
                .bodyToMono(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {})
                .block(Duration.ofSeconds(120));
    }

    public void deleteDocument(String documentPath) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", documentPath);

        anythingLlmWebClient.method(org.springframework.http.HttpMethod.DELETE)
                .uri("/system/remove-document")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .block(Duration.ofSeconds(10));
    }

    public Map<String, Object> reEmbedDocuments(String workspaceSlug) {
        return anythingLlmWebClient.post()
                .uri("/v1/workspace/{slug}/update-embeddings", workspaceSlug)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {})
                .block(Duration.ofSeconds(120));
    }
}
