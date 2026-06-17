package su26.uml.be.config.swagger;

public final class SwaggerExamples {
    private SwaggerExamples() {}

    // ─── Auth ───────────────────────────────────────────
    public static final String LOGIN_REQUEST =
            "{\"email\": \"johndoe@example.com\", \"password\": \"Passw0rd\"}";

    public static final String LOGIN_RESPONSE =
            "{\"code\":200,\"result\":{\"token\":\"eyJhbGciOiJIUzUxMiJ9...\",\"authenticated\":true,\"refreshToken\":\"eyJhbGciOiJIUzUxMiJ9...\"}}";

    public static final String REFRESH_REQUEST =
            "{\"token\": \"eyJhbGciOiJIUzUxMiJ9...\"}";

    public static final String REFRESH_RESPONSE =
            "{\"code\":200,\"result\":{\"token\":\"eyJhbGciOiJIUzUxMiJ9...\",\"refreshToken\":\"eyJhbGciOiJIUzUxMiJ9...\",\"authenticated\":true}}";

    public static final String INTROSPECT_REQUEST =
            "{\"token\": \"eyJhbGciOiJIUzUxMiJ9...\"}";

    public static final String INTROSPECT_RESPONSE =
            "{\"code\":200,\"result\":{\"valid\":true}}";

    public static final String LOGOUT_REQUEST =
            "{\"token\": \"eyJhbGciOiJIUzUxMiJ9...\"}";

    public static final String LOGOUT_RESPONSE =
            "{\"code\":0}";

    public static final String ACCOUNT_STATUS_RESPONSE =
            "{\"code\":0,\"result\":{\"locked\":false}}";

    // ─── Admin ──────────────────────────────────────────
    public static final String ADMIN_REGISTER_REQUEST = """
            {
              "password": "Passw0rd",
              "fullName": "Admin User",
              "email": "admin@gmail.com",
              "phone": "0901234567"
            }""";

    public static final String ADMIN_REGISTER_RESPONSE = """
            {
              "code": 200,
              "message": "Tạo tài khoản Admin thành công",
              "result": {
                "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
                "username": "admin@gmail.com",
                "fullName": "Admin User",
                "email": "admin@gmail.com",
                "phone": "0901234567",
                "status": "ACTIVE",
                "role": {
                  "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
                  "roleName": "ADMIN",
                  "description": "System administrator"
                }
              }
            }""";

    // ─── Users ──────────────────────────────────────────
    public static final String REGISTER_REQUEST = """
            {
              "password": "Passw0rd",
              "fullName": "John Doe",
              "email": "john.doe@gmail.com",
              "phone": "0901234567"
            }""";

    public static final String DEACTIVATE_ACCOUNT_RESPONSE = """
            {
              "code": 200,
              "message": "Yêu cầu xóa tài khoản đã được ghi nhận",
              "result": {
                "status": "PENDING_DELETE",
                "deletionDate": "2026-07-17T00:00:00",
                "daysRemaining": 30,
                "message": "Tài khoản sẽ bị xóa vĩnh viễn sau 30 ngày. Bạn có thể khôi phục trước thời hạn này."
              }
            }""";

    public static final String RESTORE_ACCOUNT_RESPONSE = """
            {
              "code": 200,
              "message": "Tài khoản đã được khôi phục thành công",
              "result": {
                "status": "ACTIVE",
                "message": "Tài khoản của bạn đã được khôi phục và hoạt động bình thường."
              }
            }""";

    public static final String UPDATE_USER_REQUEST = """
            {
              "fullName": "Nguyen Van B",
              "phone": "0909999999",
              "dob": "2000-01-15"
            }""";

    public static final String UPDATE_USER_RESPONSE = """
            {
              "code": 200,
              "message": "Cập nhật thông tin thành công",
              "result": {
                "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
                "username": "john.doe@gmail.com",
                "fullName": "Nguyen Van B",
                "email": "john.doe@gmail.com",
                "phone": "0909999999",
                "dob": "2000-01-15",
                "status": "ACTIVE",
                "role": {
                  "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
                  "roleName": "USER",
                  "description": "Standard application user"
                }
              }
            }""";

    public static final String REGISTER_RESPONSE = """
            {
              "code": 200,
              "message": "Đăng ký thành công",
              "result": {
                "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
                "username": "johndoe",
                "fullName": "John Doe",
                "email": "john.doe@gmail.com",
                "phone": "0901234567",
                "status": "ACTIVE",
                "role": {
                  "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
                  "roleName": "USER",
                  "description": "Standard application user"
                }
              }
            }""";
}