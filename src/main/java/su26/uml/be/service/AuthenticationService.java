package su26.uml.be.service;

import com.nimbusds.jose.JOSEException;

import su26.uml.be.dto.request.IntrospectRequest;
import su26.uml.be.dto.request.LoginRequest;
import su26.uml.be.dto.request.LogoutRequest;
import su26.uml.be.dto.response.AuthenticationResponse;
import su26.uml.be.dto.response.IntrospectResponse;


import java.text.ParseException;

public interface AuthenticationService {
    AuthenticationResponse authenticate(LoginRequest request);
    IntrospectResponse introspect(IntrospectRequest request);
    void logout(LogoutRequest token) throws ParseException, JOSEException;
}
