package su26.uml.be.config.anythingllm;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import su26.uml.be.dto.response.AnythingLlmChatResponse;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class AnythingLlmClient {

    private final WebClient anythingLlmWebClient;
    private final AnythingLlmProperties properties;

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
}