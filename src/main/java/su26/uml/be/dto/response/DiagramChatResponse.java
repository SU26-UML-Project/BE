package su26.uml.be.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class DiagramChatResponse {

    String answer;

    String sessionId;

    List<QuestionResponse> questions;

    List<CanvasActionResponse> actions;

    String newState;

    List<Map<String, Object>> sources;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class QuestionResponse {
        String title;
        String type;
        List<String> options;
    }
}