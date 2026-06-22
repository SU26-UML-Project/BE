package su26.uml.be.service.Impl;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import su26.uml.be.config.anythingllm.AnythingLlmClient;
import su26.uml.be.config.anythingllm.AnythingLlmProperties;
import su26.uml.be.dto.request.DiagramChatRequest;
import su26.uml.be.dto.response.AnythingLlmChatResponse;
import su26.uml.be.dto.response.ChatSessionResponse;
import su26.uml.be.dto.response.DiagramChatHistoryResponse;
import su26.uml.be.dto.response.DiagramChatResponse;
import su26.uml.be.entity.AiChatMessageDocument;
import su26.uml.be.entity.AiChatSessionDocument;
import su26.uml.be.entity.AiSourceDocument;
import su26.uml.be.exception.AppException;
import su26.uml.be.exception.ErrorCode;
import su26.uml.be.repository.AiChatMessageMongoRepository;
import su26.uml.be.repository.AiChatSessionMongoRepository;
import su26.uml.be.service.DiagramChatService;

@Service
@RequiredArgsConstructor
public class DiagramChatServiceImpl implements DiagramChatService {

    private static final String ROLE_USER = "USER";
    private static final String ROLE_ASSISTANT = "ASSISTANT";
    private static final String CHAT_MODE = "chat";
    private static final String SESSION_STATUS_ACTIVE = "ACTIVE";
    private static final String DEFAULT_SESSION_TITLE = "UML AI Chat";
    private static final int SOURCE_SNIPPET_MAX_LENGTH = 500;
    private static final int MAX_RETRY_ATTEMPTS = 2;
    private static final long RETRY_DELAY_MILLIS = 800L;

    private final AnythingLlmClient anythingLlmClient;
    private final AnythingLlmProperties anythingLlmProperties;
    private final AiChatSessionMongoRepository chatSessionRepository;
    private final AiChatMessageMongoRepository chatMessageRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public DiagramChatResponse chat(String userId, DiagramChatRequest request) {
        validateUserId(userId);
        validateChatRequest(request);

        try {
            AiChatSessionDocument session = resolveSession(userId, request.getSessionId());

            AnythingLlmChatResponse anythingResponse = callAnythingLlmWithRetry(
                    request.getMessage(),
                    session.getAnythingSessionId()
            );

            String rawAnswer = cleanAnswer(anythingResponse.getTextResponse());

            if (rawAnswer.isBlank()) {
                throw new AppException(ErrorCode.ANYTHING_LLM_ERROR);
            }

            // Parse JSON from AI
            AiResponseContent parsedContent = parseAiResponse(rawAnswer);

            List<AiSourceDocument> sourceDocuments = mapSources(anythingResponse.getSources());

            saveUserMessage(userId, session.getId(), request.getMessage());

            saveAssistantMessage(
                    userId,
                    session.getId(),
                    parsedContent.getMessage(),
                    anythingLlmProperties.modelName(),
                    sourceDocuments,
                    parsedContent.getQuestions().stream()
                            .map(q -> AiChatMessageDocument.QuestionData.builder()
                                    .title(q.getTitle())
                                    .type(q.getType())
                                    .options(q.getOptions())
                                    .build())
                            .collect(Collectors.toList())
            );

            session.setUpdatedAt(LocalDateTime.now());
            chatSessionRepository.save(session);

            return DiagramChatResponse.builder()
                    .answer(parsedContent.getMessage())
                    .questions(parsedContent.getQuestions().stream()
                            .map(q -> DiagramChatResponse.QuestionResponse.builder()
                                    .title(q.getTitle())
                                    .type(q.getType())
                                    .options(q.getOptions())
                                    .build())
                            .collect(Collectors.toList()))
                    .sessionId(session.getAnythingSessionId())
                    .sources(anythingResponse.getSources() == null ? List.of() : anythingResponse.getSources())
                    .build();

        } catch (AppException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new AppException(ErrorCode.CHAT_SESSION_PROCESSING_ERROR);
        }
    }

    @Override
    public ChatSessionResponse createSession(String userId) {
        validateUserId(userId);

        AiChatSessionDocument session = createNewSessionDocument(userId);

        return mapSessionResponse(session);
    }

    @Override
    public List<ChatSessionResponse> getSessions(String userId) {
        validateUserId(userId);

        return chatSessionRepository.findByUserIdOrderByUpdatedAtDesc(userId)
                .stream()
                .map(this::mapSessionResponse)
                .toList();
    }

