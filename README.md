# UML Diagram Studio — Backend (BE)

Backend REST API cho nền tảng **DiaUML Studio**, xây dựng bằng Spring Boot 4 + Java 21.

---

## Công nghệ chính

| Thành phần | Phiên bản |
|---|---|
| Java | 21 |
| Spring Boot | 4.0.6 |
| Spring Security | (Boot-managed) — xác thực qua filter JWT tự viết (không dùng Resource Server) |
| Spring Security OAuth2 Client | (Boot-managed) — Google OAuth2 login |
| Spring Data JPA + Hibernate | (Boot-managed) |
| Spring Data Redis | (Boot-managed) — ACCESS token blacklist + REFRESH token tracking |
| Spring Validation | (Boot-managed) |
| Spring Actuator | (Boot-managed) |
| PostgreSQL JDBC | (Boot-managed) |
| Redis 7 | (Docker) |
| Nimbus JOSE JWT | (sinh + verify token trong `JwtService`) |
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
│   │   ├── security/                          ← CHỈ còn file legacy DISABLED (giữ để rollback)
│   │   │   ├── CustomJwtDecoder.java           ← DISABLED (comment)
│   │   │   └── JwtAuthenticationEntryPoint.java ← DISABLED (comment)
│   │   └── swagger/
│   │       ├── OpenApiConfig.java
│   │       └── SwaggerExamples.java
│   ├── security/                              ← Auth config/filter/handlers (package cấp cao). Logic token/user-detail nằm ở service/
│   │   ├── SecurityConfig.java
│   │   ├── EncoderConfig.java
│   │   ├── JwtProperties.java                  ← cấu hình jwt.*
│   │   ├── JwtAuthenticationFilter.java        ← filter xác thực mỗi request
│   │   ├── CookieUtils.java
│   │   ├── CustomOAuth2User.java
│   │   ├── OAuth2AuthenticationFailureHandler.java
│   │   └── OAuth2AuthenticationSuccessHandler.java
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
│   └── service/                              ← interface + Impl/ (gồm cả JwtService, RefreshTokenService)
│       ├── AuthenticationService.java
│       ├── TokenBlacklistService.java
│       ├── JwtService.java                    ← sinh + verify token (HS512)
│       ├── RefreshTokenService.java           ← theo dõi refresh token trong Redis
│       ├── UserService.java
│       ├── CustomOAuth2UserService.java
│       └── Impl/                              ← JwtServiceImpl, RefreshTokenServiceImpl, UserDetailsServiceImpl, ...
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
| POST | `/auth/introspect` | Kiểm tra chữ ký + hạn của token (⚠️ KHÔNG check Redis blacklist/logout) | Public |
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

> Token được xác thực bằng **`JwtAuthenticationFilter` + `JwtService` tự viết** (không dùng OAuth2 Resource Server). Trạng thái refresh token nằm trong **Redis**, không phải DB. Đăng nhập bằng **email**.

**Access token** — JWT HS512 (TTL: `JWT_ACCESS_TOKEN_EXPIRATION`, mặc định 1 giờ):
- Set qua **HttpOnly cookie** (`access_token`) và cũng chấp nhận từ header `Authorization: Bearer <token>` (phục vụ Swagger).
- Claims: `sub` (= email), `uid`, `email`, `role`, `scope`, `typ="access"`, `jti`, `iss`, `iat`, `exp`.
- Blacklist: Redis key `blacklist:{jti}`, tự xoá khi hết TTL.

**Refresh token** — JWT HS512, `typ="refresh"` (TTL: `JWT_REFRESH_TOKEN_EXPIRATION`, mặc định 7 ngày):
- Gửi/nhận qua **HttpOnly cookie** (không có trong body response của login/refresh).
- `jti` được lưu trong Redis (`rt:{jti}` → userId). **Không** có SHA-256 hash, không có bảng DB, không có "family".
- Mỗi lần refresh: rotate — revoke `jti` cũ trong Redis, sinh token mới với `jti` mới.
- Token đã rotate/revoke gửi lại sẽ không còn `jti` trong Redis → 401.

**Luồng đăng nhập thường:**
1. `POST /auth/login` (đăng nhập bằng **email** + password) → nhận `token` (access) trong body; access + refresh token được set làm cookie HttpOnly.
2. Request cần xác thực dùng cookie `access_token` (hoặc header Bearer).
3. Khi access token hết hạn → gọi `POST /auth/refresh` (không cần body) → nhận access token mới; cookie refresh tự động rotate.
4. Đăng xuất → `POST /auth/logout` → cookie bị xoá; `jti` refresh bị revoke trong Redis + ghi `logout_time`.

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
- **Bảng chính:** `users`, `roles`. (`refresh_tokens` và `invalidated_token` là entity legacy, **không** được luồng auth hiện tại sử dụng — refresh token + blacklist đều nằm trong Redis.)
