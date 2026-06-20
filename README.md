# UML Diagram Studio — Backend (BE)

Backend REST API cho nền tảng **DiaUML Studio**, xây dựng bằng Spring Boot 4 + Java 21.

> Tài liệu này là bản tổng quan dành cho người chạy/triển khai. Quy ước code chi tiết (Lombok/DTO/MapStruct/ErrorCode/auth flow/rules) là **`CLAUDE.md`** — đọc file đó trước khi sửa code.

---

## Công nghệ chính

| Thành phần | Phiên bản |
|---|---|
| Java | 21 |
| Spring Boot | 4.0.6 |
| Spring Security | (Boot-managed) — xác thực qua filter JWT tự viết (không dùng Resource Server) |
| Spring Security OAuth2 Client | (Boot-managed) — Google OAuth2 login |
| Spring Data JPA + Hibernate | (Boot-managed) |
| Spring Data Redis | (Boot-managed) — ACCESS token blacklist + REFRESH token tracking + OTP |
| Spring Boot Starter Mail | (Boot-managed) — gửi email OTP + account-setup (Gmail SMTP) |
| Spring Validation | (Boot-managed) |
| Spring Actuator | (Boot-managed) |
| PostgreSQL JDBC | (Boot-managed, runtime) |
| Microsoft SQL Server JDBC | (Boot-managed, runtime) — có trong pom nhưng **chưa cấu hình dùng** |
| Redis 7 | (Docker) |
| Nimbus JOSE JWT | (sinh + verify token trong `JwtService`) |
| MapStruct | 1.5.5.Final |
| Lombok | (Boot-managed, compile-only) |
| springdoc-openapi (Swagger UI) | 3.0.3 |
| Supabase Storage (REST qua WebClient) | avatar (public) + documents (private) |
| Docker Compose | PostgreSQL + pgAdmin + Redis + Qdrant + AnythingLLM |

---

## Cấu trúc thư mục

