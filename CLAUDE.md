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
| Spring Boot Starter Data Redis | (Boot-managed) — ACCESS token blacklist |
| Spring Boot OAuth2 Resource Server | (Boot-managed) — brings in Nimbus JOSE JWT |
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
    │   ├── TokenCleanupTask.java            — @Scheduled 03:00 daily: prune expired refresh_tokens
    │   ├── security/
    │   │   ├── CookieUtils.java             — HttpOnly refresh-token cookie: set / clear / extract
    │   │   ├── CustomJwtDecoder.java        — JwtDecoder bean; Redis blacklist + tokenType check
    │   │   ├── CustomOAuth2User.java        — OAuth2User wrapper carrying email, name, picture
    │   │   ├── EncoderConfig.java           — BCryptPasswordEncoder bean (strength=10)
    │   │   ├── JwtAuthenticationEntryPoint.java — 401 JSON for unauthenticated access
    │   │   ├── OAuth2AuthenticationFailureHandler.java — redirect FE with ?error=...
    │   │   ├── OAuth2AuthenticationSuccessHandler.java — issue JWT, set refresh cookie, redirect FE
    │   │   └── SecurityConfig.java          — SecurityFilterChain, CORS, oauth2Login, PUBLIC_ENDPOINTS
    │   └── swagger/
    │       ├── OpenApiConfig.java           — OpenAPI bean, global error responses, bearer scheme
    │       └── SwaggerExamples.java         — All Swagger @ExampleObject strings (constants only)
    ├── controller/
    │   ├── AuthenticationController.java    — POST /auth/{login,refresh,introspect,logout}, GET /auth/account-status
    │   └── UserController.java              — POST /users/register
    ├── dto/
    │   ├── request/
    │   │   ├── LoginRequest.java
    │   │   ├── RefreshTokenRequest.java     — body optional (refresh token now read from cookie)
    │   │   ├── IntrospectRequest.java
    │   │   ├── LogoutRequest.java           — body optional (access token blacklist only)
    │   │   ├── UserRegisterRequest.java
    │   │   └── UserRequest.java
    │   └── response/
    │       ├── ApiResponse.java             — Universal response envelope {code, message, result}
    │       ├── AuthenticationResponse.java  — {token, authenticated}; refreshToken omitted from body
    │       ├── IntrospectResponse.java      — {valid}
    │       └── UserResponse.java            — User details + nested RoleResponse
    ├── entity/
    │   ├── User.java                        — @Table("users"), UUID PK; password + phone nullable (Google users); fields: provider, googleId
    │   ├── Role.java                        — @Table("roles"), UUID PK, 1:N to User
    │   ├── RefreshToken.java                — @Table("refresh_tokens"): opaque token hash + family-based reuse detection
    │   └── InvalidatedToken.java            — @Deprecated; ACCESS token blacklist migrated to Redis
    ├── exception/
    │   ├── AppException.java                — Runtime exception carrying an ErrorCode
    │   ├── ErrorCode.java                   — Enum: code (int) + message (String) + HttpStatus
    │   └── GlobalExceptionHandler.java      — @ControllerAdvice; handles AppException, validation, AccessDenied
    ├── mapper/
    │   └── UserMapper.java                  — MapStruct: UserRegisterRequest→User, User→UserResponse
    ├── repository/
    │   ├── UserRepository.java
    │   ├── RoleRepository.java              — + findByRoleName(String)
    │   ├── RefreshTokenRepository.java      — findByTokenHash, revokeFamily(familyId), deleteExpired(now)
    │   └── InvalidatedTokenRepository.java  — @Deprecated
    └── service/
        ├── AuthenticationService.java       — Interface
        ├── TokenBlacklistService.java       — Interface: Redis-backed ACCESS token blacklist
        ├── UserService.java                 — Interface
        ├── CustomOAuth2UserService.java     — Interface: Google profile → find/create User
        └── Impl/
            ├── AuthenticationServiceImpl.java
            ├── TokenBlacklistServiceImpl.java
            ├── CustomOAuth2UserServiceImpl.java
            └── UserServiceImpl.java
