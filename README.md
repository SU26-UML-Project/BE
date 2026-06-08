# UML Diagram Studio — Backend (BE)

Backend REST API cho nền tảng **DiaUML Studio**, xây dựng bằng Spring Boot 4 + Java 21.

---

## Công nghệ chính

| Thành phần | Phiên bản |
|---|---|
| Java | 21 |
| Spring Boot | 4.0.6 |
| Spring Security + OAuth2 Resource Server | (Boot-managed) |
| Spring Data JPA + Hibernate | (Boot-managed) |
| Spring Validation | (Boot-managed) |
| Spring Actuator | (Boot-managed) |
| PostgreSQL JDBC | (Boot-managed) |
| Nimbus JOSE JWT | (via OAuth2 Resource Server) |
| MapStruct | 1.5.5.Final |
| Lombok | (Boot-managed) |
| springdoc-openapi (Swagger UI) | 3.0.3 |
| Docker Compose | (PostgreSQL + pgAdmin) |

---

## Cấu trúc thư mục

```text
BE/
├── src/main/java/su26/uml/be/
│   ├── BeApplication.java
│   ├── config/
│   │   ├── CustomJwtDecoder.java
│   │   ├── EncoderConfig.java
│   │   ├── JwtAuthenticationEntryPoint.java
│   │   ├── SecurityConfig.java
│   │   ├── TimezoneVerificationConfig.java
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
│   │   └── InvalidatedToken.java
│   ├── exception/
│   │   ├── AppException.java
│   │   ├── ErrorCode.java
│   │   └── GlobalExceptionHandler.java
│   ├── mapper/
│   │   └── UserMapper.java
│   ├── repository/
│   └── service/
│       ├── AuthenticationService.java
│       ├── UserService.java
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

# Tuỳ chọn — có giá trị mặc định nếu bỏ qua
JWT_ACCESS_TOKEN_EXPIRATION=
JWT_REFRESH_TOKEN_EXPIRATION=
```

---

## Chạy PostgreSQL bằng Docker

```bash
docker compose up -d
```

pgAdmin sẽ chạy tại `http://localhost:<PGADMIN_PORT>`.

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
| POST | `/auth/login` | Đăng nhập, nhận access token + refresh token | Public |
| POST | `/auth/refresh` | Đổi refresh token lấy cặp token mới (rotation) | Public |
| POST | `/auth/introspect` | Kiểm tra token còn hợp lệ không | Public |
| POST | `/auth/logout` | Vô hiệu hoá access token | Public |
| GET | `/auth/account-status` | Kiểm tra tài khoản có bị khoá không | Public |

### Users (`/users`)

| Method | Endpoint | Mô tả | Auth |
|---|---|---|---|
| POST | `/users/register` | Đăng ký tài khoản mới | Public |

---

## Cơ chế xác thực (JWT)

Tất cả token được ký bằng **HS512** với `JWT_SIGNER_KEY`.

**Access token** (TTL: `JWT_ACCESS_TOKEN_EXPIRATION`, mặc định 1 giờ):
- Dùng để truy cập các endpoint được bảo vệ.
- Gửi qua header: `Authorization: Bearer <token>`
- Claims: `sub`, `userID`, `fullName`, `email`, `role`, `scope`, `tokenType="ACCESS"`

**Refresh token** (TTL: `JWT_REFRESH_TOKEN_EXPIRATION`, mặc định 7 ngày):
- Dùng duy nhất một lần để đổi lấy cặp token mới tại `POST /auth/refresh`.
- Claims: `sub`, `tokenType="REFRESH"`

**Luồng hoạt động:**
1. Đăng nhập → nhận `token` (access) + `refreshToken`.
2. Dùng `token` cho các request cần xác thực.
3. Khi access token hết hạn → gọi `/auth/refresh` với `refreshToken` → nhận cặp token mới.
4. Đăng xuất → gọi `/auth/logout` với access token → token bị blacklist.

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
