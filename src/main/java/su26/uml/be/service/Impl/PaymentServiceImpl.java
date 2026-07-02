package su26.uml.be.service.Impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import su26.uml.be.dto.payment.PaymentResponse;
import su26.uml.be.dto.payment.PaymentStatusResponse;
import su26.uml.be.entity.PaymentTransaction;
import su26.uml.be.entity.Plan;
import su26.uml.be.entity.Subscription;
import su26.uml.be.entity.User;
import su26.uml.be.enums.PaymentStatus;
import su26.uml.be.enums.SubscriptionStatus;
import su26.uml.be.repository.PaymentTransactionRepository;
import su26.uml.be.repository.PlanRepository;
import su26.uml.be.repository.SubscriptionRepository;
import su26.uml.be.repository.UserRepository;
import su26.uml.be.service.PaymentService;
import vn.payos.PayOS;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkRequest;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkResponse;
import vn.payos.model.webhooks.WebhookData;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentServiceImpl implements PaymentService {

    private final PayOS payOS;
    private final PlanRepository planRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;

    @Value("${app.frontend.base-url:http://localhost:5173}")
    private String frontendUrl;

    @Override
    @Transactional
    public PaymentResponse createPaymentLink(User user, Long planId) {
        Plan plan = planRepository.findById(planId)
                .orElseThrow(() -> new IllegalArgumentException("Plan not found"));

        // Generate a unique order code (PayOS requires an integer/long <= 9007199254740991)
        Long orderCode = System.currentTimeMillis() % 10000000000L;

        PaymentTransaction transaction = PaymentTransaction.builder()
                .orderCode(orderCode)
                .user(user)
                .plan(plan)
                .amount(plan.getPrice())
                .status(PaymentStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();

        paymentTransactionRepository.save(transaction);

        try {
            String returnUrl = frontendUrl + "/payment/success";
            String cancelUrl = frontendUrl + "/payment/cancel";
            String description = "Thanh toan goi " + plan.getName();

            CreatePaymentLinkRequest paymentData = CreatePaymentLinkRequest.builder()
                    .orderCode(orderCode)
                    .amount(plan.getPrice().longValue())
                    .description(description.length() > 25 ? description.substring(0, 25) : description)
                    .returnUrl(returnUrl)
                    .cancelUrl(cancelUrl)
                    .build();

            CreatePaymentLinkResponse checkoutResponse = payOS.paymentRequests().create(paymentData);

            transaction.setCheckoutUrl(checkoutResponse.getCheckoutUrl());
            paymentTransactionRepository.save(transaction);

            return PaymentResponse.builder()
                    .checkoutUrl(checkoutResponse.getCheckoutUrl())
                    .orderCode(orderCode)
                    .qrCode(checkoutResponse.getQrCode())
                    .build();

        } catch (Exception e) {
            log.error("Error creating payment link with PayOS", e);
            throw new RuntimeException("Could not create payment link");
        }
    }

    @Override
    @Transactional
    public void processWebhook(WebhookData webhookData) {
        try {
            Long orderCode = webhookData.getOrderCode();
            log.info("Processing webhook for orderCode: {}", orderCode);

            Optional<PaymentTransaction> transactionOpt = paymentTransactionRepository
                    .findByOrderCodeAndStatus(orderCode, PaymentStatus.PENDING);

            if (transactionOpt.isEmpty()) {
                log.warn("Transaction not found or already processed for orderCode: {}", orderCode);
                return;
            }

            PaymentTransaction transaction = transactionOpt.get();
            transaction.setStatus(PaymentStatus.PAID);
            paymentTransactionRepository.save(transaction);

            // Grant subscription to user
            User user = transaction.getUser();
            Plan plan = transaction.getPlan();

            LocalDateTime now = LocalDateTime.now();
            LocalDateTime endDate = now.plusDays(plan.getDurationDays() != null ? plan.getDurationDays() : 30);

            Subscription subscription = Subscription.builder()
                    .user(user)
                    .plan(plan)
                    .status(SubscriptionStatus.ACTIVE)
                    .startDate(now)
                    .endDate(endDate)
                    .build();

            subscriptionRepository.save(subscription);

            user.setCurrentSubscription(subscription);
            userRepository.save(user);

            log.info("Successfully granted plan {} to user {}", plan.getName(), user.getUsername());

        } catch (Exception e) {
            log.error("Failed to process webhook", e);
            throw new RuntimeException("Webhook processing failed");
        }
    }

    @Override
    public PaymentStatusResponse getPaymentStatus(Long orderCode) {
        PaymentTransaction transaction = paymentTransactionRepository.findByOrderCode(orderCode)
                .orElseThrow(() -> new IllegalArgumentException("Transaction not found for orderCode: " + orderCode));

        return PaymentStatusResponse.builder()
                .orderCode(transaction.getOrderCode())
                .status(transaction.getStatus().name())
                .planName(transaction.getPlan().getName())
                .build();
    }
}
