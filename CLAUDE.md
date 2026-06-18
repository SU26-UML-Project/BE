# CLAUDE.md — UML Diagram Studio Backend

## 1. Project Overview

| Field | Value |
|---|---|
| **Name** | UML Diagram Studio API (`DiaUML-Studio`) |
| **Artifact** | `su26.uml:BE:0.0.1-SNAPSHOT` |
| **Java** | 21 |
| **Spring Boot** | 4.0.6 |
| **Build tool** | Maven Wrapper (`mvnw` / `mvnw.cmd`) |

**Runtime dependencies (from pom.xml):**

| Library | Version |
|---|---|
| Spring Boot Starter Web | (Boot-managed) |
| Spring Boot Starter Data JPA | (Boot-managed) |
| Spring Boot Starter Security | (Boot-managed) |
| Spring Boot Starter Validation | (Boot-managed) |
| Spring Boot Starter Actuator | (Boot-managed) |
| Spring Boot Starter Mail | (Boot-managed) |
| Spring Boot Starter Data Redis | (Boot-managed) — ACCESS token blacklist + REFRESH token tracking |
| Spring Boot OAuth2 Resource Server | (Boot-managed) — present in pom for Nimbus JOSE JWT only; **NOT used as a resource server** (no `oauth2ResourceServer` config) |
| Spring Boot OAuth2 Client | (Boot-managed) — Google OAuth2 login |
| PostgreSQL JDBC | (Boot-managed, runtime) |
| Microsoft SQL Server JDBC | (Boot-managed, runtime) — present in pom but not configured |
| Lombok | (Boot-managed, compile-only) |
| MapStruct | 1.5.5.Final |
| springdoc-openapi-starter-webmvc-ui | 3.0.3 |

---

## 2. Project Structure

