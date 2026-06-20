package su26.uml.be.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import su26.uml.be.entity.AiChatMessageDocument;

import java.util.List;

public interface AiChatMessageMongoRepository
        extends MongoRepository<AiChatMessageDocument, String> {

    List<AiChatMessageDocument> findByChatSessionIdOrderByCreatedAtAsc(String chatSessionId);

    List<AiChatMessageDocument> findTop10ByChatSessionIdOrderByCreatedAtDesc(String chatSessionId);
}