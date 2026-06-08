package su26.uml.be.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Central OpenAPI / Swagger configuration for the UML Diagram backend.
 *
 * <p>Defines API metadata, the public server base URL, a global Bearer (JWT) security
 * scheme so protected endpoints can be exercised straight from the Swagger UI, and a
 * reusable {@code ErrorResponse} schema shared by every error response.</p>
 */
@Configuration
public class OpenApiConfig {

    /** Name of the security scheme referenced by {@code @SecurityRequirement} on operations. */
    public static final String BEARER_SCHEME = "bearerAuth";

    @Value("${server.port:8088}")
    private String serverPort;

    @Value("${server.servlet.context-path:/api/uml}")
    private String contextPath;

    @Bean
    public OpenAPI umlOpenAPI() {
        final Server localServer = new Server()
                .url("http://localhost:" + serverPort + contextPath)
                .description("Local development server");

        final SecurityScheme bearerScheme = new SecurityScheme()
                .name(BEARER_SCHEME)
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description("""
                        Paste the JWT returned by `POST /auth/login` (the `result.token` field).
                        Swagger sends it as the `Authorization: Bearer <token>` header on protected endpoints.""");

        return new OpenAPI()
                .info(apiInfo())
                .servers(List.of(localServer))
                .components(new Components()
                        .addSecuritySchemes(BEARER_SCHEME, bearerScheme)
                        .addSchemas("ErrorResponse", errorResponseSchema()))
                // Apply Bearer auth globally; public endpoints simply ignore the header.
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }

    private Info apiInfo() {
        return new Info()
                .title("UML Diagram Studio API")
                .version("0.0.1-SNAPSHOT")
                .description("""
                        REST API for the DiaUML Studio platform: authentication (HS512 JWT),
                        user registration and account management.

                        **Authentication**

                        1. Call `POST /auth/login` with valid credentials to obtain a JWT.
                        2. Click **Authorize** and paste the token to call protected endpoints.

                        All responses are wrapped in a standard envelope: `{ code, message, result }`.""")
                .contact(new Contact()
                        .name("SU26 UML Project Team")
                        .email("uml-admin@educare.com"))
                .license(new License().name("Proprietary"));
    }

    /**
     * Reusable error envelope returned by the {@code GlobalExceptionHandler} for every
     * 4xx/5xx response. Mirrors {@code ApiResponse} with a populated {@code message} and
     * {@code null} {@code result}.
     */
    private ObjectSchema errorResponseSchema() {
        ObjectSchema schema = new ObjectSchema();
        schema.setName("ErrorResponse");
        schema.description("Standard error envelope returned for all non-2xx responses.");
        schema.addProperty("code", new IntegerSchema()
                .example(1006)
                .description("Application-specific error code (see ErrorCode enum). Not the HTTP status."));
        schema.addProperty("message", new StringSchema()
                .example("Tên đăng nhập hoặc mật khẩu không đúng")
                .description("Human-readable error message."));
        schema.addProperty("result", new ObjectSchema()
                .nullable(true)
                .description("Always null for error responses."));
        return schema;
    }
}
