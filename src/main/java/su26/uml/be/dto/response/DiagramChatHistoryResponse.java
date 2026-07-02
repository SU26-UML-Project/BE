package su26.uml.be.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class DiagramChatHistoryResponse {

    String sessionId;

    List<MessageItem> messages;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class MessageItem {

        String role;

        String content;

        String modelName;

        LocalDateTime createdAt;
    }
}
