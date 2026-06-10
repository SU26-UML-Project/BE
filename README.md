# UML Diagram Studio — Backend (BE)

Backend REST API cho nền tảng **DiaUML Studio**, xây dựng bằng Spring Boot 4 + Java 21.

---

## Công nghệ chính

| Thành phần | Phiên bản |
|---|---|
| Java | 21 |
| Spring Boot | 4.0.6 |
| Spring Security + OAuth2 Resource Server | (Boot-managed) |
| Spring Security OAuth2 Client | (Boot-managed) — Google OAuth2 login |
| Spring Data JPA + Hibernate | (Boot-managed) |
| Spring Data Redis | (Boot-managed) — ACCESS token blacklist |
| Spring Validation | (Boot-managed) |
| Spring Actuator | (Boot-managed) |
| PostgreSQL JDBC | (Boot-managed) |
| Redis 7 | (Docker) |
| Nimbus JOSE JWT | (via OAuth2 Resource Server) |
| MapStruct | 1.5.5.Final |
| Lombok | (Boot-managed) |
| springdoc-openapi (Swagger UI) | 3.0.3 |
| Docker Compose | (PostgreSQL + pgAdmin + Redis) |

---

## Cấu trúc thư mục

```text
BE/
├── src/main/java/su26/uml/be/
│   ├── BeApplication.java
│   ├── config/
│   │   ├── RedisConfig.java
│   │   ├── TimezoneVerificationConfig.java
│   │   ├── TokenCleanupTask.java
│   │   ├── security/
│   │   │   ├── CookieUtils.java
│   │   │   ├── CustomJwtDecoder.java
│   │   │   ├── CustomOAuth2User.java
│   │   │   ├── EncoderConfig.java
│   │   │   ├── JwtAuthenticationEntryPoint.java
│   │   │   ├── OAuth2AuthenticationFailureHandler.java
│   │   │   ├── OAuth2AuthenticationSuccessHandler.java
│   │   │   └── SecurityConfig.java
│   │   └── swagger/
│   │       ├── OpenApiConfig.java
│   │       └── SwaggerExamples.java
│   ├── controller/
│   │   ├── AuthenticationController.java
│   │   └── UserController.java
│   ├── dto/
│   │   ├── request/
│   │   └── response/
│   ├── entity/
│   │   ├── User.java
│   │   ├── Role.java
│   │   ├── RefreshToken.java
│   │   └── InvalidatedToken.java   ← @Deprecated
│   ├── exception/
│   │   ├── AppException.java
│   │   ├── ErrorCode.java
│   │   └── GlobalExceptionHandler.java
│   ├── mapper/
│   │   └── UserMapper.java
│   ├── repository/
│   └── service/
│       ├── AuthenticationService.java
│       ├── TokenBlacklistService.java
│       ├── UserService.java
│       ├── CustomOAuth2UserService.java
│       └── Impl/
├── src/main/resources/
│   └── application.yaml
├── docker-compose.yml
├── .env.example
├── CLAUDE.md
└── pom.xml
```

---

## Yêu cầu môi trường

- JDK 21
- Maven Wrapper đã có sẵn (`mvnw` / `mvnw.cmd`) — không cần cài Maven riêng
- Docker + Docker Compose (để chạy PostgreSQL và pgAdmin bằng container)

---

## Cấu hình biến môi trường

Tạo file `.env` từ `.env.example` rồi điền đầy đủ:

```env
APP_NAME=
SERVER_PORT=
CONTEXT_PATH=

DB_HOST=
DB_PORT=
DB_NAME=
DB_USERNAME=
DB_PASSWORD=

APP_TIMEZONE=

PGADMIN_EMAIL=
PGADMIN_PASSWORD=
PGADMIN_PORT=

JWT_SIGNER_KEY=your_secret_key

# Google OAuth2
GOOGLE_CLIENT_ID=
GOOGLE_CLIENT_SECRET=
FRONTEND_CALLBACK_URL=http://localhost:5173/auth/google/callback

# Redis (mặc định localhost:6379 nếu bỏ qua)
REDIS_HOST=
REDIS_PORT=
REDIS_TIMEOUT=

# HttpOnly Cookie cho refresh token (có giá trị mặc định)
AUTH_COOKIE_NAME=refresh_token
AUTH_COOKIE_SECURE=false      # true khi chạy HTTPS production
AUTH_COOKIE_SAME_SITE=Lax     # None khi FE/BE khác domain (production)
AUTH_COOKIE_PATH=/

# Tuỳ chọn — có giá trị mặc định nếu bỏ qua
JWT_ACCESS_TOKEN_EXPIRATION=
JWT_REFRESH_TOKEN_EXPIRATION=
```

**Google Cloud Console** — đăng ký redirect URI:
```
http://localhost:8088/api/uml/login/oauth2/code/google
```

