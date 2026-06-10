package su26.uml.be.service;

import java.util.Date;

public interface TokenBlacklistService {
    void blacklist(String jwtId, Date expiryTime);
    boolean isBlacklisted(String jwtId);
}
