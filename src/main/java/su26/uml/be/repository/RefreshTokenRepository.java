package su26.uml.be.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import su26.uml.be.entity.RefreshToken;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /** Thu hồi toàn bộ token trong một family (logout hoặc phát hiện reuse). */
    @Modifying
    @Query("update RefreshToken r set r.revoked = true where r.familyId = :familyId and r.revoked = false")
    void revokeFamily(@Param("familyId") UUID familyId);

    /** Dọn token đã hết hạn (gọi định kỳ). */
    @Modifying
    @Query("delete from RefreshToken r where r.expiryTime < :now")
    int deleteExpired(@Param("now") Instant now);
}
