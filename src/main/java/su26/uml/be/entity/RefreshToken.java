package su26.uml.be.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Refresh token dạng opaque, lưu server-side.
 *
 * <p>Token gửi cho client là một chuỗi ngẫu nhiên (không phải JWT, không chứa
 * username/PII). Server chỉ lưu {@code SHA-256} của token nên kể cả DB bị lộ
 * cũng không tái dùng được token. Mỗi lần đăng nhập tạo một "family" mới;
 * mỗi lần refresh sẽ rotate trong cùng family. Nếu một token đã dùng/đã thu hồi
 * bị dùng lại ⇒ dấu hiệu bị đánh cắp ⇒ thu hồi toàn bộ family.</p>
 */
@Entity
@Table(name = "refresh_tokens", indexes = {
        @Index(name = "idx_refresh_token_hash", columnList = "token_hash"),
        @Index(name = "idx_refresh_token_family", columnList = "family_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RefreshToken {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    UUID id;

    /** SHA-256 (hex, 64 ký tự) của token gốc. Không bao giờ lưu token gốc. */
    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    String tokenHash;

    /** Định danh "dòng họ" token — dùng để thu hồi toàn bộ khi phát hiện reuse. */
    @Column(name = "family_id", nullable = false)
    UUID familyId;

    @Column(name = "user_id", nullable = false)
    UUID userId;

    @Column(name = "expiry_time", nullable = false)
    Instant expiryTime;

    /** true sau khi token này đã được rotate (đổi lấy token mới). */
    @Column(name = "used", nullable = false)
    boolean used;

    /** true khi family bị thu hồi (logout hoặc phát hiện reuse). */
    @Column(name = "revoked", nullable = false)
    boolean revoked;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    LocalDateTime createdAt;
}
