# UML Project - Backend (BE)

Backend service được xây dựng bằng Spring Boot.

## Công nghệ chính

- Java 21
- Spring Boot
- Spring Data JPA
- Spring Validation
- Spring Security
- PostgreSQL
- Docker Compose

## Cấu trúc thư mục

```text
BE/
├── src/main/java/su26/uml/be
│   ├── config
│   ├── controller
│   ├── dto
│   ├── entity
│   ├── mapper
│   ├── repository
│   └── service
├── src/main/resources/application.yaml
├── docker-compose.yml
├── .env.example
└── pom.xml
```

## Yêu cầu môi trường

- JDK 21
- Maven (hoặc dùng `mvnw`/`mvnw.cmd`)
- Docker + Docker Compose (nếu chạy DB bằng container)

## Cấu hình biến môi trường

1. Tạo file `.env` từ `.env.example`.
2. Khai báo đầy đủ các biến đang được sử dụng trong `application.yaml` và `docker-compose.yml`.

Biến hiện có trong `.env.example`:

```env
DB_NAME=
DB_USERNAME=
DB_PASSWORD=
```

Gợi ý bạn sẽ cần bổ sung thêm các biến sau:

- `SERVER_PORT`
- `CONTEXT_PATH`
- `APP_NAME`
- `DB_HOST`
- `DB_PORT`
- `APP_TIMEZONE`
- `PGADMIN_EMAIL`
- `PGADMIN_PASSWORD`
- `PGADMIN_PORT`

## Chạy PostgreSQL bằng Docker

```bash
docker compose up -d
```

## Chạy ứng dụng

Trên Windows:

```bash
./mvnw.cmd spring-boot:run
```

Trên macOS/Linux:

```bash
./mvnw spring-boot:run
```

## Kiểm tra nhanh

- Health check endpoint (Actuator): `GET /actuator/health`
- User endpoint base path: `GET /users`

## TODO (cập nhật sau)

- [ ] Mô tả nghiệp vụ chi tiết
- [ ] Danh sách API đầy đủ
- [ ] Cơ chế auth/permission
- [ ] Quy ước code và branching
- [ ] Hướng dẫn deploy môi trường staging/production
- [ ] Tài liệu migration database
