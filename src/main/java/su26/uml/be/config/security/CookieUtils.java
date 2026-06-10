package su26.uml.be.config.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Quản lý refresh-token cookie (HttpOnly).
 *
 * <p>Refresh token KHÔNG còn được trả về trong body JSON hay nhét lên URL.
 * Nó được đặt trong một cookie {@code HttpOnly} nên JavaScript phía FE
 * không đọc được — kháng được tấn công đánh cắp token qua XSS.
 * Browser tự động đính kèm cookie này khi gọi {@code /auth/refresh} và
 * {@code /auth/logout}.</p>
 */
@Component
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CookieUtils {

    @NonFinal
    @Value("${app.auth.cookie.refresh-token-name:refresh_token}")
    String refreshCookieName;

    /** Bật cho production (HTTPS). Trên http://localhost để false. */
    @NonFinal
    @Value("${app.auth.cookie.secure:false}")
    boolean secure;

    /** Lax | Strict | None. None bắt buộc đi kèm secure=true (cross-site). */
    @NonFinal
    @Value("${app.auth.cookie.same-site:Lax}")
    String sameSite;

    @NonFinal
    @Value("${app.auth.cookie.path:/}")
    String path;

    @NonFinal
    @Value("${jwt.refreshTokenExpiration:604800}")
    long refreshTokenExpiration;

    /** Đặt refresh token vào cookie HttpOnly trên response. */
    public void addRefreshTokenCookie(HttpServletResponse response, String token) {
        ResponseCookie cookie = ResponseCookie.from(refreshCookieName, token)
                .httpOnly(true)
                .secure(secure)
                .sameSite(sameSite)
                .path(path)
                .maxAge(refreshTokenExpiration)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    /** Xoá refresh-token cookie (khi logout). */
    public void clearRefreshTokenCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(refreshCookieName, "")
                .httpOnly(true)
                .secure(secure)
                .sameSite(sameSite)
                .path(path)
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    /** Đọc refresh token từ cookie của request (nếu có). */
    public Optional<String> extractRefreshToken(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return Optional.empty();
        }
        for (Cookie cookie : request.getCookies()) {
            if (refreshCookieName.equals(cookie.getName())) {
                String value = cookie.getValue();
                if (value != null && !value.isBlank()) {
                    return Optional.of(value);
                }
            }
        }
        return Optional.empty();
    }
}
