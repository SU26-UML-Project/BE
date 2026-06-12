package su26.uml.be.service;

import java.text.ParseException;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jwt.JWTClaimsSet;

import su26.uml.be.entity.User;

public interface JwtService {
    String generateAccessToken(User user);

    String generateRefreshToken(User user, String jti);

    JWTClaimsSet parseClaims(String token) throws JOSEException, ParseException;
}