```

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

### Config classes use field-level `@Autowired`
```java
@Autowired
private CustomJwtDecoder customJwtDecoder;
```

### DTO pattern
All DTOs use `@Data @Builder @AllArgsConstructor @NoArgsConstructor @FieldDefaults(level = AccessLevel.PRIVATE)`.
Request DTOs carry Bean Validation annotations whose `message` is an `ErrorCode` enum key:
```java
@NotBlank(message = "USERNAME_REQUIRED")  // resolves to ErrorCode.USERNAME_REQUIRED
String username;
```

### Error handling
1. Service throws `new AppException(ErrorCode.SOME_CODE)`.
2. `GlobalExceptionHandler.handlingAppException()` catches it and returns `ApiResponse` with `code` and `message` from `ErrorCode`.
3. Never throw raw exceptions to the controller.

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

---

## 4. Authentication Flow

### ACCESS token structure (HS512, signed with `jwt.signerKey`)

```
sub (= username), iss="DiaUML-Studio", iat, exp, jti, userID, fullName, email,
scope="ROLE_<roleName>", role="ROLE_<roleName>", tokenType="ACCESS"
```
TTL: `jwt.accessTokenExpiration` seconds (default 3600 = 1 hour).
Blacklist: Redis key `blacklist:{jti}` with TTL = remaining lifetime.

### REFRESH token (opaque — NOT a JWT)

A 256-bit Base64url random string sent to the client via **HttpOnly cookie** (`refresh_token`).
Server stores only the SHA-256 hash in the `refresh_tokens` table alongside a `family_id`.
Each login creates a new family; each refresh rotates within the same family.
Reuse of an already-used/revoked token triggers full family revocation → 401.

### Login — `POST /auth/login`
1. Find user by username → `USER_NOT_FOUND` if missing.
2. BCrypt password check → `INVALID_CREDENTIALS` if wrong.
3. Status check: `"LOCKED"` → `USER_INACTIVE`.
4. Update `user.lastActiveAt`, save.
5. Generate ACCESS token (JWT) + opaque REFRESH token (new family).
6. Set refresh token as HttpOnly cookie via `CookieUtils`.
7. Return `{token, authenticated: true}` — no `refreshToken` in body.

### Google OAuth2 Login — `GET /oauth2/authorization/google` (Spring-managed redirect)
1. Spring redirects to Google consent screen.
2. Google redirects to `/login/oauth2/code/google`.
3. `CustomOAuth2UserServiceImpl` loads Google profile, finds or creates a local `User` (links by email if existing, default role = `"USER"`).
4. `OAuth2AuthenticationSuccessHandler`: generates ACCESS token + opaque REFRESH, sets cookie, redirects FE to `FRONTEND_CALLBACK_URL?login=success`.
5. `OAuth2AuthenticationFailureHandler`: redirects FE to `FRONTEND_CALLBACK_URL?error=<message>`.
6. FE must call `POST /auth/refresh` to get the access token after redirect.

### Refresh — `POST /auth/refresh` (public)
1. Read opaque refresh token from HttpOnly cookie (cookie name = `AUTH_COOKIE_NAME`).
2. Compute SHA-256 → look up `refresh_tokens` by hash.
3. If `used` or `revoked` → **reuse detected** → `revokeFamily(familyId)` → 401.
4. Mark old record `used=true`, create new record in same family.
5. Set new refresh cookie, return `{token, authenticated: true}`.

### Introspect — `POST /auth/introspect` (public)
- Checks Redis blacklist + tokenType == "ACCESS" + signature + expiry.
- Returns `{valid: true|false}` — never throws to the caller.

### Logout — `POST /auth/logout` (public)
1. Read refresh token from cookie → `revokeFamily(familyId)` (invalidates all family tokens in DB).
2. Clear refresh cookie via `CookieUtils`.
3. If access token provided in body (`LogoutRequest`): add JTI to Redis blacklist.

### Protected request flow
```
Request → SecurityFilterChain
       → CustomJwtDecoder.decode()
           → TokenBlacklistService.isBlacklisted(jti)   ← Redis check
           → verifies tokenType == "ACCESS"
           → NimbusJwtDecoder.decode()
       → JwtAuthenticationConverter
           → reads claim "role" as authority (no added prefix, empty authorityPrefix)
       → @EnableMethodSecurity enforces @PreAuthorize on method level