```text
BE/
├── src/main/java/su26/uml/be/
│   ├── BeApplication.java                   ← @SpringBootApplication, @EnableScheduling
│   ├── config/
│   │   ├── RedisConfig.java
│   │   ├── TimezoneVerificationConfig.java
│   │   ├── TokenCleanupTask.java            ← DISABLED (Redis TTL tự dọn token)
│   │   ├── security/                        ← CHỈ còn file legacy DISABLED (giữ để rollback)
│   │   │   ├── CustomJwtDecoder.java           ← DISABLED (comment)
│   │   │   └── JwtAuthenticationEntryPoint.java ← DISABLED (comment)
│   │   └── swagger/
│   │       ├── OpenApiConfig.java
│   │       └── SwaggerExamples.java
│   ├── security/                            ← Auth config/filter/handlers (logic token/user-detail nằm ở service/)
│   │   ├── SecurityConfig.java
│   │   ├── EncoderConfig.java
│   │   ├── JwtProperties.java
│   │   ├── JwtAuthenticationFilter.java     ← filter xác thực mỗi request (cơ chế auth đang dùng)
│   │   ├── CookieUtils.java
│   │   ├── CustomOAuth2User.java
│   │   ├── OAuth2AuthenticationFailureHandler.java
│   │   └── OAuth2AuthenticationSuccessHandler.java
│   ├── controller/
│   │   ├── AuthenticationController.java     ← /auth: login, refresh, introspect, logout, account-status
│   │   ├── UserController.java              ← /users: register, me, profile, complete-profile, deactivate/restore, change-password (init/confirm), OTP reset, list (ADMIN)
│   │   ├── AdminController.java             ← quản lý user (ADMIN, @PreAuthorize ROLE_ADMIN)
│   │   └── FileController.java              ← /files: avatar, documents, signed-url (Supabase Storage)
│   ├── dto/
│   │   ├── request/                         ← Login, RefreshToken, Introspect, Logout, UserRegister, UpdateUser,
│   │   │                                       CompleteProfile, ForgotPassword/VerifyOtp/ResetPassword, OAuth2UserInfo, ...
│   │   └── response/                        ← ApiResponse, AuthenticationResponse, IntrospectResponse, MeResponse,
│   │                                           UserResponse, DeleteAccountResponse, FileUploadResponse, SignedUrlResponse
│   ├── entity/
│   │   ├── User.java                        ← @Table("users"), UUID PK; password/phone nullable; provider, googleId, profileCompleted
│   │   ├── Role.java
│   │   ├── RefreshToken.java                ← legacy, UNUSED (refresh tracking đã chuyển sang Redis)
│   │   └── InvalidatedToken.java            ← @Deprecated (blacklist đã chuyển sang Redis)
│   ├── exception/
│   │   ├── AppException.java
│   │   ├── ErrorCode.java
│   │   └── GlobalExceptionHandler.java
│   ├── mapper/
│   │   ├── UserMapper.java
│   │   └── OAuth2UserMapper.java
│   ├── repository/                          ← UserRepository, RoleRepository (+ RefreshToken/InvalidatedToken repo legacy)
│   └── service/                             ← interface + Impl/
│       ├── AuthenticationService.java
│       ├── TokenBlacklistService.java        ← Redis ACCESS token blacklist
│       ├── JwtService.java                   ← sinh + verify token (HS512)
│       ├── RefreshTokenService.java          ← theo dõi refresh token JTI trong Redis
│       ├── UserService.java
│       ├── CustomOAuth2UserService.java      ← Google profile → find/create User
│       ├── EmailService.java                 ← email OTP + account-setup (HTML, branded, VN)
│       ├── OtpService.java                   ← lưu/verify OTP reset password trong Redis
│       ├── SupabaseStorageService.java       ← upload avatar/document, signed URL
│       └── Impl/                             ← JwtServiceImpl, RefreshTokenServiceImpl, UserDetailsServiceImpl,
│                                               CustomOAuth2UserServiceImpl, EmailServiceImpl, OtpServiceImpl,
│                                               SupabaseStorageServiceImpl, UserServiceImpl, ...
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
- Docker + Docker Compose (PostgreSQL, pgAdmin, Redis, Qdrant, AnythingLLM)

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

# Gmail SMTP — gửi email OTP + account-setup (spring.mail)
MAIL_USERNAME=
MAIL_PASSWORD=

# Supabase Storage (service_role key — chỉ dùng ở backend, KHÔNG để lộ ra FE)
SUPABASE_URL=
SUPABASE_SERVICE_KEY=

# Redis (mặc định localhost:6379 nếu bỏ qua)
REDIS_HOST=
REDIS_PORT=
REDIS_TIMEOUT=

# HttpOnly Cookie cho token (có giá trị mặc định)
AUTH_COOKIE_NAME=refresh_token
AUTH_COOKIE_SECURE=false      # true khi chạy HTTPS production
AUTH_COOKIE_SAME_SITE=Lax     # None khi FE/BE khác domain (production)
AUTH_COOKIE_PATH=/

# Tuỳ chọn — có giá trị mặc định nếu bỏ qua
FRONTEND_BASE_URL=http://localhost:5173   # link "quên mật khẩu" trong email onboarding
JWT_ACCESS_TOKEN_EXPIRATION=              # mặc định 3600 s
JWT_REFRESH_TOKEN_EXPIRATION=             # mặc định 604800 s
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

**Chạy test:**
```bash
./mvnw.cmd test                  # Windows
./mvnw test                      # macOS / Linux
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
| POST | `/auth/login` | Đăng nhập (email + password), access token trả body, refresh token set cookie HttpOnly | Public |
| POST | `/auth/refresh` | Đọc refresh token từ cookie, rotate, trả access token mới | Public |
| POST | `/auth/introspect` | Kiểm tra chữ ký + hạn của token (⚠️ KHÔNG check Redis blacklist/logout) | Public |
| POST | `/auth/logout` | Revoke refresh token trong Redis + xoá cookie; blacklist access token nếu gửi kèm | Public |
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
| GET | `/users/me` | Thông tin định danh gọn (`MeResponse`, gồm `profileCompleted`) | Authenticated |
| GET | `/users/me/profile` | Hồ sơ đầy đủ của user hiện tại | Authenticated |
| PATCH | `/users/me` | Cập nhật hồ sơ một phần (fullName, phone, dob, avatarUrl) | Authenticated |
| PATCH | `/users/complete-profile` | Onboarding lần đầu cho user Google (đặt password + hồ sơ) | Authenticated |
| POST | `/users/me/deactivate-request` | Yêu cầu xoá tài khoản (grace 30 ngày, sau đó scheduler purge) | Authenticated |
| POST | `/users/me/restore` | Khôi phục tài khoản đang chờ xoá | Authenticated |
| POST | `/users/me/change-password/init` | Đổi mật khẩu bước 1: xác minh mật khẩu hiện tại → gửi OTP qua email | Authenticated |
| POST | `/users/me/change-password/confirm` | Đổi mật khẩu bước 2: xác minh OTP + đặt mật khẩu mới | Authenticated |
| POST | `/users/forgot-password` | Gửi OTP đặt lại mật khẩu qua email | Public |
| POST | `/users/verify-otp` | Xác minh OTP | Public |
| POST | `/users/reset-password` | Đặt lại mật khẩu sau khi verify OTP | Public |
| GET | `/users` | Danh sách user | ADMIN |
| GET | `/users/{userId}` | Chi tiết user theo id | ADMIN |

