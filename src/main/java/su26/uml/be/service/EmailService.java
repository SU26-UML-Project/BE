package su26.uml.be.service;

public interface EmailService {
    void sendForgotPasswordOtpEmail(String toEmail, String otpCode, String fullName);

    void sendChangePasswordOtpEmail(String toEmail, String otpCode, String fullName);
}