```
Unauthenticated → `JwtAuthenticationEntryPoint` → JSON `{code:401, message:"..."}`.

### Scheduled cleanup — `TokenCleanupTask` (daily at 03:00)
- Deletes expired rows from `refresh_tokens`.
- (Redis TTL handles `blacklist:*` keys automatically — no manual cleanup needed.)

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
Used exclusively for ACCESS token blacklist (`blacklist:{jti}` keys, TTL = remaining token lifetime).

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
| `status` | VARCHAR | `"ACTIVE"` or `"LOCKED"` |
| `avatar_url` | VARCHAR(500) | nullable |
| `last_active_at` | TIMESTAMP | nullable |
| `last_password_change_at` | TIMESTAMP | nullable |
| `created_at` | TIMESTAMP | `@CreationTimestamp`, not updatable |
| `updated_at` | TIMESTAMP | `@UpdateTimestamp` |

**`roles` table — `Role.java`**
| Column | Type | Notes |
|---|---|---|
| `role_id` | UUID (PK) | `@UuidGenerator` |
| `role_name` | VARCHAR | unique, not null |
| `description` | VARCHAR(1000) | nullable |

**`refresh_tokens` table — `RefreshToken.java`**
| Column | Type | Notes |
|---|---|---|
| `id` | UUID (PK) | `@UuidGenerator` |
| `token_hash` | VARCHAR(64) | SHA-256 hex of the opaque token; unique |
| `family_id` | UUID | groups tokens for one login session |
| `user_id` | UUID | FK to users (not @JoinColumn, just UUID) |
| `expiry_time` | INSTANT | token expiry |
| `used` | BOOLEAN | true after this token has been rotated |
| `revoked` | BOOLEAN | true when family is revoked (logout / reuse detected) |
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
# Optional overrides (have defaults in application.yaml):
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

6. **The `role` claim in the ACCESS token must stay named `"role"`** — `JwtGrantedAuthoritiesConverter.setAuthoritiesClaimName("role")` depends on it. The authority prefix is empty string (no extra `ROLE_` prepended by Spring).

7. **Swagger example strings belong in `SwaggerExamples`** as public static final constants. Do not inline raw JSON strings in annotation attributes on controllers.

8. **`@SecurityRequirements({})` is required on every public endpoint** in the controller to remove the Swagger bearer lock icon. Missing it will cause Swagger UI to require a token on that operation.

9. **`invalidated_token.iD` field name is intentional** (capital D in `iD`). Do not rename — the entity is `@Deprecated` but still mapped to the DB for rollback safety.

10. **`ddl-auto: update` is active.** Adding new entity fields will auto-create columns on startup. Do not use `create` or `create-drop` in any environment that has existing data.

11. **Service implementations live in `service/Impl/`**, not in `service/`. The pattern `FooService` (interface) + `FooServiceImpl` (class in `Impl/`) must be maintained.

12. **Constructor injection via `@RequiredArgsConstructor`** is the convention for service and controller classes. Do not add field-level `@Autowired` to these classes.

13. **Refresh token is opaque — never a JWT.** The raw token is never stored on the server; only its SHA-256 hex hash is persisted. Never log or return the raw token except as the HttpOnly cookie value.

14. **`password` and `phone` are nullable on `User`.** Always null-check before using them. Google OAuth2 users will not have either field set.

15. **ACCESS token blacklist lives in Redis, not the DB.** Use `TokenBlacklistService` — never write to `invalidated_token` table for new code.

16. **Config classes (`SecurityConfig`, `RedisConfig`) use field-level `@Autowired`**, not constructor injection. All other classes use `@RequiredArgsConstructor`.

17. **`authenticate()`, `refreshToken()`, `generateTokenForOAuth2User()` in `AuthenticationServiceImpl` are `@Transactional`** — they write to `refresh_tokens`. Do not call them from another `@Transactional` context that you want to keep separate.