### Admin (`/admin`)

| Method | Endpoint | Mô tả | Auth |
|---|---|---|---|
| — | quản lý user (xem `AdminController`) | Các thao tác quản trị user | ADMIN (`@PreAuthorize` ROLE_ADMIN) |

### Files (`/files`) — Supabase Storage

| Method | Endpoint | Mô tả | Auth |
|---|---|---|---|
| POST | `/files/avatar` | Upload avatar (bucket public) | Authenticated |
| POST | `/files/documents` | Upload document/PDF (bucket private) | Authenticated |
| GET | `/files/documents/signed-url` | Sinh signed URL truy cập document private | Authenticated |

---

## Cơ chế xác thực

> Token được xác thực bằng **`JwtAuthenticationFilter` + `JwtService` tự viết** (không dùng OAuth2 Resource Server). Trạng thái refresh token nằm trong **Redis**, không phải DB. Đăng nhập bằng **email**.

**Access token** — JWT HS512 (TTL: `JWT_ACCESS_TOKEN_EXPIRATION`, mặc định 1 giờ):
- Set qua **HttpOnly cookie** (`access_token`) và cũng chấp nhận từ header `Authorization: Bearer <token>` (phục vụ Swagger).
- Claims: `sub` (= email), `uid`, `email`, `role`, `scope`, `typ="access"`, `jti`, `iss`, `iat`, `exp`.
- Blacklist: Redis key `blacklist:{jti}`, tự xoá khi hết TTL.

**Refresh token** — JWT HS512, `typ="refresh"` (TTL: `JWT_REFRESH_TOKEN_EXPIRATION`, mặc định 7 ngày):
- Gửi/nhận qua **HttpOnly cookie** (không có trong body response của login/refresh).
- `jti` được lưu trong Redis (`rt:{jti}` → userId, kèm set index `user_rt:{userId}`). **Không** có SHA-256 hash, không có bảng DB, không có "family".
- Mỗi lần refresh: rotate — revoke `jti` cũ trong Redis, sinh token mới với `jti` mới.
- Token đã rotate/revoke gửi lại sẽ không còn `jti` trong Redis → 401.

