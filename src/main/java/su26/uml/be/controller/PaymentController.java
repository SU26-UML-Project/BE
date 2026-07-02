package su26.uml.be.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import su26.uml.be.dto.payment.PaymentRequest;
import su26.uml.be.dto.payment.PaymentResponse;
import su26.uml.be.dto.payment.PaymentStatusResponse;
import su26.uml.be.entity.User;
import su26.uml.be.service.PaymentService;
import vn.payos.PayOS;
import vn.payos.model.webhooks.WebhookData;

@RestController
@RequestMapping("/api/uml/payments")
@RequiredArgsConstructor
@Slf4j
public class PaymentController {

    private final PaymentService paymentService;
    private final PayOS payOS;

    @PostMapping("/create")
    public ResponseEntity<PaymentResponse> createPaymentLink(
            @AuthenticationPrincipal User user,
            @RequestBody PaymentRequest request) {
        PaymentResponse response = paymentService.createPaymentLink(user, request.getPlanId());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/status/{orderCode}")
    public ResponseEntity<PaymentStatusResponse> getPaymentStatus(
            @PathVariable Long orderCode) {
        PaymentStatusResponse response = paymentService.getPaymentStatus(orderCode);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/webhook")
    public ResponseEntity<String> handleWebhook(@RequestBody Object webhookBody) {
        try {
            // Verify checksum signature using PayOS SDK v2
            WebhookData webhookData = payOS.webhooks().verify(webhookBody);
            paymentService.processWebhook(webhookData);
            return ResponseEntity.ok("success");
        } catch (Exception e) {
            log.error("Webhook verification failed", e);
            return ResponseEntity.badRequest().body("Invalid webhook");
        }
    }
}

