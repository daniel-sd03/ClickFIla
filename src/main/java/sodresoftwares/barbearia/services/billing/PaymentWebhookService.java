package sodresoftwares.barbearia.services.billing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sodresoftwares.barbearia.infra.exception.AppException;
import sodresoftwares.barbearia.model.billing.*;
import sodresoftwares.barbearia.repositories.billing.PaymentRepository;
import sodresoftwares.barbearia.repositories.billing.SubscriptionRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import static java.time.Instant.now;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentWebhookService {

    private final SubscriptionRepository subscriptionRepository;
    private final PaymentRepository paymentRepository;
    private final SubscriptionHistoryService subscriptionHistoryService;

    // ==========================================
    // 1. ENTRY POINT (Webhook Reception)
    // ==========================================

    @Transactional
    public void processWebhook(Map<String, Object> payload) {
        String event = (String) payload.get("event");
        if (event == null) return;

        if (event.startsWith("PAYMENT_")) {
            processPaymentEvent(event, extractPayloadNode(payload, "payment"));
        } else if (event.startsWith("SUBSCRIPTION_")) {
            processSubscriptionEvent(event, extractPayloadNode(payload, "subscription"));
        } else {
            log.debug("Unhandled event category: {}", event);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractPayloadNode(Map<String, Object> payload, String nodeName) {
        return (Map<String, Object>) payload.get(nodeName);
    }

    // ==========================================
    // 2. ROUTERS (Event Delegation)
    // ==========================================

    private void processPaymentEvent(String event, Map<String, Object> paymentData) {
        if (paymentData == null) return;

        String gatewayInvoiceId = (String) paymentData.get("id");
        log.info("Payment Webhook received: {} for invoice {}", event, gatewayInvoiceId);

        switch (event) {
            case "PAYMENT_CREATED" -> handlePaymentCreated(paymentData);
            case "PAYMENT_RECEIVED", "PAYMENT_CONFIRMED" -> handlePaymentPaid(gatewayInvoiceId);
            case "PAYMENT_OVERDUE" -> handlePaymentOverdue(gatewayInvoiceId);
            case "PAYMENT_REFUNDED", "PAYMENT_CHARGEBACK_REQUESTED" -> handlePaymentRefunded(gatewayInvoiceId);
            case "PAYMENT_DELETED", "PAYMENT_BANK_SLIP_CANCELLED", "PAYMENT_CREDIT_CARD_CAPTURE_REFUSED" -> handlePaymentCancelled(gatewayInvoiceId);
            default -> log.debug("Unhandled payment event: {}", event);
        }
    }

    private void processSubscriptionEvent(String event, Map<String, Object> subscriptionData) {
        if (subscriptionData == null) return;

        String businessId = (String) subscriptionData.get("externalReference");
        if (businessId == null) return;

        log.info("Subscription Webhook received: {} for business {}", event, businessId);

        switch (event) {
            case "SUBSCRIPTION_DELETED", "SUBSCRIPTION_INACTIVATED" -> handleSubscriptionSuspended(businessId, event);
            case "SUBSCRIPTION_CREATED", "SUBSCRIPTION_UPDATED" -> handleSubscriptionUpdated(businessId, event);
            default -> log.debug("Unhandled subscription event: {}", event);
        }
    }

    // ==========================================
    // 3. PAYMENT HANDLERS (Business Rules)
    // ==========================================

    private void handlePaymentCreated(Map<String, Object> paymentData) {
        String gatewayInvoiceId = (String) paymentData.get("id");
        String businessId = (String) paymentData.get("externalReference");

        if (paymentRepository.existsByGatewayInvoiceId(gatewayInvoiceId)) {
            log.debug("Payment {} already exists. Ignoring.", gatewayInvoiceId);
            return;
        }

        Subscription subscription = subscriptionRepository.findByBusinessId(businessId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "SUBSCRIPTION_NOT_FOUND", "Subscription not found."));

        Payment payment = Payment.builder()
                .subscription(subscription)
                .gatewayInvoiceId(gatewayInvoiceId)
                .paymentProvider(PaymentProvider.ASAAS)
                .amount(new BigDecimal(paymentData.get("value").toString()))
                .dueDate(LocalDate.parse((String) paymentData.get("dueDate")))
                .status(PaymentStatus.PENDING)
                .invoiceUrl((String) paymentData.get("invoiceUrl"))
                .build();

        paymentRepository.save(payment);
        log.info("Payment PENDING created for invoice {}", gatewayInvoiceId);
    }

    private void handlePaymentPaid(String gatewayInvoiceId) {
        Payment payment = paymentRepository.findByGatewayInvoiceId(gatewayInvoiceId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", "Payment not found."));

        if (payment.getStatus() == PaymentStatus.PAID) {
            log.debug("Payment {} is already PAID. Ignoring.", gatewayInvoiceId);
            return;
        }

        payment.setStatus(PaymentStatus.PAID);
        payment.setPaidAt(now());
        paymentRepository.save(payment);

        extendSubscription(payment);
    }

    private void handlePaymentOverdue(String gatewayInvoiceId) {
        paymentRepository.findByGatewayInvoiceId(gatewayInvoiceId).ifPresent(payment -> {
            if (payment.getStatus() == PaymentStatus.PENDING) {
                Subscription subscription = payment.getSubscription();
                if (subscription.getStatus() == SubscriptionStatus.ACTIVE) {
                    payment.setStatus(PaymentStatus.OVERDUE);
                    log.info("Payment {} marked as OVERDUE.", gatewayInvoiceId);
                } else {
                    payment.setStatus(PaymentStatus.EXPIRED);
                    log.info("Payment {} marked as EXPIRED.", gatewayInvoiceId);
                }
                paymentRepository.save(payment);
            }
        });
    }

    private void handlePaymentRefunded(String gatewayInvoiceId) {
        paymentRepository.findByGatewayInvoiceId(gatewayInvoiceId).ifPresent(payment -> {
            payment.setStatus(PaymentStatus.REFUNDED);
            paymentRepository.save(payment);

            Subscription subscription = payment.getSubscription();
            subscriptionHistoryService.logStatusChange(subscription, SubscriptionStatus.SUSPENDED, "WEBHOOK: PAYMENT_REFUNDED");
            subscription.setStatus(SubscriptionStatus.SUSPENDED);
            subscriptionRepository.save(subscription);

            log.info("Payment {} REFUNDED. Subscription {} suspended.", gatewayInvoiceId, subscription.getId());
        });
    }

    private void handlePaymentCancelled(String gatewayInvoiceId) {
        paymentRepository.findByGatewayInvoiceId(gatewayInvoiceId).ifPresent(payment -> {
            if (payment.getStatus() == PaymentStatus.PENDING) {
                payment.setStatus(PaymentStatus.EXPIRED);
                paymentRepository.save(payment);
                log.info("Payment {} marked as EXPIRED/CANCELLED.", gatewayInvoiceId);
            }
        });
    }

    private void extendSubscription(Payment payment) {
        Subscription lockedSub = subscriptionRepository.findByIdWithLock(payment.getSubscription().getId())
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "SUBSCRIPTION_NOT_FOUND", "Subscription not found."));

        Plan plan = lockedSub.getPlan();
        LocalDate newPeriodEnd;

        if (plan.getBillingCycle() == BillingCycle.YEARLY) {
            double fractionPaid = payment.getAmount().doubleValue() / plan.getPrice().doubleValue();
            long daysToAdd = Math.round(fractionPaid * 365);
            newPeriodEnd = lockedSub.getCurrentPeriodEnd().plusDays(daysToAdd);
            log.info("Annual plan installment paid. Adding {} days.", daysToAdd);
        } else {
            newPeriodEnd = lockedSub.getCurrentPeriodEnd().plusMonths(1);
            log.info("Monthly plan paid. Adding 1 month.");
        }

        subscriptionHistoryService.logStatusChange(lockedSub, SubscriptionStatus.ACTIVE, "WEBHOOK: PAYMENT_CONFIRMED");
        lockedSub.setCurrentPeriodEnd(newPeriodEnd);
        lockedSub.setStatus(SubscriptionStatus.ACTIVE);
        subscriptionRepository.save(lockedSub);
    }

    // ==========================================
    // 4. SUBSCRIPTION HANDLERS (Business Rules)
    // ==========================================

    private void handleSubscriptionSuspended(String businessId, String event) {
        subscriptionRepository.findByBusinessId(businessId).ifPresent(subscription -> {
            subscriptionHistoryService.logStatusChange(subscription, SubscriptionStatus.SUSPENDED, "WEBHOOK: " + event);
            subscription.setStatus(SubscriptionStatus.SUSPENDED);
            subscriptionRepository.save(subscription);
            log.info("Subscription for business {} suspended directly via webhook.", businessId);
        });
    }

    private void handleSubscriptionUpdated(String businessId, String event) {
        log.info("Subscription event {} registered successfully for business {}.", event, businessId);
    }
}