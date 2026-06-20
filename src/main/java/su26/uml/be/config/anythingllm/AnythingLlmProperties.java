package su26.uml.be.config.anythingllm;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "anything-llm")
public record AnythingLlmProperties(
        String baseUrl,
        String apiKey,
        String workspaceSlug,
        String modelName
) {
}