```
BE/
├── pom.xml
├── docker-compose.yml                       — PostgreSQL + pgAdmin + Redis + Qdrant + AnythingLLM
├── .env / .env.example
└── src/main/java/su26/uml/be/
    ├── BeApplication.java                   — @SpringBootApplication, @EnableScheduling
    ├── config/
    │   ├── RedisConfig.java                 — RedisTemplate<String,String> bean
    │   ├── TimezoneVerificationConfig.java  — Timezone setup on startup
    │   ├── TokenCleanupTask.java            — @Deprecated/DISABLED (commented out); refresh tokens now expire via Redis TTL
    │   ├── security/                        — ONLY legacy DISABLED files remain here (kept untouched for rollback)
    │   │   ├── CustomJwtDecoder.java        — @Deprecated/DISABLED (commented out); old OAuth2 Resource Server JwtDecoder
    │   │   └── JwtAuthenticationEntryPoint.java — @Deprecated/DISABLED (commented out); 401 now routed via handlerExceptionResolver
    │   └── swagger/
    │       ├── OpenApiConfig.java           — OpenAPI bean, global error responses, bearer scheme
    │       └── SwaggerExamples.java         — All Swagger @ExampleObject strings (constants only)
    ├── security/                            — Auth CONFIG/filter/handlers only (top-level package, moved out of config/security). Token & user-detail logic lives in service/ now.
    │   ├── SecurityConfig.java              — SecurityFilterChain, CORS, oauth2Login, PUBLIC_ENDPOINTS, jwtAuthenticationFilter
    │   ├── EncoderConfig.java               — BCryptPasswordEncoder bean (strength=10)
    │   ├── JwtProperties.java               — @ConfigurationProperties(jwt.*): signerKey, accessTokenExpiration, refreshTokenExpiration
    │   ├── JwtAuthenticationFilter.java     — OncePerRequestFilter: verify token per request → set SecurityContext (THE active auth mechanism)
    │   ├── CookieUtils.java                 — HttpOnly access + refresh cookie: set / clear / extract
    │   ├── CustomOAuth2User.java            — OAuth2User wrapper carrying email, name, picture
    │   ├── OAuth2AuthenticationFailureHandler.java — redirect FE with ?error=...
    │   └── OAuth2AuthenticationSuccessHandler.java — issue JWT, set refresh cookie, redirect FE
    ├── controller/
    │   ├── AuthenticationController.java    — POST /auth/{login,refresh,introspect,logout}, GET /auth/account-status
    │   ├── UserController.java              — /users: register, GET/PATCH me, me/profile, complete-profile (onboarding),
    │   │                                       me/deactivate-request, me/restore, forgot-password, verify-otp, reset-password,
    │   │                                       GET (list, ADMIN), GET /{userId} (ADMIN)
    │   ├── AdminController.java             — Admin-only user management (@PreAuthorize ROLE_ADMIN)
    │   └── FileController.java              — /files: POST avatar, POST documents, GET documents/signed-url (Supabase Storage)
    ├── dto/
    │   ├── request/
    │   │   ├── LoginRequest.java
    │   │   ├── RefreshTokenRequest.java     — body optional (refresh token now read from cookie)
    │   │   ├── IntrospectRequest.java
    │   │   ├── LogoutRequest.java           — body optional (access token blacklist only)
    │   │   ├── UserRegisterRequest.java
    │   │   ├── UpdateUserRequest.java       — partial profile update (fullName, phone, dob, avatarUrl)
    │   │   ├── CompleteProfileRequest.java  — onboarding: fullName, phone, dob, password, confirmPassword
    │   │   ├── ForgotPasswordRequest / VerifyOtpRequest / ResetPasswordRequest — OTP reset flow
    │   │   ├── OAuth2UserInfo.java          — internal carrier for Google profile → User mapping
    │   │   └── UserRequest.java
    │   └── response/
    │       ├── ApiResponse.java             — Universal response envelope {code, message, result}
    │       ├── AuthenticationResponse.java  — {token, authenticated}; refreshToken omitted from body
    │       ├── IntrospectResponse.java      — {valid}
    │       ├── MeResponse.java              — lightweight identity {id, email, role, avatarUrl, profileCompleted}
    │       ├── UserResponse.java            — full User details + nested RoleResponse
    │       ├── DeleteAccountResponse.java   — status + deletionDate + daysRemaining + message
    │       ├── FileUploadResponse.java      — {bucket, path, url} for Supabase uploads
    │       └── SignedUrlResponse.java       — {signedUrl, expiresInSeconds}
    ├── entity/
    │   ├── User.java                        — @Table("users"), UUID PK; password + phone nullable (Google users); fields: provider, googleId
    │   ├── Role.java                        — @Table("roles"), UUID PK, 1:N to User
    │   ├── RefreshToken.java                — @Table("refresh_tokens") entity still mapped, but UNUSED by current flow (refresh tracking moved to Redis)
    │   └── InvalidatedToken.java            — @Deprecated; ACCESS token blacklist migrated to Redis
    ├── exception/
    │   ├── AppException.java                — Runtime exception carrying an ErrorCode
    │   ├── ErrorCode.java                   — Enum: code (int) + message (String) + HttpStatus
    │   └── GlobalExceptionHandler.java      — @ControllerAdvice; handles AppException, validation, AccessDenied, catch-all Exception (NOTE: no AuthenticationException/JwtException handler → auth failures currently fall through to 500)
    ├── mapper/
    │   ├── UserMapper.java                  — MapStruct: UserRegisterRequest→User, User→{UserResponse, MeResponse},
    │   │                                       updateUser/completeProfile (@MappingTarget), toDeleteAccountResponse.
    │   │                                       MeResponse.profileCompleted maps from User.profile_completed (see §4 onboarding)
    │   └── OAuth2UserMapper.java            — MapStruct: OAuth2UserInfo→User, updateGoogleFields (@MappingTarget)
    ├── repository/
    │   ├── UserRepository.java
    │   ├── RoleRepository.java              — + findByRoleName(String)
    │   ├── RefreshTokenRepository.java      — @Deprecated/DISABLED (commented out); refresh tracking moved to Redis
    │   └── InvalidatedTokenRepository.java  — @Deprecated
    └── service/
        ├── AuthenticationService.java       — Interface
        ├── TokenBlacklistService.java       — Interface: Redis-backed ACCESS token blacklist
        ├── JwtService.java                  — Interface: generate (HS512) + parseClaims (verify signature + expiry)
        ├── RefreshTokenService.java         — Interface: REFRESH token JTI tracking in Redis (rt:{jti}, user_rt:{userId}, logout_time:{email})
        ├── UserService.java                 — Interface (register, updateMe, completeProfile, profile/me, delete/restore, OTP reset)
        ├── CustomOAuth2UserService.java     — Interface: Google profile → find/create User
        ├── EmailService.java                — Interface: OTP emails + account-setup-success email (HTML, branded, VN)
        ├── OtpService.java                  — Interface: Redis OTP storage/verify for password reset
        ├── SupabaseStorageService.java      — Interface: upload avatar/document, signed URLs (Supabase Storage REST)
        └── Impl/
            ├── AuthenticationServiceImpl.java
            ├── TokenBlacklistServiceImpl.java
            ├── JwtServiceImpl.java          — Nimbus JOSE implementation of JwtService
            ├── RefreshTokenServiceImpl.java — Redis (StringRedisTemplate) implementation of RefreshTokenService
            ├── UserDetailsServiceImpl.java  — Spring UserDetailsService: load user by email (no custom interface)
            ├── CustomOAuth2UserServiceImpl.java — THE active Google-user creator (password=null for fresh users)
            ├── EmailServiceImpl.java        — JavaMailSender; HTML templates; never emails plaintext passwords
            ├── OtpServiceImpl.java
            ├── SupabaseStorageServiceImpl.java
            └── UserServiceImpl.java
```

