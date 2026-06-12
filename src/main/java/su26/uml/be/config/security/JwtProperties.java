package su26.uml.be.config.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
        String signerKey,
        long accessTokenExpiration,
        long refreshTokenExpiration
) {
}
