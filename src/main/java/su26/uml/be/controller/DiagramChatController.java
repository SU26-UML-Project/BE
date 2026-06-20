package su26.uml.be.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import su26.uml.be.dto.request.DiagramChatRequest;
import su26.uml.be.dto.response.ApiResponse;
import su26.uml.be.dto.response.ChatSessionResponse;
import su26.uml.be.dto.response.DiagramChatHistoryResponse;
import su26.uml.be.dto.response.DiagramChatResponse;
import su26.uml.be.entity.User;
import su26.uml.be.exception.AppException;
import su26.uml.be.exception.ErrorCode;
import su26.uml.be.repository.UserRepository;
import su26.uml.be.service.DiagramChatService;

import java.util.List;

@RestController
@RequestMapping("/diagram-ai")
@RequiredArgsConstructor
@Tag(name = "Diagram AI Chat", description = "APIs for UML diagram AI chat and session management.")
@SecurityRequirement(name = "bearerAuth")
public class DiagramChatController {

    private final DiagramChatService diagramChatService;
    private final UserRepository userRepository;

    @Operation(
            summary = "Send message to AI",
            description = "Send a message to Diagram AI. Provide sessionId to continue an existing chat. If sessionId is empty, a new session will be created."
    )
    @PostMapping("/chat")
    public ResponseEntity<ApiResponse<DiagramChatResponse>> chat(
            @RequestBody @Valid DiagramChatRequest request,
            @Parameter(hidden = true) Authentication authentication
    ) {
        User user = getCurrentUser(authentication);

        DiagramChatResponse response = diagramChatService.chat(
                user.getId().toString(),
                request
        );

        return ResponseEntity.ok(ApiResponse.success("Chat successfully", response));
    }

    @Operation(
            summary = "Create chat session",
            description = "Create a new AI chat session for the current user. Use the returned sessionId in later chat requests."
    )
    @PostMapping("/chat/sessions")
    public ResponseEntity<ApiResponse<ChatSessionResponse>> createSession(@Parameter(hidden = true) Authentication authentication) {
        User user = getCurrentUser(authentication);

        ChatSessionResponse response = diagramChatService.createSession(
                user.getId().toString()
        );

        return ResponseEntity.ok(ApiResponse.success("Create chat session successfully", response));
    }

    @Operation(
            summary = "Get chat sessions",
            description = "Get all AI chat sessions of the current user, ordered by latest update."
    )
    @GetMapping("/chat/sessions")
    public ResponseEntity<ApiResponse<List<ChatSessionResponse>>> getSessions(@Parameter(hidden = true) Authentication authentication) {
        User user = getCurrentUser(authentication);

        List<ChatSessionResponse> response = diagramChatService.getSessions(
                user.getId().toString()
        );

        return ResponseEntity.ok(ApiResponse.success("Get chat sessions successfully", response));
    }

    @Operation(
            summary = "Get chat history",
            description = "Get all messages of a specific AI chat session."
    )
    @GetMapping("/chat/sessions/{sessionId}/messages")
    public ResponseEntity<ApiResponse<DiagramChatHistoryResponse>> getHistory(
            @Parameter(description = "Chat session ID", example = "uml-chat-3f974a86-f7c9-4d5d-b386-0c6110896cb6")
            @PathVariable String sessionId,

            @Parameter(hidden = true)
            Authentication authentication
    ) {
        User user = getCurrentUser(authentication);

        DiagramChatHistoryResponse response = diagramChatService.getHistory(
                user.getId().toString(),
                sessionId
        );

        return ResponseEntity.ok(ApiResponse.success("Get chat history successfully", response));
    }

    private User getCurrentUser(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        String email = authentication.getName();

        return userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));
    }
}