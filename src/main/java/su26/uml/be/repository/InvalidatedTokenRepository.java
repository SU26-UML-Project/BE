package su26.uml.be.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import su26.uml.be.entity.InvalidatedToken;

public interface InvalidatedTokenRepository extends JpaRepository<InvalidatedToken, String> {
}