    @Override
    public DiagramChatHistoryResponse getHistory(String userId, String sessionId) {
        validateUserId(userId);

        if (sessionId == null || sessionId.isBlank()) {
            throw new AppException(ErrorCode.CHAT_SESSION_ID_REQUIRED);
        }

        AiChatSessionDocument session = chatSessionRepository
                .findByAnythingSessionIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new AppException(ErrorCode.CHAT_SESSION_NOT_FOUND));

        List<AiChatMessageDocument> messages =
                chatMessageRepository.findByChatSessionIdOrderByCreatedAtAsc(session.getId());

        return DiagramChatHistoryResponse.builder()
                .sessionId(session.getAnythingSessionId())
                .messages(messages.stream()
                        .map(message -> DiagramChatHistoryResponse.MessageItem.builder()
                                .role(message.getRole())
                                .content(message.getContent())
                                .modelName(message.getModelName())
                                .questions(message.getQuestions() == null ? List.of() : message.getQuestions().stream()
                                        .map(q -> DiagramChatResponse.QuestionResponse.builder()
                                                .title(q.getTitle())
                                                .type(q.getType())
                                                .options(q.getOptions())
                                                .build())
                                        .collect(Collectors.toList()))
                                .createdAt(message.getCreatedAt())
                                .build()
                        )
                        .toList())
                .build();
    }

    private AiResponseContent parseAiResponse(String rawAnswer) {
        String cleanedJson = rawAnswer.trim();

        // 1. Cố gắng tìm JSON block trong markdown if any
        Pattern jsonPattern = Pattern.compile("(?s)```(?:json)?\\s*(\\{.*?\\})\\s*```");
        Matcher matcher = jsonPattern.matcher(cleanedJson);
        if (matcher.find()) {
            cleanedJson = matcher.group(1).trim();
        } else {
            // 2. Nếu không có code block, cố gắng tìm cặp dấu ngoặc {} đầu tiên và cuối cùng
            int firstBrace = cleanedJson.indexOf('{');
            int lastBrace = cleanedJson.lastIndexOf('}');
            if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
                cleanedJson = cleanedJson.substring(firstBrace, lastBrace + 1).trim();
            }
        }

        // 3. Tự động sửa các lỗi JSON phổ biến của AI
        cleanedJson = fixAiJson(cleanedJson);

        try {
            AiResponseContent content = objectMapper.readValue(cleanedJson, AiResponseContent.class);

            // Xử lý từng câu hỏi trong danh sách
            if (content.getQuestions() != null) {
                for (AiResponseContent.QuestionContent q : content.getQuestions()) {
                    if (q.getOptions() != null && !q.getOptions().isEmpty()) {
                        List<String> options = new ArrayList<>(q.getOptions());
                        if (!options.contains("Khác")) {
                            options.add("Khác");
                        }
                        q.setOptions(options);
                    }
                }
            } else {
                content.setQuestions(Collections.emptyList());
            }

            return content;
        } catch (JsonProcessingException e) {
            // Fallback: Trả về text thuần nếu không phải JSON
            return AiResponseContent.builder()
                    .message(rawAnswer)
                    .questions(Collections.emptyList())
                    .build();
        }
    }

    /**
     * Tự động sửa lỗi JSON phổ biến: thiếu dấu phẩy, dấu ngoặc kép thông minh...
     */
    private String fixAiJson(String json) {
        if (json == null || json.isEmpty()) return json;

        return json
            // Thay thế dấu ngoặc kép thông minh “ ” và ‘ ’ bằng "
            .replace("“", "\"").replace("”", "\"")
            .replace("‘", "\"").replace("’", "\"")
            // Sửa lỗi thiếu dấu phẩy giữa các trường (ví dụ: "field1": "val" "field2": "val")
            .replaceAll("(\\\"\\s*:\\s*\\\"[^\\\"]*\\\")\\s*(\\\"\\s*[a-zA-Z0-9_]+\\\"\\s*:)", "$1, $2")
            // Sửa lỗi thiếu dấu phẩy sau mảng (ví dụ: "options": [...] "next_field": ...)
            .replaceAll("(])\\s*(\\\"\\s*[a-zA-Z0-9_]+\\\"\\s*:)", "$1, $2")
            .trim();
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    private static class AiResponseContent {
        private String message;
        private List<QuestionContent> questions;

        @lombok.Data
        @lombok.Builder
        @lombok.NoArgsConstructor
        @lombok.AllArgsConstructor
        public static class QuestionContent {
            private String title;
            private String type;
            private List<String> options;
        }
    }

    private AiChatSessionDocument resolveSession(String userId, String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return createNewSessionDocument(userId);
        }

        return chatSessionRepository
                .findByAnythingSessionIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new AppException(ErrorCode.CHAT_SESSION_NOT_FOUND));
    }

    private AiChatSessionDocument createNewSessionDocument(String userId) {
        LocalDateTime now = LocalDateTime.now();

        return chatSessionRepository.save(
                AiChatSessionDocument.builder()
                        .userId(userId)
                        .anythingSessionId("uml-chat-" + UUID.randomUUID())
                        .title(DEFAULT_SESSION_TITLE)
                        .status(SESSION_STATUS_ACTIVE)
                        .createdAt(now)
                        .updatedAt(now)
                        .build()
        );
    }

    private AnythingLlmChatResponse callAnythingLlmWithRetry(String prompt, String sessionId) {
        for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
            try {
                AnythingLlmChatResponse response =
                        anythingLlmClient.chat(prompt, sessionId);

                if (response == null) {
                    throw new AppException(ErrorCode.ANYTHING_LLM_ERROR);
                }

                if (response.getError() != null && !response.getError().isBlank()) {
                    throw new AppException(ErrorCode.ANYTHING_LLM_ERROR);
                }

                return response;

            } catch (AppException exception) {
                if (attempt == MAX_RETRY_ATTEMPTS) {
                    throw exception;
                }

                sleepBeforeRetry();

            } catch (Exception exception) {
                if (attempt == MAX_RETRY_ATTEMPTS) {
                    throw new AppException(ErrorCode.ANYTHING_LLM_ERROR);
                }

                sleepBeforeRetry();
            }
        }

        throw new AppException(ErrorCode.ANYTHING_LLM_ERROR);
    }

    private void sleepBeforeRetry() {
        try {
            Thread.sleep(RETRY_DELAY_MILLIS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AppException(ErrorCode.ANYTHING_LLM_ERROR);
        }
    }

    private void saveUserMessage(String userId, String chatSessionId, String content) {
        chatMessageRepository.save(
                AiChatMessageDocument.builder()
                        .chatSessionId(chatSessionId)
                        .userId(userId)
                        .role(ROLE_USER)
                        .content(content)
                        .mode(CHAT_MODE)
                        .createdAt(LocalDateTime.now())
                        .build()
        );
    }

    private void saveAssistantMessage(
            String userId,
            String chatSessionId,
            String content,
            String modelName,
            List<AiSourceDocument> sources,
            List<AiChatMessageDocument.QuestionData> questions
    ) {
        chatMessageRepository.save(
                AiChatMessageDocument.builder()
                        .chatSessionId(chatSessionId)
                        .userId(userId)
                        .role(ROLE_ASSISTANT)
                        .content(content)
                        .mode(CHAT_MODE)
                        .modelName(modelName)
                        .questions(questions)
                        .sources(sources)
                        .createdAt(LocalDateTime.now())
                        .build()
        );
    }

    private String cleanAnswer(String answer) {
        if (answer == null) {
            return "";
        }

        return answer
                .replaceAll("(?s)<think>.*?</think>", "")
                .replaceAll("\\[END CONTEXT \\d+\\]", "") // Xóa markers của AnythingLLM
                .replaceAll("\\[\\d+\\]", "")             // Xóa các trích dẫn [1], [2]...
                .trim();
    }

    private List<AiSourceDocument> mapSources(List<Map<String, Object>> sources) {
        if (sources == null || sources.isEmpty()) {
            return List.of();
        }

        return sources.stream()
                .map(this::mapSource)
                .toList();
    }

    private AiSourceDocument mapSource(Map<String, Object> source) {
        return AiSourceDocument.builder()
                .title(toStringValue(source.get("title")))
                .url(toStringValue(source.get("url")))
                .score(toBigDecimal(source.get("score")))
                .snippet(shorten(toStringValue(source.get("text")), SOURCE_SNIPPET_MAX_LENGTH))
                .build();
    }

    private ChatSessionResponse mapSessionResponse(AiChatSessionDocument session) {
        return ChatSessionResponse.builder()
                .sessionId(session.getAnythingSessionId())
                .title(session.getTitle())
                .status(session.getStatus())
                .createdAt(session.getCreatedAt())
                .updatedAt(session.getUpdatedAt())
                .build();
    }

    private void validateUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
    }

    private void validateChatRequest(DiagramChatRequest request) {
        if (request == null || request.getMessage() == null || request.getMessage().isBlank()) {
            throw new AppException(ErrorCode.CHAT_MESSAGE_REQUIRED);
        }
    }

    private String toStringValue(Object value) {
        return value == null ? null : value.toString();
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return null;
        }

        try {
            return new BigDecimal(value.toString());
        } catch (Exception exception) {
            return null;
        }
    }

    private String shorten(String value, int maxLength) {
        if (value == null) {
            return null;
        }

        if (value.length() <= maxLength) {
            return value;
        }

        return value.substring(0, maxLength) + "...";
    }
}