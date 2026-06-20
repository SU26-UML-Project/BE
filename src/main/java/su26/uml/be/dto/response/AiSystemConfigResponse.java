package su26.uml.be.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AiSystemConfigResponse {
    String llmProvider;
    String model;
    String vectorDb;
    String vectorDbEndpoint;
    Boolean hasApiKey;
}
