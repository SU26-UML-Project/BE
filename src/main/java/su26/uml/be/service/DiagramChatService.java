package su26.uml.be.service;

import su26.uml.be.dto.request.DiagramChatRequest;
import su26.uml.be.dto.response.ChatSessionResponse;
import su26.uml.be.dto.response.DiagramChatHistoryResponse;
import su26.uml.be.dto.response.DiagramChatResponse;

import java.util.List;

public interface DiagramChatService {

    DiagramChatResponse chat(String userId, DiagramChatRequest request);

    ChatSessionResponse createSession(String userId);

    List<ChatSessionResponse> getSessions(String userId);

    DiagramChatHistoryResponse getHistory(String userId, String sessionId);
}