---

## Chạy services bằng Docker

```bash
docker compose up -d
```

Docker Compose khởi động: **PostgreSQL**, **pgAdmin**, **Redis**, **Qdrant**, **AnythingLLM**.

pgAdmin: `http://localhost:<PGADMIN_PORT>`
Redis: `localhost:6379`
Qdrant: `http://localhost:6333`
AnythingLLM: `http://localhost:3001`

---

## Chạy ứng dụng

**Windows:**
```bash
./mvnw.cmd spring-boot:run
```

**macOS / Linux:**
```bash
./mvnw spring-boot:run
```

**Build JAR:**
```bash
./mvnw.cmd package -DskipTests   # Windows
./mvnw package -DskipTests       # macOS / Linux
```

---

## URL truy cập (mặc định)

| Mục đích | URL |
|---|---|
| Base API | `http://localhost:8088/api/uml` |
| Swagger UI | `http://localhost:8088/api/uml/swagger-ui.html` |
| OpenAPI JSON | `http://localhost:8088/api/uml/v3/api-docs` |
| Health check | `http://localhost:8088/api/uml/actuator/health` |

---

## API chính

### Authentication (`/auth`)

| Method | Endpoint | Mô tả | Auth |
|---|---|---|---|
| POST | `/auth/login` | Đăng nhập, access token trả body, refresh token set cookie HttpOnly | Public |
| POST | `/auth/refresh` | Đọc refresh token từ cookie, rotate, trả access token mới | Public |
| POST | `/auth/introspect` | Kiểm tra token còn hợp lệ không (Redis blacklist check) | Public |
| POST | `/auth/logout` | Revoke refresh token family + xoá cookie; blacklist access token nếu gửi kèm | Public |
| GET | `/auth/account-status` | Kiểm tra tài khoản có bị khoá không | ADMIN |

### Google OAuth2

| Method | Endpoint | Mô tả |
|---|---|---|
| GET | `/oauth2/authorization/google` | Redirect đến Google consent screen (Spring-managed) |
| GET | `/login/oauth2/code/google` | Google callback — Spring xử lý, redirect FE với `?login=success` hoặc `?error=...` |

Sau khi redirect thành công, FE gọi `POST /auth/refresh` để lấy access token từ cookie.

### Users (`/users`)

| Method | Endpoint | Mô tả | Auth |
|---|---|---|---|
| POST | `/users/register` | Đăng ký tài khoản mới | Public |

---

## Cơ chế xác thực

**Access token** — JWT HS512 (TTL: `JWT_ACCESS_TOKEN_EXPIRATION`, mặc định 1 giờ):
- Gửi qua header: `Authorization: Bearer <token>`
- Claims: `sub` (username), `userID`, `fullName`, `email`, `role`, `scope`, `tokenType="ACCESS"`
- Blacklist: Redis key `blacklist:{jti}`, tự xoá khi hết TTL.

**Refresh token** — Opaque 256-bit (TTL: `JWT_REFRESH_TOKEN_EXPIRATION`, mặc định 7 ngày):
- **KHÔNG phải JWT**, không chứa thông tin người dùng.
- Gửi/nhận qua **HttpOnly cookie** (không có trong body response).
- Server lưu SHA-256 hash + `family_id` trong bảng `refresh_tokens`.
- Mỗi lần refresh: rotate token, giữ nguyên family.
- Phát hiện reuse (token đã dùng bị gửi lại): thu hồi toàn bộ family → 401.

**Luồng đăng nhập thường:**
1. `POST /auth/login` → nhận `token` (access) trong body; refresh token được set làm cookie.
2. Dùng `token` cho các request cần xác thực.
3. Khi access token hết hạn → gọi `POST /auth/refresh` (không cần body) → nhận access token mới; cookie refresh tự động rotate.
4. Đăng xuất → `POST /auth/logout` → cookie xoá, refresh family bị revoke.

**Luồng Google OAuth2:**
1. Redirect trình duyệt đến `/oauth2/authorization/google`.
2. Sau khi Google xác thực, FE nhận redirect `FRONTEND_CALLBACK_URL?login=success`.
3. FE gọi `POST /auth/refresh` để nhận access token từ cookie đã được set.
4. Các bước tiếp theo giống luồng thường.

---

## Response envelope

Mọi endpoint đều trả về cùng một cấu trúc:

```json
{
  "code": 200,
  "message": "Success",
  "result": { }
}
```

Khi lỗi, `result` bị bỏ qua. `code` tương ứng với `ErrorCode` enum trong source.

---

## Database

- **RDBMS:** PostgreSQL
- **DDL:** `hibernate.ddl-auto=update` — Hibernate tự tạo/cập nhật schema khi khởi động.
- **Bảng chính:** `users`, `roles`, `invalidated_token`
