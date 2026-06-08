package su26.uml.be.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

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
                        .addSecuritySchemes(BEARER_SCHEME, bearerScheme))
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

                        All responses are wrapped in a standard envelope: `{ code, message, result }`.
                        On errors, `result` is absent (omitted by `@JsonInclude(NON_NULL)`).
                        Error codes are defined in `ErrorCode` (e.g. 1006 = invalid credentials).""")
                .contact(new Contact()
                        .name("SU26 UML Project Team")
                        .email("uml-admin@educare.com"))
                .license(new License().name("Proprietary"));
    }
}