**Luồng đăng nhập thường:**
1. `POST /auth/login` (email + password) → nhận `token` (access) trong body; access + refresh token được set làm cookie HttpOnly.
2. Request cần xác thực dùng cookie `access_token` (hoặc header Bearer).
3. Khi access token hết hạn → gọi `POST /auth/refresh` (không cần body) → nhận access token mới; cookie refresh tự động rotate.
4. Đăng xuất → `POST /auth/logout` → cookie bị xoá; `jti` refresh bị revoke trong Redis + ghi `logout_time`.

**Luồng Google OAuth2:**
1. Redirect trình duyệt đến `/oauth2/authorization/google`.
2. Sau khi Google xác thực, FE nhận redirect `FRONTEND_CALLBACK_URL?login=success`.
3. FE gọi `POST /auth/refresh` để nhận access token từ cookie đã được set.
4. User Google lần đầu (`profileCompleted = false`, chưa có password) phải hoàn tất **onboarding** qua `PATCH /users/complete-profile` trước khi dùng app.

**Luồng quên mật khẩu (OTP, public):**
1. `POST /users/forgot-password` → gửi OTP qua email (lưu trong Redis).
2. `POST /users/verify-otp` → xác minh OTP.
3. `POST /users/reset-password` → đặt mật khẩu mới (revoke toàn bộ session cũ).

**Luồng đổi mật khẩu trong app (OTP, authenticated):**
1. `POST /users/me/change-password/init` (body `currentPassword`) → xác minh mật khẩu hiện tại; nếu đúng và **không vướng giới hạn 7 ngày** (`PASSWORD_CHANGE_LIMIT`) thì gửi OTP qua email.
2. `POST /users/verify-otp` (dùng chung) → cổng xác minh OTP cho UI (không tiêu thụ OTP).
3. `POST /users/me/change-password/confirm` (body `otpCode`, `newPassword`, `confirmPassword`) → re-validate khớp + độ mạnh (≥ Trung bình, length ≥ 8), xác minh lại OTP rồi đặt mật khẩu mới. **Giữ nguyên session hiện tại** (không revoke token).

> Đổi mật khẩu dùng chung `OtpService` (Redis, OTP đã hash + TTL 90s) với luồng quên mật khẩu. Email OTP đổi mật khẩu có template riêng (`sendChangePasswordOtpEmail`, tông màu tím).

**Xoá tài khoản (grace 30 ngày):**
- `POST /users/me/deactivate-request` → set `status = PENDING_DELETE` + `deletionDate = now + 30 ngày`.
- `POST /users/me/restore` → khôi phục về `ACTIVE`, xoá `deletionDate` (chỉ khi đang `PENDING_DELETE`).
- `AccountDeletionScheduler` chạy lúc **00:00 mỗi ngày** (`0 0 0 * * *`), purge các tài khoản `PENDING_DELETE` đã quá hạn.

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

Khi lỗi, `result` bị bỏ qua (`@JsonInclude(NON_NULL)`). `code` tương ứng với `ErrorCode` enum trong source.

---

## Database & Cache

- **RDBMS:** PostgreSQL
- **DDL:** `hibernate.ddl-auto=update` — Hibernate tự tạo/cập nhật schema khi khởi động (đừng dùng `create`/`create-drop` trên dữ liệu thật).
- **Bảng chính:** `users`, `roles`. (`refresh_tokens` và `invalidated_token` là entity legacy, **không** được luồng auth hiện tại sử dụng — refresh token + blacklist đều nằm trong Redis.)
- **Redis** dùng cho: ACCESS token blacklist (`blacklist:{jti}`), REFRESH token tracking (`rt:{jti}`, `user_rt:{userId}`), marker đăng xuất/đổi mật khẩu (`logout_time:{email}`), và OTP reset password.

> User Google được tạo với `password = null` và `profile_completed = false`; cờ `profile_completed` là nguồn sự thật cho onboarding (xem `CLAUDE.md` §4). Hàng dữ liệu cũ được backfill một lần lúc khởi động bởi `DataInitializer`.
