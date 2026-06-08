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
| Spring Boot OAuth2 Resource Server | (Boot-managed) — brings in Nimbus JOSE JWT |
| Spring Boot OAuth2 Client | (Boot-managed) |
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
├── docker-compose.yml
├── .env / .env.example
└── src/main/java/su26/uml/be/
    ├── BeApplication.java                   — @SpringBootApplication entry point
    ├── config/
    │   ├── CustomJwtDecoder.java            — JwtDecoder bean; calls introspect() on every protected request
    │   ├── EncoderConfig.java               — BCryptPasswordEncoder bean (strength=10)
    │   ├── JwtAuthenticationEntryPoint.java — 401 JSON response for unauthenticated access
    │   ├── SecurityConfig.java              — SecurityFilterChain, CORS, PUBLIC_ENDPOINTS list
    │   ├── TimezoneVerificationConfig.java  — Timezone setup on startup
    │   └── swagger/
    │       ├── OpenApiConfig.java           — OpenAPI bean, global error responses, bearer scheme
    │       └── SwaggerExamples.java         — All Swagger @ExampleObject strings (constants only)
    ├── controller/
    │   ├── AuthenticationController.java    — POST /auth/{login,refresh,introspect,logout}, GET /auth/account-status
    │   └── UserController.java             — POST /users/register
    ├── dto/
    │   ├── request/
    │   │   ├── LoginRequest.java
    │   │   ├── RefreshTokenRequest.java
    │   │   ├── IntrospectRequest.java
    │   │   ├── LogoutRequest.java
    │   │   ├── UserRegisterRequest.java
    │   │   └── UserRequest.java
    │   └── response/
    │       ├── ApiResponse.java             — Universal response envelope {code, message, result}
    │       ├── AuthenticationResponse.java  — {token, refreshToken, authenticated}
    │       ├── IntrospectResponse.java      — {valid}
    │       └── UserResponse.java            — User details + nested RoleResponse
    ├── entity/
    │   ├── User.java                        — @Table("users"), UUID PK via @UuidGenerator
    │   ├── Role.java                        — @Table("roles"), UUID PK, 1:N to User
    │   └── InvalidatedToken.java            — @Table("invalidated_token"), String PK (JWT ID)
    ├── exception/
    │   ├── AppException.java                — Runtime exception carrying an ErrorCode
    │   ├── ErrorCode.java                   — Enum: code (int) + message (String) + HttpStatus
    │   └── GlobalExceptionHandler.java      — @ControllerAdvice; handles AppException, validation, AccessDenied
    ├── mapper/
    │   └── UserMapper.java                  — MapStruct: UserRegisterRequest→User, User→UserResponse
    ├── repository/
    │   ├── UserRepository.java
    │   ├── RoleRepository.java
    │   └── InvalidatedTokenRepository.java
    └── service/
        ├── AuthenticationService.java       — Interface
        ├── UserService.java                 — Interface
        └── Impl/
            ├── AuthenticationServiceImpl.java
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

### Token structure (HS512, signed with `jwt.signerKey`)

**ACCESS token claims:**
```
sub, iss="DiaUML-Studio", iat, exp, jti, userID, fullName, email,
scope="ROLE_<roleName>", role="ROLE_<roleName>", tokenType="ACCESS"
```
TTL: `jwt.accessTokenExpiration` seconds (default 3600 = 1 hour).

**REFRESH token claims:**
```
sub, iss="DiaUML-Studio", iat, exp, jti, tokenType="REFRESH"
```
TTL: `jwt.refreshTokenExpiration` seconds (default 604800 = 7 days).

### Login — `POST /auth/login`
1. Find user by username → `USER_NOT_FOUND` if missing.
2. BCrypt password check → `INVALID_CREDENTIALS` if wrong.
3. Status check: `"LOCKED"` → `USER_INACTIVE`.
4. Update `user.lastActiveAt`, save.
5. Generate ACCESS + REFRESH tokens.
6. Return `{token, refreshToken, authenticated: true}`.

### Refresh — `POST /auth/refresh` (public)
1. `verifyToken(token, isRefresh=true)`: signature + expiry + blacklist + `tokenType=="REFRESH"`.
2. Blacklist the used refresh token (save JTI to `invalidated_token`).
3. Lookup user by `sub`.
4. Issue new ACCESS + REFRESH pair (rotation — one-time use).
5. Return `{token, refreshToken, authenticated: true}`.

### Introspect — `POST /auth/introspect` (public)
- Calls `verifyToken(token)` which requires `tokenType=="ACCESS"`.
- Returns `{valid: true|false}` — never throws to the caller.

### Logout — `POST /auth/logout` (public)
- Calls `verifyToken(token)` — requires `tokenType=="ACCESS"`.
- Saves JTI + expiry to `invalidated_token` table.

### Protected request flow
```
Request → SecurityFilterChain
       → CustomJwtDecoder.decode()
           → authenticationService.introspect()   ← blacklist + tokenType check
           → NimbusJwtDecoder.decode()
       → JwtAuthenticationConverter
           → reads claim "role" as authority (no added prefix, empty authorityPrefix)
       → @EnableMethodSecurity enforces @PreAuthorize on method level
```
Unauthenticated → `JwtAuthenticationEntryPoint` → JSON `{code:401, message:"..."}`.

### Token type guard — `verifyToken` overloads
```java
verifyToken(String token)                  // ACCESS only
verifyToken(String token, boolean isRefresh)  // isRefresh=true → REFRESH; false → ACCESS
```
Wrong `tokenType` → `AppException(UNAUTHENTICATED)`.

---

## 5. Database

### Configuration (`application.yaml` + `.env`)
```
Host:     DB_HOST:DB_PORT (default: localhost:5432)
Database: DB_NAME
Username: DB_USERNAME
Password: DB_PASSWORD
DDL:      hibernate.ddl-auto=update
Dialect:  PostgreSQLDialect
Timezone: APP_TIMEZONE (e.g. Asia/Ho_Chi_Minh)
```
Run PostgreSQL locally via Docker: `docker compose up -d`.

### Entities

**`users` table — `User.java`**
| Column | Type | Notes |
|---|---|---|
| `user_id` | UUID (PK) | `@UuidGenerator`, not updatable |
| `user_name` | VARCHAR | unique, not null |
| `password` | VARCHAR | BCrypt hash |
| `full_name` | VARCHAR(100) | not null |
| `email` | VARCHAR | unique, not null |
| `phone` | VARCHAR | not null |
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

**`invalidated_token` table — `InvalidatedToken.java`**
| Column | Type | Notes |
|---|---|---|
| `iD` | VARCHAR (PK) | JWT ID (JTI claim) |
| `expiry_time` | TIMESTAMP | used for token blacklist TTL |

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
# Optional overrides (have defaults in application.yaml):
JWT_ACCESS_TOKEN_EXPIRATION   # default 3600 s
JWT_REFRESH_TOKEN_EXPIRATION  # default 604800 s
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

9. **`invalidated_token.iD` field name is intentional** (capital D in `iD`). Do not rename — it is the JPA `@Id` and changing it would break the blacklist lookup.

10. **`ddl-auto: update` is active.** Adding new entity fields will auto-create columns on startup. Do not use `create` or `create-drop` in any environment that has existing data.

11. **Service implementations live in `service/Impl/`**, not in `service/`. The pattern `FooService` (interface) + `FooServiceImpl` (class in `Impl/`) must be maintained.

12. **Constructor injection via `@RequiredArgsConstructor`** is the convention for service and controller classes. Do not add field-level `@Autowired` to these classes.
