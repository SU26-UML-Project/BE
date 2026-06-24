package su26.uml.be.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import su26.uml.be.dto.request.DiagramChatRequest;
import su26.uml.be.dto.response.ApiResponse;
import su26.uml.be.dto.response.ChatSessionResponse;
import su26.uml.be.dto.response.DiagramChatHistoryResponse;
import su26.uml.be.dto.response.DiagramChatResponse;
import su26.uml.be.service.DiagramChatService;

import java.util.List;

@RestController
@RequestMapping("/diagram-ai")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Diagram AI Chat", description = "UML diagram AI chat and session APIs.")
@SecurityRequirement(name = "bearerAuth")
public class DiagramChatController {

    DiagramChatService diagramChatService;

    @PostMapping("/chat")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @Operation(
            summary = "Send message to AI",
            description = "Send a message to Diagram AI. Provide sessionId to continue an existing chat."
    )
    public ApiResponse<DiagramChatResponse> chat(
            @Parameter(hidden = true)
            @AuthenticationPrincipal UserDetails userDetails,

            @Valid @RequestBody DiagramChatRequest request
    ) {
        return diagramChatService.chat(userDetails.getUsername(), request);
    }

    @PostMapping("/chat/sessions")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @Operation(
            summary = "Create chat session",
            description = "Create a new AI chat session for the current user."
    )
    public ApiResponse<ChatSessionResponse> createSession(
            @Parameter(hidden = true)
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        return diagramChatService.createSession(userDetails.getUsername());
    }

    @GetMapping("/chat/sessions")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @Operation(
            summary = "Get chat sessions",
            description = "Get all AI chat sessions of the current user."
    )
    public ApiResponse<List<ChatSessionResponse>> getSessions(
            @Parameter(hidden = true)
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        return diagramChatService.getSessions(userDetails.getUsername());
    }

    @GetMapping("/chat/sessions/{sessionId}/messages")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @Operation(summary = "Get chat history", description = "Get all messages of a specific AI chat session.")
    public ApiResponse<DiagramChatHistoryResponse> getHistory(
            @Parameter(hidden = true)
            @AuthenticationPrincipal UserDetails userDetails,

            @Parameter(description = "Chat session ID", example = "uml-chat-3f974a86-f7c9-4d5d-b386-0c6110896cb6")
            @PathVariable String sessionId
    ) {
        return diagramChatService.getHistory(userDetails.getUsername(), sessionId);
    }
}