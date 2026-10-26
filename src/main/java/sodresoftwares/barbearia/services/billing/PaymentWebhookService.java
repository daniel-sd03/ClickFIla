package sodresoftwares.barbearia.services.billing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sodresoftwares.barbearia.infra.exception.AppException;
import sodresoftwares.barbearia.model.billing.*;
import sodresoftwares.barbearia.repositories.billing.PaymentRepository;
import sodresoftwares.barbearia.repositories.billing.PlanRepository;
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
    private final PlanRepository planRepository;
    private final PaymentHistoryService paymentHistoryService;
    private final SubscriptionHistoryService subscriptionHistoryService;

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

    private void processPaymentEvent(String event, Map<String, Object> paymentData) {
        if (paymentData == null) return;

        String gatewayInvoiceId = (String) paymentData.get("id");
        log.info("Payment Webhook received: {} for invoice {}", event, gatewayInvoiceId);

        switch (event) {
            case "PAYMENT_CREATED" -> handlePaymentCreated(event, paymentData);
            case "PAYMENT_RECEIVED", "PAYMENT_CONFIRMED" -> handlePaymentPaid(gatewayInvoiceId, event, paymentData);
            case "PAYMENT_OVERDUE", "PAYMENT_CREDIT_CARD_CAPTURE_REFUSED" -> handlePaymentFailed(gatewayInvoiceId, event, paymentData);
            case "PAYMENT_REFUNDED", "PAYMENT_CHARGEBACK_REQUESTED" -> handlePaymentRefunded(gatewayInvoiceId, event);
            case "PAYMENT_DELETED", "PAYMENT_BANK_SLIP_CANCELLED" -> handlePaymentCancelled(gatewayInvoiceId, event);
            default -> log.debug("Unhandled payment event: {}", event);
        }
    }

    private void processSubscriptionEvent(String event, Map<String, Object> subscriptionData) {
        if (subscriptionData == null) return;

        String businessId = (String) subscriptionData.get("externalReference");
        if (businessId == null) return;

        log.info("Subscription Webhook received: {} for business {}", event, businessId);

        if (event.equals("SUBSCRIPTION_DELETED")) {
            subscriptionRepository.findByBusinessId(businessId).ifPresent(subscription -> {
                if (subscription.getStatus() != SubscriptionStatus.CANCELED) {
                    subscriptionHistoryService.logStatusChange(
                            subscription,
                            SubscriptionStatus.CANCELED,
                            "WEBHOOK: " + event
                    );
                    subscription.setStatus(SubscriptionStatus.CANCELED);
                    subscriptionRepository.save(subscription);
                }
            });
        }
    }

    private void handlePaymentCreated(String event, Map<String, Object> paymentData) {
        String gatewayInvoiceId = (String) paymentData.get("id");
        String businessId = (String) paymentData.get("externalReference");

        if (paymentRepository.existsByGatewayInvoiceId(gatewayInvoiceId)) return;

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
                .lastGatewayEvent(event)
                .build();

        paymentRepository.save(payment);
        paymentHistoryService.logStatusChange(payment, PaymentStatus.PENDING, event);
    }

    private void handlePaymentPaid(String gatewayInvoiceId, String event, Map<String, Object> paymentData) {
        paymentRepository.findByGatewayInvoiceId(gatewayInvoiceId).ifPresent(payment -> {
            if (payment.getStatus() == PaymentStatus.PAID) return;

            String asaasBillingType = (String) paymentData.get("billingType");
            if (asaasBillingType != null) {
                payment.setPaymentMethod(mapAsaasBillingType(asaasBillingType));
            }

            paymentHistoryService.logStatusChange(payment, PaymentStatus.PAID, event);
            payment.setStatus(PaymentStatus.PAID);
            payment.setPaidAt(now());
            payment.setLastGatewayEvent(event);
            paymentRepository.save(payment);

            String planCode = (String) paymentData.get("description");
            extendSubscription(payment, event, planCode);
        });
    }

    private void handlePaymentFailed(String gatewayInvoiceId, String event, Map<String, Object> paymentData) {
        paymentRepository.findByGatewayInvoiceId(gatewayInvoiceId).ifPresent(payment -> {
            PaymentStatus newStatus = (payment.getSubscription().getStatus() == SubscriptionStatus.ACTIVE)
                    ? PaymentStatus.OVERDUE
                    : PaymentStatus.EXPIRED;

            paymentHistoryService.logStatusChange(payment, newStatus, event);
            payment.setStatus(newStatus);

            if (paymentData.containsKey("invoiceUrl") && paymentData.get("invoiceUrl") != null) {
                payment.setInvoiceUrl((String) paymentData.get("invoiceUrl"));
            }
            payment.setLastGatewayEvent(event);
            paymentRepository.save(payment);
        });
    }

    private void handlePaymentRefunded(String gatewayInvoiceId, String event) {
        paymentRepository.findByGatewayInvoiceId(gatewayInvoiceId).ifPresent(payment -> {
            paymentHistoryService.logStatusChange(payment, PaymentStatus.REFUNDED, event);
            payment.setStatus(PaymentStatus.REFUNDED);
            payment.setLastGatewayEvent(event);
            paymentRepository.save(payment);

            Subscription subscription = payment.getSubscription();
            if (subscription.getStatus() != SubscriptionStatus.SUSPENDED) {
                subscriptionHistoryService.logStatusChange(subscription, SubscriptionStatus.SUSPENDED, "WEBHOOK: " + event);
                subscription.setStatus(SubscriptionStatus.SUSPENDED);
                subscriptionRepository.save(subscription);
            }
        });
    }

    private void handlePaymentCancelled(String gatewayInvoiceId, String event) {
        paymentRepository.findByGatewayInvoiceId(gatewayInvoiceId).ifPresent(payment -> {
            if (payment.getStatus() == PaymentStatus.PENDING || payment.getStatus() == PaymentStatus.OVERDUE) {
                paymentHistoryService.logStatusChange(payment, PaymentStatus.EXPIRED, event);
                payment.setStatus(PaymentStatus.EXPIRED);
                payment.setLastGatewayEvent(event);
                paymentRepository.save(payment);
            }
        });
    }

    //------------ Auxiliary Methods ------------

    private void extendSubscription(Payment payment, String event, String planCode) {
        Subscription lockedSub = subscriptionRepository.findByIdWithLock(payment.getSubscription().getId())
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "SUBSCRIPTION_NOT_FOUND", "Subscription not found."));

        if (planCode != null && !planCode.isBlank() &&
                (lockedSub.getPlan() == null || !lockedSub.getPlan().getCode().equals(planCode))) {

            Plan newPlan = planRepository.findByCode(planCode)
                    .orElseThrow(() -> new AppException(
                            HttpStatus.NOT_FOUND,
                            "PLAN_NOT_FOUND",
                            "Plan not found for code: " + planCode
                    ));

            lockedSub.setPlan(newPlan);
            log.info("Subscription {} plan updated to {}", lockedSub.getId(), planCode);
        }

        Plan plan = lockedSub.getPlan();
        if (plan == null) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_STATE",
                    "Subscription has no plan assigned."
            );
        }

        LocalDate newPeriodEnd;
        LocalDate baseDate = lockedSub.getCurrentPeriodEnd().isBefore(LocalDate.now())
                ? LocalDate.now()
                : lockedSub.getCurrentPeriodEnd();

        if (plan.getBillingCycle() == BillingCycle.YEARLY) {
            double fractionPaid = payment.getAmount().doubleValue() / plan.getPrice().doubleValue();
            long daysToAdd = Math.round(fractionPaid * 365);
            newPeriodEnd = baseDate.plusDays(daysToAdd);
        } else {
            newPeriodEnd = baseDate.plusMonths(1);
        }

        subscriptionHistoryService.logStatusChange(lockedSub, SubscriptionStatus.ACTIVE, "WEBHOOK: " + event);
        lockedSub.setCurrentPeriodEnd(newPeriodEnd);
        lockedSub.setStatus(SubscriptionStatus.ACTIVE);
        subscriptionRepository.save(lockedSub);

        log.info("Subscription {} extended to {}.", lockedSub.getId(), newPeriodEnd);
    }


    private PaymentMethod mapAsaasBillingType(String asaasBillingType) {
        if (asaasBillingType == null) return null;
        return switch (asaasBillingType.toUpperCase()) {
            case "PIX" -> PaymentMethod.PIX;
            case "CREDIT_CARD" -> PaymentMethod.CREDIT_CARD;
            default -> null;
        };
    }
}