> **Note — `FileController` layering deviation:** unlike the other controllers (which are thin
> and delegate entirely to a `*Service`), `FileController` injects `UserRepository` directly and
> contains business logic (`currentUserId()` lookup, `pathFromPublicUrl()` URL parsing, and
> hand-built `FileUploadResponse`/`SignedUrlResponse` via `.builder()`). This is a known
> inconsistency with Rule 12/18 — see §7. Prefer moving identity resolution + response assembly
> into `SupabaseStorageService` for new file features.

---

## 3. Code Conventions

### Lombok pattern on service / controller classes
```java
@Service                                        // or @RestController
@RequiredArgsConstructor                        // constructor injection — no @Autowired
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)  // all injected fields final+private
public class FooServiceImpl implements FooService {
    // injected deps — no visibility modifier needed (FieldDefaults handles it)
    SomeRepository someRepository;

    // non-injected, configurable fields
    @NonFinal
    @Value("${some.property:default}")
    protected String SOME_PROPERTY;
}
```

### Config classes
`@Configuration` classes use **constructor injection** (`@RequiredArgsConstructor` with `private final` fields, e.g. `SecurityConfig`) or **`@Bean` method-parameter injection** (e.g. `RedisConfig`'s `redisTemplate(RedisConnectionFactory)`). Do not use field-level `@Autowired`.

### DTO pattern
All DTOs use `@Data @Builder @AllArgsConstructor @NoArgsConstructor @FieldDefaults(level = AccessLevel.PRIVATE)`.
Request DTOs carry Bean Validation annotations whose `message` is an `ErrorCode` enum key:
```java
@NotBlank(message = "USERNAME_REQUIRED")  // resolves to ErrorCode.USERNAME_REQUIRED
String username;
```

### Error handling
**Every service/`*Impl` method must guard its inputs and failure paths with explicit exception handling — never let an invalid value, missing entity, or duplicate flow through silently.**
1. **Validate request DTOs at the boundary.** All request DTOs carry Bean Validation annotations whose `message` is an `ErrorCode` enum key (see DTO pattern), and the controller param is annotated `@Valid`. Add a matching `ErrorCode` entry for every new validation message.
2. **Guard inside the service before acting:**
   - Lookups that can miss → `.orElseThrow(() -> new AppException(ErrorCode.X_NOT_EXISTED))` (e.g. `findByEmail`, `findById`).
   - Uniqueness / pre-conditions → check then `throw new AppException(ErrorCode.X_EXISTED)` (e.g. `existsByUsername`, `existsByEmail`).
   - Business-state checks (locked account, OTP expired, password mismatch, etc.) → dedicated `ErrorCode`.
3. Service throws `new AppException(ErrorCode.SOME_CODE)`; if a new condition has no code yet, **add one to the `ErrorCode` enum** (code + message + `HttpStatus`) first.
4. `GlobalExceptionHandler.handlingAppException()` catches it and returns `ApiResponse` with `code` and `message` from `ErrorCode`.
5. **Never throw raw exceptions to the controller** and never return an error by hand-building `ApiResponse` — always go through `AppException` + `ErrorCode` so the response envelope and HTTP status stay consistent.
6. When wrapping risky calls in `try/catch`, log with `@Slf4j` and either rethrow as an `AppException` or fall back deliberately — do not swallow the exception silently.

### Response envelope
Every endpoint returns `ApiResponse<T>`:
```java
// success with payload
return ApiResponse.<UserResponse>builder().result(result).build();
// or
return ApiResponse.success("message", result);

// void success
return ApiResponse.<Void>builder().build();
```
`@JsonInclude(NON_NULL)` on `ApiResponse` — absent fields are omitted from JSON.

### Swagger
- All `@ExampleObject` strings live as `public static final String` constants in `SwaggerExamples`.
- Public endpoints carry `@SecurityRequirements({})` to remove the lock icon in Swagger UI.
- `@Operation`, `@io.swagger.v3.oas.annotations.parameters.RequestBody`, and `@ApiResponse` are on every endpoint.
- `OpenApiConfig.globalErrorResponses()` automatically injects 400/401/403/500 responses on all operations.

### MapStruct
```java
@Mapper(componentModel = "spring")  // always spring component model
public interface UserMapper { ... }
```
**Always map entity → DTO (and DTO → entity) through a MapStruct mapper method — never hand-build a DTO/entity with its Lombok `.builder()` / setters inside a service.** This applies to **every CRUD operation** (Create, Read, Update, Delete) on every entity, not just reads:
- **Create:** map the request DTO → entity via the mapper (e.g. `userMapper.toUser(request)`), then set only the fields the mapper can't (password hash, role lookup, status, etc.).
- **Read:** map entity → response DTO via the mapper (e.g. `toUserResponse`, `toMeResponse`, `toUserResponseList`).
- **Update:** use a MapStruct `void update(SourceDto dto, @MappingTarget Entity entity)` method (with `@BeanMapping(nullValuePropertyMappingStrategy = IGNORE)` for partial updates) instead of manually calling setters.
- **Delete:** map the affected entity → response DTO via the mapper when returning the deleted record.

If a target DTO has no mapper method yet, add one to the appropriate mapper (e.g. `UserMapper.toMeResponse(User)`) and call it from the service. Use `@Mapping(target = ..., source = ...)` for fields that differ in name or are nested (e.g. `@Mapping(target = "role", source = "role.roleName")` to flatten a nested entity field to a `String`).

---

## 4. Authentication Flow

> **Architecture:** Tokens are verified by a hand-written `JwtAuthenticationFilter` + `JwtService`, **NOT** by Spring's OAuth2 Resource Server. Auth **config/filter/handlers** live in the top-level package **`su26.uml.be.security`** (moved out of `config/security`); the **token & user-detail logic** follows the project's interface+Impl convention in **`service/`** (`JwtService`, `RefreshTokenService`) + **`service/Impl/`** (`JwtServiceImpl`, `RefreshTokenServiceImpl`, `UserDetailsServiceImpl`). The DISABLED legacy files `CustomJwtDecoder` and `JwtAuthenticationEntryPoint` remain in `config/security` untouched. `CustomJwtDecoder`, `JwtAuthenticationEntryPoint`, the `refresh_tokens` table, `RefreshTokenRepository`, and `TokenCleanupTask` are all commented out / unused. Refresh-token state lives in **Redis**, not the DB.

### ACCESS token structure (HS512, signed with `jwt.signerKey`)

```
sub (= email), iss="DiaUML-Studio", iat, exp, jti,
uid (= userId), email, typ="access",
scope="ROLE_<roleName>", role="ROLE_<roleName>"
```
- TTL: `jwt.accessTokenExpiration` seconds (default 3600 = 1 hour).
- Delivered to the client as an **HttpOnly cookie** (`access_token`) AND accepted from the `Authorization: Bearer <token>` header (Swagger support).
- Blacklist: Redis key `blacklist:{jti}` with TTL = remaining lifetime.

### REFRESH token (also a JWT, `typ="refresh"`)

A signed HS512 JWT (same `JwtService`) sent to the client via **HttpOnly cookie** (`refresh_token`).
Its `jti` is tracked in Redis (`rt:{jti}` → userId, plus a `user_rt:{userId}` set index).
There is **no SHA-256 hash, no DB row, and no token family** — rotation works purely via the Redis `rt:{jti}` entry. Reuse of a rotated/revoked token fails because its `jti` is no longer in Redis → 401.

### Login — `POST /auth/login`
1. Find user **by email** (`LoginRequest.email`) → `USER_NOT_FOUND` if missing.
2. BCrypt password check → `INVALID_CREDENTIALS` if wrong.
3. Status check: `"LOCKED"` → `USER_INACTIVE`.
4. Update `user.lastActiveAt`, save.
5. Generate ACCESS token + REFRESH token (new random `jti`); store `jti` in Redis (`RefreshTokenService.store`).
6. Set both access + refresh tokens as HttpOnly cookies via `CookieUtils`.
7. Return `{token, authenticated: true}` — no `refreshToken` in body.

### Google OAuth2 Login — `GET /oauth2/authorization/google` (Spring-managed redirect)
1. Spring redirects to Google consent screen.
2. Google redirects to `/login/oauth2/code/google`.
3. `CustomOAuth2UserServiceImpl` loads Google profile, finds or creates a local `User` (links by email if existing, default role = `"USER"`).
4. `OAuth2AuthenticationSuccessHandler`: generates ACCESS + REFRESH tokens, sets cookies, redirects FE to `FRONTEND_CALLBACK_URL?login=success`.
5. `OAuth2AuthenticationFailureHandler`: redirects FE to `FRONTEND_CALLBACK_URL?error=<message>`.
6. FE must call `POST /auth/refresh` to get the access token after redirect.

### Onboarding (first-time Google users) — `PATCH /users/complete-profile` (authenticated)
A fresh Google user is created with **no real password** (`CustomOAuth2UserServiceImpl` sets
`password = null`; the dead fallback in `OAuth2AuthenticationSuccessHandler` historically wrote the
placeholder `"GOOGLE_OAUTH2_USER"`). They must finish a one-time onboarding before using the app.

- **Completion flag — explicit `User.profile_completed` BOOLEAN column** (source of truth; mapped
  straight onto `MeResponse.profileCompleted`). Set explicitly everywhere a user is created:
  register/admin → `true`; fresh Google user (`CustomOAuth2UserServiceImpl`, and the success-handler
  fallback) → `false`; onboarding completion → `true`. The FE gates on `MeResponse.profileCompleted`
  (GET `/users/me`): `=== false` → onboarding wizard, otherwise → app. Legacy rows are backfilled
  once on startup (`DataInitializer`, keyed on `provider`/`lastPasswordChangeAt`, NOT password —
  fresh Google users may carry a random BCrypt hash so the password is not a reliable signal).
- **Endpoint:** `UserServiceImpl.completeProfile(email, CompleteProfileRequest)`:
  1. `findByEmail` → `USER_NOT_EXISTED`.
  2. Guard one-time: if `Boolean.TRUE.equals(user.getProfileCompleted())` → `PROFILE_ALREADY_COMPLETED`.
  3. Re-validate server-side (never trust FE): `password == confirmPassword` else `PASSWORDS_NOT_MATCH`;
     password strength ≥ **medium** (≥3 of {len≥8, upper, lower, digit, special}) **and** length ≥ 8 else `WEAK_PASSWORD`.
  4. `userMapper.completeProfile(...)` maps fullName/phone/dob; service sets BCrypt password + `lastPasswordChangeAt` + `profileCompleted = true`.
  5. Best-effort `EmailService.sendAccountSetupSuccessEmail` (try/catch, logged — onboarding still succeeds if SMTP fails).
     The email **never** contains the plaintext password — only a confirmation + a "forgot password" link (`app.frontend.base-url`).
  6. Returns `UserResponse`.
- **New ErrorCodes:** `WEAK_PASSWORD` (1074), `PROFILE_ALREADY_COMPLETED` (1075), `DOB_REQUIRED` (1076).

### Refresh — `POST /auth/refresh` (public)
1. Read refresh JWT from HttpOnly cookie (`AUTH_COOKIE_NAME`) → `UNAUTHENTICATED` if missing.
2. `JwtService.parseClaims` (verify signature + expiry) and check `typ == "refresh"`.
3. Look up `jti` in Redis (`getUserIdByJti`) → `UNAUTHENTICATED` if absent (already rotated/revoked).
4. Load user, reject if `"LOCKED"`.
5. **Rotate:** revoke old `jti` in Redis, generate new ACCESS + REFRESH (new `jti`), store new `jti`.
6. Set new access + refresh cookies, return `{token, authenticated: true}`.

### Introspect — `POST /auth/introspect` (public)
- Calls `JwtService.parseClaims` only — verifies **signature + expiry**.
- ⚠️ Does **NOT** check the Redis blacklist or logout-time → a logged-out/blacklisted token can still return `{valid: true}`. (Known inconsistency vs. the request filter.)
- Returns `{valid: true|false}` — never throws to the caller.

### Logout — `POST /auth/logout` (public)
1. Read refresh JWT from cookie → `parseClaims` → `RefreshTokenService.revoke(jti)` + `setLogoutTime(email)`.
2. Clear both access + refresh cookies via `CookieUtils`.
3. If an access token is provided in the body (`LogoutRequest`): blacklist its `jti` in Redis.

### Protected request flow (`JwtAuthenticationFilter`)
```
Request → JwtAuthenticationFilter (addFilterBefore UsernamePasswordAuthenticationFilter)
       → skip if path is /auth/login, /auth/refresh, /users/register
       → extract token from access_token cookie, else Authorization: Bearer header
       → JwtService.parseClaims()           ← verify signature + expiry
       → require claim typ == "access"
       → TokenBlacklistService.isBlacklisted(jti)         ← Redis check (clears cookies if hit)
       → RefreshTokenService.getLogoutTime(email) vs iat  ← revoked-by-logout check
       → UserDetailsServiceImpl.loadUserByUsername(email) → set SecurityContext (if user enabled)
       → @EnableMethodSecurity enforces @PreAuthorize at method level
   On parse error: clear cookies + handlerExceptionResolver.resolveException(...)
```
Unauthenticated / token error → `SecurityConfig` `authenticationEntryPoint` delegates to `handlerExceptionResolver` (→ `GlobalExceptionHandler`).
> ✅ **Resolved:** `GlobalExceptionHandler` now defines `@ExceptionHandler(AuthenticationException.class)` → `ErrorCode.UNAUTHENTICATED` (401) and `@ExceptionHandler(AccessDeniedException.class)` → `ErrorCode.UNAUTHORIZED` (403), so authentication/authorization failures return clean JSON envelopes. The disabled `JwtAuthenticationEntryPoint` is fully superseded. Edge case: a raw `JwtException` not wrapped in an `AuthenticationException` still hits the catch-all 500 — keep token-parse failures wrapped.

### Scheduled cleanup
- `TokenCleanupTask` is disabled — Redis TTL handles expiry of both `blacklist:{jti}` and `rt:{jti}` keys automatically.

---

## 5. Database & Cache

### PostgreSQL (`application.yaml` + `.env`)
```
Host:     DB_HOST:DB_PORT (default: localhost:5432)
Database: DB_NAME
Username: DB_USERNAME
Password: DB_PASSWORD
DDL:      hibernate.ddl-auto=update
Dialect:  PostgreSQLDialect
Timezone: APP_TIMEZONE (e.g. Asia/Ho_Chi_Minh)
```

### Redis
```
Host:    REDIS_HOST (default: localhost)
Port:    REDIS_PORT (default: 6379)
Timeout: REDIS_TIMEOUT (default: 2000ms)
```
Used for:
- ACCESS token blacklist — `blacklist:{jti}` keys, TTL = remaining token lifetime.
- REFRESH token tracking — `rt:{jti}` → userId, `user_rt:{userId}` set index, TTL = refresh token lifetime.
- Logout/password-change marker — `logout_time:{email}` (compared against token `iat` to revoke sessions).

Run both via Docker: `docker compose up -d`.

### Entities

**`users` table — `User.java`**
| Column | Type | Notes |
|---|---|---|
| `user_id` | UUID (PK) | `@UuidGenerator`, not updatable |
| `user_name` | VARCHAR | unique, not null |
| `password` | VARCHAR | BCrypt hash; **nullable** (Google users have no password) |
| `full_name` | VARCHAR(100) | not null |
| `email` | VARCHAR | unique, not null |
| `phone` | VARCHAR | **nullable** (Google users may not provide phone) |
| `provider` | VARCHAR(20) | nullable; `"google"` for OAuth2 users |
| `google_id` | VARCHAR | unique, nullable |
| `role_id` | UUID (FK→roles) | not null |
| `status` | VARCHAR | `UserStatus` enum: `ACTIVE`, `LOCKED`, `PENDING_DELETE` |
| `deletion_date` | TIMESTAMP | nullable; set when status = `PENDING_DELETE` (30-day grace) |
| `avatar_url` | VARCHAR(500) | nullable |
| `dob` | DATE | nullable (date of birth) |
| `last_active_at` | TIMESTAMP | nullable |
| `last_password_change_at` | TIMESTAMP | nullable; set on password set/change/onboarding |
| `profile_completed` | BOOLEAN | nullable in DB; `true` for normal-register/admin/onboarded users, `false` for fresh Google users who must onboard (see §4 Onboarding) |
| `created_at` | TIMESTAMP | `@CreationTimestamp`, not updatable |
| `updated_at` | TIMESTAMP | `@UpdateTimestamp` |

> `profile_completed` is set explicitly on every creation path (register/admin → `true`,
> fresh Google user → `false`, onboarding → `true`). Rows created before the column existed are
> backfilled once on startup by `DataInitializer.backfillProfileCompleted()`.

**`roles` table — `Role.java`**
| Column | Type | Notes |
|---|---|---|
| `role_id` | UUID (PK) | `@UuidGenerator` |
| `role_name` | VARCHAR | unique, not null |
| `description` | VARCHAR(1000) | nullable |

**`refresh_tokens` table — `RefreshToken.java` (UNUSED by current flow)**
> The entity is still mapped (Hibernate may create the table via `ddl-auto: update`), but **no active code reads or writes it** — refresh-token state now lives in Redis (`rt:{jti}`). Columns below are legacy; kept for rollback safety.
| Column | Type | Notes |
|---|---|---|
| `id` | UUID (PK) | `@UuidGenerator` |
| `token_hash` | VARCHAR(64) | legacy SHA-256 scheme (unused) |
| `family_id` | UUID | legacy family scheme (unused) |
| `user_id` | UUID | FK to users (not @JoinColumn, just UUID) |
| `expiry_time` | INSTANT | token expiry |
| `used` | BOOLEAN | legacy (unused) |
| `revoked` | BOOLEAN | legacy (unused) |
| `created_at` | TIMESTAMP | `@CreationTimestamp` |

**`invalidated_token` table — `InvalidatedToken.java` (@Deprecated)**
| Column | Type | Notes |
|---|---|---|
| `iD` | VARCHAR (PK) | JWT ID (JTI claim) |
| `expiry_time` | TIMESTAMP | legacy; ACCESS blacklist now in Redis |

> Do **not** delete `InvalidatedToken` entity or its table — kept for rollback safety.

---

## 6. Commands

```bash
# Start PostgreSQL (Docker)
docker compose up -d

# Run application
./mvnw.cmd spring-boot:run          # Windows
./mvnw spring-boot:run              # macOS / Linux

# Build JAR
./mvnw.cmd package -DskipTests
./mvnw package -DskipTests

# Run tests
./mvnw.cmd test
./mvnw test
```

**Runtime URLs (default `.env`):**
```
Base URL:    http://localhost:8088/api/uml
Swagger UI:  http://localhost:8088/api/uml/swagger-ui.html
OpenAPI JSON: http://localhost:8088/api/uml/v3/api-docs
Health:      http://localhost:8088/api/uml/actuator/health
```

**Required `.env` variables:**
```
APP_NAME, SERVER_PORT, CONTEXT_PATH
DB_HOST, DB_PORT, DB_NAME, DB_USERNAME, DB_PASSWORD
APP_TIMEZONE, JWT_SIGNER_KEY
GOOGLE_CLIENT_ID, GOOGLE_CLIENT_SECRET
FRONTEND_CALLBACK_URL          # e.g. http://localhost:5173/auth/google/callback
MAIL_USERNAME, MAIL_PASSWORD   # Gmail SMTP (spring.mail) — OTP + account-setup emails
SUPABASE_URL, SUPABASE_SERVICE_KEY  # Supabase Storage (service_role key, backend-only)
# Optional overrides (have defaults in application.yaml):
FRONTEND_BASE_URL              # default http://localhost:5173 (used for the "forgot password" link in onboarding email)
JWT_ACCESS_TOKEN_EXPIRATION    # default 3600 s
JWT_REFRESH_TOKEN_EXPIRATION   # default 604800 s
REDIS_HOST                     # default localhost
REDIS_PORT                     # default 6379
REDIS_TIMEOUT                  # default 2000ms
AUTH_COOKIE_NAME               # default refresh_token
AUTH_COOKIE_SECURE             # default false (set true in production HTTPS)
AUTH_COOKIE_SAME_SITE          # default Lax (use None + secure=true for cross-site)
AUTH_COOKIE_PATH               # default /
```

**Google Cloud Console — redirect URI to register:**
```
http://localhost:8088/api/uml/login/oauth2/code/google
```

---

## 7. Rules

1. **Never rename** existing packages, classes, or public interfaces — `AuthenticationService`, `UserService`, `ErrorCode` enum keys, etc. are referenced in many places.

2. **ErrorCode enum keys are validation message keys.** Every `@NotBlank(message="KEY")` maps directly to `ErrorCode.KEY`. Adding a new validation requires a matching `ErrorCode` entry.

3. **Always use `ApiResponse<T>` as the return type** for every controller method. Never return raw types or `ResponseEntity` directly.

4. **`@JsonInclude(NON_NULL)` on `ApiResponse` is intentional.** Do not remove it; absent fields must be omitted from JSON output.

5. **JWT signing algorithm is HS512** with `SIGNER_KEY`. Do not change the algorithm or the `JWSAlgorithm.HS512` constant.

6. **Token verification is hand-written** via `JwtAuthenticationFilter` + `JwtService.parseClaims` — the app is **not** configured as an OAuth2 Resource Server. The access-token type claim is `typ` (value `"access"`), and login/identity is keyed on **email** (`sub` = email). `CustomJwtDecoder` / `JwtAuthenticationEntryPoint` are disabled (commented out); do not assume Spring's `JwtDecoder`/`JwtAuthenticationConverter` pipeline is active.

7. **Swagger example strings belong in `SwaggerExamples`** as public static final constants. Do not inline raw JSON strings in annotation attributes on controllers.

8. **`@SecurityRequirements({})` is required on every public endpoint** in the controller to remove the Swagger bearer lock icon. Missing it will cause Swagger UI to require a token on that operation.

9. **`invalidated_token.iD` field name is intentional** (capital D in `iD`). Do not rename — the entity is `@Deprecated` but still mapped to the DB for rollback safety.

10. **`ddl-auto: update` is active.** Adding new entity fields will auto-create columns on startup. Do not use `create` or `create-drop` in any environment that has existing data.

11. **Service implementations live in `service/Impl/`**, not in `service/`. The pattern `FooService` (interface) + `FooServiceImpl` (class in `Impl/`) must be maintained.

12. **Constructor injection via `@RequiredArgsConstructor`** is the convention for service and controller classes. Do not add field-level `@Autowired` to these classes.

13. **Refresh token is a signed JWT (`typ="refresh"`), tracked by `jti` in Redis** (`RefreshTokenService` / `RefreshTokenServiceImpl`, key `rt:{jti}`). There is no DB row, SHA-256 hash, or token family. The `refresh_tokens` table / `RefreshToken` entity / `RefreshTokenRepository` are unused legacy. Never log or return the raw refresh token except as the HttpOnly cookie value.

14. **`password` and `phone` are nullable on `User`.** Always null-check before using them. Google OAuth2 users will not have either field set.

15. **ACCESS token blacklist lives in Redis, not the DB.** Use `TokenBlacklistService` — never write to `invalidated_token` table for new code.

16. **Use constructor injection everywhere — no field-level `@Autowired`.** Service/controller classes use `@RequiredArgsConstructor` (+ `@FieldDefaults(makeFinal = true)`); `@Configuration` classes use `@RequiredArgsConstructor` with `private final` fields (`SecurityConfig`) or `@Bean` method-parameter injection (`RedisConfig`).

17. **`authenticate()`, `refreshToken()`, `generateTokenForOAuth2User()` in `AuthenticationServiceImpl` are `@Transactional`** — they update `user.lastActiveAt` and write refresh-token state to Redis. Do not call them from another `@Transactional` context that you want to keep separate.

18. **Use MapStruct for all entity↔DTO conversion across the full CRUD surface — never hand-build DTOs/entities in services.** Applies to Create, Read, Update, and Delete on every entity. Do not construct a response/entity with Lombok `.builder()` or setters inside a service method when a mapper can do it: requests → entity on create, entity → response on read/delete, and a `@MappingTarget` update method on update. Add (or reuse) a method on the relevant MapStruct mapper (`@Mapper(componentModel = "spring")`) and call it. Use `@Mapping` for renamed/nested fields (e.g. `source = "role.roleName"`).

19. **Always handle errors via `AppException` + `ErrorCode` — guard inputs and failure paths in every service method.** Validate request DTOs at the boundary with Bean Validation (`@Valid` + `message = "ERROR_CODE_KEY"`); inside the `*Impl`, guard every lookup (`.orElseThrow(() -> new AppException(...))`), uniqueness/pre-condition, and business-state check by throwing `AppException(ErrorCode.X)`. Add a new `ErrorCode` entry (code + message + `HttpStatus`) for any new condition. Never throw raw exceptions to the controller, never hand-build an error `ApiResponse`, and never swallow a caught exception silently.

20. **Auth-failure responses return a clean 401 UNAUTHENTICATED.** `GlobalExceptionHandler` now has `@ExceptionHandler(AuthenticationException.class)` → `ErrorCode.UNAUTHENTICATED` (and an `AccessDeniedException` → `UNAUTHORIZED` handler), fully replacing the disabled `JwtAuthenticationEntryPoint`. A raw `JwtException` thrown outside an `AuthenticationException` still falls through to the catch-all 500, so wrap token-parse failures in an `AuthenticationException` (or `AppException`) where possible.

21. **Controllers must be thin — delegate to a `*Service`; do not inject repositories.** `currentUserId(userDetails)` resolution, URL/path parsing, and response assembly belong in the service layer (every controller except `FileController` follows this). `FileController` is a **known deviation** (injects `UserRepository`, hand-builds `FileUploadResponse`/`SignedUrlResponse`) — do not copy its shape; for new file features push that logic into `SupabaseStorageService`. Note: hand-building those two response DTOs is borderline vs Rule 18 because their source is a plain `String` from storage (no JPA entity to map), but the layering point above still applies.

22. **Onboarding completion is the explicit `User.profile_completed` BOOLEAN column.** Set it on every creation path (register/admin `true`, fresh Google user `false`, onboarding `true`); it maps straight onto `MeResponse.profileCompleted` and the guard in `completeProfile` is `Boolean.TRUE.equals(...)`. Do NOT reintroduce a password-based heuristic — fresh Google users may carry a random BCrypt hash, so `password != null` is not a reliable "completed" signal (this was the bug that sent first-time Google users straight to the dashboard). Still re-validate password match + strength (≥ medium, length ≥ 8) on the server, and never put a plaintext password in any email.
