package su26.uml.be.service;

import com.nimbusds.jose.JOSEException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import su26.uml.be.dto.request.IntrospectRequest;
import su26.uml.be.dto.request.LoginRequest;
import su26.uml.be.dto.request.LogoutRequest;
import su26.uml.be.dto.response.AuthenticationResponse;
import su26.uml.be.dto.response.IntrospectResponse;
import su26.uml.be.entity.User;

import java.text.ParseException;

public interface AuthenticationService {
    AuthenticationResponse authenticate(LoginRequest request, HttpServletResponse response);
    IntrospectResponse introspect(IntrospectRequest request);
    void logout(LogoutRequest request, HttpServletRequest httpRequest, HttpServletResponse response)
            throws ParseException, JOSEException;
    AuthenticationResponse refreshToken(HttpServletRequest httpRequest, HttpServletResponse response);
    AuthenticationResponse generateTokenForOAuth2User(User user);
    boolean isAccountLocked(String identifier);
}
