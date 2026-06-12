//package su26.uml.be.config;
//
//import lombok.AccessLevel;
//import lombok.RequiredArgsConstructor;
//import lombok.experimental.FieldDefaults;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.scheduling.annotation.Scheduled;
//import org.springframework.stereotype.Component;
//import org.springframework.transaction.annotation.Transactional;
//import su26.uml.be.repository.RefreshTokenRepository;
//
//import java.time.Instant;
//
///**
// * Dọn định kỳ các refresh token đã hết hạn trong bảng {@code refresh_tokens}
// * để bảng không phình vô hạn. Chạy 3h sáng mỗi ngày.
// *
// * <p>Access-token blacklist KHÔNG cần dọn ở đây nữa: đã chuyển sang Redis với
// * TTL nên Redis tự xoá khi token hết hạn.</p>
// */
///*
//@Component
//@RequiredArgsConstructor
//@Slf4j
//@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
//public class TokenCleanupTask {
//
//    // RefreshTokenRepository refreshTokenRepository;
//
//    @Scheduled(cron = "0 0 3 * * *")
//    @Transactional
//    public void cleanupExpiredTokens() {
//        // int refreshDeleted = refreshTokenRepository.deleteExpired(Instant.now());
//        // log.info("Dọn refresh token hết hạn: {} refresh_tokens", refreshDeleted);
//        log.info("TokenCleanupTask: Refresh tokens are now handled by Redis TTL. SQL cleanup skipped.");
//    }
//}
//*/
