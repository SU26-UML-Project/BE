package su26.uml.be.service.Impl;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import su26.uml.be.config.anythingllm.AnythingLlmClient;
import su26.uml.be.config.anythingllm.AnythingLlmProperties;
import su26.uml.be.dto.request.DiagramChatRequest;
import su26.uml.be.dto.response.AnythingLlmChatResponse;
import su26.uml.be.dto.response.ApiResponse;
import su26.uml.be.dto.response.ChatSessionResponse;
import su26.uml.be.dto.response.DiagramChatHistoryResponse;
import su26.uml.be.dto.response.DiagramChatResponse;
import su26.uml.be.entity.AiChatMessageDocument;
import su26.uml.be.entity.AiChatSessionDocument;
import su26.uml.be.entity.AiSourceDocument;
import su26.uml.be.entity.User;
import su26.uml.be.exception.AppException;
import su26.uml.be.exception.ErrorCode;
import su26.uml.be.repository.AiChatMessageRepository;
import su26.uml.be.repository.AiChatSessionRepository;
import su26.uml.be.repository.UserRepository;
import su26.uml.be.service.DiagramChatService;

@Service
@RequiredArgsConstructor
@Slf4j
public class DiagramChatServiceImpl implements DiagramChatService {

    private static final String ROLE_USER = "USER";
    private static final String ROLE_ASSISTANT = "ASSISTANT";
    private static final String CHAT_MODE = "chat";
    private static final String SESSION_STATUS_ACTIVE = "ACTIVE";
    private static final String DEFAULT_SESSION_TITLE = "New chat";
    private static final int SOURCE_SNIPPET_MAX_LENGTH = 500;
    private static final int MAX_RETRY_ATTEMPTS = 2;
    private static final long RETRY_DELAY_MILLIS = 800L;

    private final AnythingLlmClient anythingLlmClient;
    private final AnythingLlmProperties anythingLlmProperties;
    private final AiChatSessionRepository chatSessionRepository;
    private final AiChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;

    @Override
    public ApiResponse<DiagramChatResponse> chat(String email, DiagramChatRequest request) {
        User user = getCurrentUser(email);
        String userId = user.getId().toString();

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

            updateSessionTitleIfNeeded(session, request.getMessage());

            List<AiSourceDocument> sourceDocuments = mapSources(anythingResponse.getSources());

            saveUserMessage(userId, session.getId(), request.getMessage());

            saveAssistantMessage(
                    userId,
                    session.getId(),
                    rawAnswer,
                    anythingLlmProperties.modelName(),
                    sourceDocuments
            );

            session.setUpdatedAt(LocalDateTime.now());
            chatSessionRepository.save(session);

            DiagramChatResponse response = DiagramChatResponse.builder()
                    .answer(rawAnswer)
                    .sessionId(session.getAnythingSessionId())
                    .sources(anythingResponse.getSources() == null ? List.of() : anythingResponse.getSources())
                    .build();

            return ApiResponse.success("Chat successfully", response);

        } catch (AppException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new AppException(ErrorCode.CHAT_SESSION_PROCESSING_ERROR);
        }
    }

    @Override
    public ApiResponse<ChatSessionResponse> createSession(String email) {
        User user = getCurrentUser(email);
        String userId = user.getId().toString();

        AiChatSessionDocument session = createNewSessionDocument(userId);

        return ApiResponse.success(
                "Create chat session successfully",
                mapSessionResponse(session)
        );
    }

    @Override
    public ApiResponse<List<ChatSessionResponse>> getSessions(String email) {
        User user = getCurrentUser(email);
        String userId = user.getId().toString();

        List<ChatSessionResponse> response = chatSessionRepository.findByUserIdOrderByUpdatedAtDesc(userId)
                .stream()
                .map(this::mapSessionResponse)
                .toList();

        return ApiResponse.success("Get chat sessions successfully", response);
    }

    @Override
    public ApiResponse<DiagramChatHistoryResponse> getHistory(String email, String sessionId) {
        User user = getCurrentUser(email);
        String userId = user.getId().toString();

        if (sessionId == null || sessionId.isBlank()) {
            throw new AppException(ErrorCode.CHAT_SESSION_ID_REQUIRED);
        }

        AiChatSessionDocument session = chatSessionRepository
                .findByAnythingSessionIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new AppException(ErrorCode.CHAT_SESSION_NOT_FOUND));

        List<AiChatMessageDocument> messages =
                chatMessageRepository.findByChatSessionIdOrderByCreatedAtAsc(session.getId());

        DiagramChatHistoryResponse response = DiagramChatHistoryResponse.builder()
                .sessionId(session.getAnythingSessionId())
                .messages(messages.stream()
                        .map(message -> DiagramChatHistoryResponse.MessageItem.builder()
                                .role(message.getRole())
                                .content(message.getContent())
                                .modelName(message.getModelName())
                                .createdAt(message.getCreatedAt())
                                .build()
                        )
                        .toList())
                .build();

        return ApiResponse.success("Get chat history successfully", response);
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
            List<AiSourceDocument> sources
    ) {
        chatMessageRepository.save(
                AiChatMessageDocument.builder()
                        .chatSessionId(chatSessionId)
                        .userId(userId)
                        .role(ROLE_ASSISTANT)
                        .content(content)
                        .mode(CHAT_MODE)
                        .modelName(modelName)
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

    private User getCurrentUser(String email) {
        if (email == null || email.isBlank()) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        return userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));
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

    private void updateSessionTitleIfNeeded(AiChatSessionDocument session, String userMessage) {
        if (session.getTitle() != null && !session.getTitle().equals(DEFAULT_SESSION_TITLE)) {
            return;
        }

        if (isGreetingOrTooShort(userMessage)) {
            return;
        }

        String title = generateSimpleSessionTitle(userMessage);

        session.setTitle(title);
        session.setUpdatedAt(LocalDateTime.now());

        chatSessionRepository.save(session);
    }

    private String generateSimpleSessionTitle(String message) {
        String title = message.trim()
                .replaceAll("[\\r\\n]+", " ")
                .replaceAll("\\s+", " ")
                .trim();

        title = removeSimplePrefix(title);

        int maxLength = 45;

        if (title.length() <= maxLength) {
            return title;
        }

        return title.substring(0, maxLength).trim() + "...";
    }

    private String removeSimplePrefix(String title) {
        return title
                .replaceFirst("(?i)^tôi muốn\\s+", "")
                .replaceFirst("(?i)^tôi cần\\s+", "")
                .replaceFirst("(?i)^mình muốn\\s+", "")
                .replaceFirst("(?i)^mình cần\\s+", "")
                .replaceFirst("(?i)^em muốn\\s+", "")
                .replaceFirst("(?i)^em cần\\s+", "")
                .replaceFirst("(?i)^hãy giúp tôi\\s+", "")
                .replaceFirst("(?i)^giúp tôi\\s+", "")
                .trim();
    }

    private boolean isGreetingOrTooShort(String message) {
        if (message == null || message.isBlank()) {
            return true;
        }

        String normalized = message.toLowerCase()
                .replaceAll("[\\s,.!?;:]+", " ")
                .trim();

        return normalized.length() < 6
                || normalized.equals("hi")
                || normalized.equals("hello")
                || normalized.equals("hey")
                || normalized.equals("chào")
                || normalized.equals("xin chào")
                || normalized.equals("chào bạn")
                || normalized.equals("alo");
    }
}