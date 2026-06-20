package su26.uml.be.dto.request;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AiSystemConfigRequest {
    String llmProvider;
    String baseUrl;
    String apiKey;
    String model;
    String vectorDb;
    String vectorDbEndpoint;
    String vectorDbApiKey;
}
