package su26.uml.be.service;

import su26.uml.be.dto.request.DiagramChatRequest;
import su26.uml.be.dto.response.ApiResponse;
import su26.uml.be.dto.response.ChatSessionResponse;
import su26.uml.be.dto.response.DiagramChatHistoryResponse;
import su26.uml.be.dto.response.DiagramChatResponse;

import java.util.List;

public interface DiagramChatService {

    ApiResponse<DiagramChatResponse> chat(String email, DiagramChatRequest request);

    ApiResponse<ChatSessionResponse> createSession(String email);

    ApiResponse<List<ChatSessionResponse>> getSessions(String email);

    ApiResponse<DiagramChatHistoryResponse> getHistory(String email, String sessionId);
}