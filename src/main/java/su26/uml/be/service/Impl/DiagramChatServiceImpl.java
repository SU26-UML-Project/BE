package su26.uml.be.service.Impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import su26.uml.be.config.anythingllm.AnythingLlmClient;
import su26.uml.be.config.anythingllm.AnythingLlmProperties;
import su26.uml.be.dto.request.DiagramChatRequest;
import su26.uml.be.dto.response.AnythingLlmChatResponse;
import su26.uml.be.dto.response.ChatSessionResponse;
import su26.uml.be.dto.response.DiagramChatHistoryResponse;
import su26.uml.be.dto.response.DiagramChatResponse;
import su26.uml.be.exception.AppException;
import su26.uml.be.exception.ErrorCode;
import su26.uml.be.entity.AiChatMessageDocument;
import su26.uml.be.entity.AiChatSessionDocument;
import su26.uml.be.entity.AiSourceDocument;
import su26.uml.be.repository.AiChatMessageMongoRepository;
import su26.uml.be.repository.AiChatSessionMongoRepository;
import su26.uml.be.service.DiagramChatService;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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

            List<AiChatMessageDocument> recentMessages = getRecentMessages(session.getId());

            String prompt = buildPrompt(request.getMessage(), recentMessages);

            AnythingLlmChatResponse anythingResponse = callAnythingLlmWithRetry(
                    prompt,
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
        
        // Loại bỏ markdown code blocks nếu có
        if (cleanedJson.startsWith("```")) {
            cleanedJson = cleanedJson.replaceAll("^```[a-z]*\\n?", "").replaceAll("\\n?```$", "").trim();
        }

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
            // Fallback: Smart Parse nếu không phải JSON
            return smartParseBulletPoints(rawAnswer);
        }
    }

    /**
     * Tự động quét các bullet points trong text để biến thành QuestionBox
     */
    private AiResponseContent smartParseBulletPoints(String text) {
        List<AiResponseContent.QuestionContent> questions = new ArrayList<>();
        
        // Regex tìm các dòng bắt đầu bằng -, *, + hoặc số thứ tự
        Pattern bulletPattern = Pattern.compile("^[\\s]*[-*+•][\\s]+(.*)$|^[\\s]*[0-9]+\\.[\\s]+(.*)$", Pattern.MULTILINE);
        Matcher matcher = bulletPattern.matcher(text);
        
        List<String> options = new ArrayList<>();
        while (matcher.find()) {
            String option = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            if (option != null && !option.trim().isEmpty()) {
                options.add(option.trim());
            }
        }

        if (!options.isEmpty()) {
            options.add("Khác");
            questions.add(AiResponseContent.QuestionContent.builder()
                    .title("Vui lòng chọn hoặc bổ sung thông tin:")
                    .type("single_select")
                    .options(options)
                    .build());
        }

        return AiResponseContent.builder()
                .message(text)
                .questions(questions)
                .build();
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
                        anythingLlmClient.chat(prompt, sessionId, CHAT_MODE);

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

    private List<AiChatMessageDocument> getRecentMessages(String chatSessionId) {
        List<AiChatMessageDocument> messages =
                chatMessageRepository.findTop10ByChatSessionIdOrderByCreatedAtDesc(chatSessionId);

        List<AiChatMessageDocument> orderedMessages = new ArrayList<>(messages);
        Collections.reverse(orderedMessages);

        return orderedMessages;
    }

    private String buildPrompt(String userMessage, List<AiChatMessageDocument> recentMessages) {
        String conversationHistory = buildConversationHistory(recentMessages);

        return """
                Bạn là trợ lý tư vấn UML Diagram và SDLC.

                Quy tắc trả lời:
                - Người dùng hỏi gì thì trả lời đúng trọng tâm câu đó.
                - Không trả lời lan man.
                - Không tự suy diễn nếu thiếu thông tin.
                - Nếu người dùng hỏi tiếp bằng câu ngắn như "giải thích lý do", "vì sao", "nói rõ hơn", "cái đó là gì", "vậy nên dùng gì", hãy hiểu theo ngữ cảnh của lịch sử hội thoại gần nhất.
                - Chỉ yêu cầu người dùng bổ sung thông tin nếu cả tin nhắn hiện tại và lịch sử hội thoại đều không đủ để kết luận.
                - Nếu người dùng hỏi lý do, hãy giải thích lý do dựa trên câu trả lời gần nhất.
                - Nếu đủ thông tin, hãy đề xuất diagram phù hợp và nói ngắn gọn mục đích sử dụng.
                - Trả lời bằng tiếng Việt.
                - Không hiển thị nội dung trong thẻ <think>.
                - Chỉ trả về câu trả lời cuối cùng.

                Lịch sử hội thoại gần nhất:
                %s

                Tin nhắn hiện tại của người dùng:
                %s
                """.formatted(conversationHistory, userMessage);
    }

    private String buildConversationHistory(List<AiChatMessageDocument> messages) {
        if (messages == null || messages.isEmpty()) {
            return "Chưa có lịch sử hội thoại.";
        }

        StringBuilder builder = new StringBuilder();

        for (AiChatMessageDocument message : messages) {
            builder.append(message.getRole())
                    .append(": ")
                    .append(message.getContent())
                    .append("\n");
        }

        return builder.toString();
    }

    private String cleanAnswer(String answer) {
        if (answer == null) {
            return "";
        }

        return answer
                .replaceAll("(?s)<think>.*?</think>", "")
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