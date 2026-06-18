package su26.uml.be.service.Impl;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import su26.uml.be.service.EmailService;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class EmailServiceImpl implements EmailService {

    final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    String fromEmail;

    private static final String BRAND_NAME = "DiaUML Studio";

    @Override
    public void sendForgotPasswordOtpEmail(String toEmail, String otpCode, String fullName) {
        String htmlContent = buildOtpEmail(
                fullName,
                "Bạn vừa yêu cầu <b>khôi phục mật khẩu</b> cho tài khoản " + BRAND_NAME + " của mình.",
                otpCode,
                "#2563eb",
                "Mã có hiệu lực trong 1 phút 30 giây.",
                "Nếu bạn không yêu cầu khôi phục mật khẩu, vui lòng bỏ qua email này — tài khoản của bạn vẫn an toàn."
        );

        sendHtmlEmail(toEmail, "Mã OTP khôi phục mật khẩu - " + BRAND_NAME, htmlContent);
    }

    @Override
    public void sendChangePasswordOtpEmail(String toEmail, String otpCode, String fullName) {
        String htmlContent = buildOtpEmail(
                fullName,
                "Bạn đang thực hiện <b>đổi mật khẩu</b> cho tài khoản " + BRAND_NAME + " của mình.",
                otpCode,
                "#7c3aed",
                "Mã có hiệu lực trong 2 phút.",
                "Nếu bạn không thực hiện thao tác này, vui lòng đổi mật khẩu ngay hoặc liên hệ quản trị viên " + BRAND_NAME + "."
        );

        sendHtmlEmail(toEmail, "Mã OTP đổi mật khẩu - " + BRAND_NAME, htmlContent);
    }

    private String buildOtpEmail(String fullName, String intro, String otpCode,
                                 String accentColor, String expiryNote, String footerNote) {
        String greetingName = (fullName != null && !fullName.isBlank()) ? fullName : "bạn";
        return "<div style='margin:0;padding:24px;background-color:#f1f5f9;font-family:Segoe UI,Arial,sans-serif;'>"
                + "<div style='max-width:480px;margin:0 auto;background:#ffffff;border-radius:16px;overflow:hidden;"
                + "box-shadow:0 8px 24px rgba(15,23,42,0.08);'>"
                + "<div style='background:" + accentColor + ";padding:28px 32px;'>"
                + "<h1 style='margin:0;color:#ffffff;font-size:22px;font-weight:800;letter-spacing:0.5px;'>"
                + BRAND_NAME + "</h1>"
                + "<p style='margin:4px 0 0;color:rgba(255,255,255,0.85);font-size:13px;'>"
                + "Nền tảng thiết kế &amp; hỗ trợ vẽ UML Diagram</p>"
                + "</div>"
                + "<div style='padding:32px;color:#0f172a;'>"
                + "<h2 style='margin:0 0 12px;font-size:18px;'>Xin chào " + greetingName + ",</h2>"
                + "<p style='margin:0 0 20px;font-size:15px;line-height:1.6;color:#334155;'>" + intro + "</p>"
                + "<p style='margin:0 0 12px;font-size:14px;color:#334155;'>Mã xác nhận (OTP) của bạn là:</p>"
                + "<div style='text-align:center;margin:0 0 20px;'>"
                + "<span style='display:inline-block;padding:14px 28px;background:#f8fafc;border:1px dashed " + accentColor + ";"
                + "border-radius:12px;font-size:32px;font-weight:800;letter-spacing:8px;color:" + accentColor + ";'>"
                + otpCode + "</span></div>"
                + "<p style='margin:0 0 24px;font-size:13px;color:#64748b;text-align:center;'>"
                + "<i>" + expiryNote + " Tuyệt đối không chia sẻ mã này cho bất kỳ ai.</i></p>"
                + "<hr style='border:none;border-top:1px solid #e2e8f0;margin:0 0 16px;'>"
                + "<p style='margin:0;font-size:12px;line-height:1.6;color:#94a3b8;'>" + footerNote + "</p>"
                + "</div>"
                + "<div style='padding:16px 32px;background:#f8fafc;text-align:center;'>"
                + "<p style='margin:0;font-size:12px;color:#94a3b8;'>© " + BRAND_NAME
                + " — Email tự động, vui lòng không trả lời.</p>"
                + "</div>"
                + "</div></div>";
    }

    private void sendHtmlEmail(String toEmail, String subject, String htmlContent) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);

            mailSender.send(message);
        } catch (MessagingException e) {
            throw new RuntimeException("Lỗi khi gửi email: " + e.getMessage());
        }
    }
}