package su26.uml.be.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class DiagramChatRequest {

        String sessionId;

        @NotBlank(message = "CHAT_MESSAGE_REQUIRED")
        String message;
}