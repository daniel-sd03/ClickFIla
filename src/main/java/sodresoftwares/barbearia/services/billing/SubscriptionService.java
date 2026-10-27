package sodresoftwares.barbearia.services.billing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sodresoftwares.barbearia.dto.billing.CheckoutRequest;
import sodresoftwares.barbearia.dto.billing.SubscriptionResponseDTO;
import sodresoftwares.barbearia.infra.exception.AppException;
import sodresoftwares.barbearia.model.business.Business;
import sodresoftwares.barbearia.model.billing.*;
import sodresoftwares.barbearia.ports.PaymentGatewayPort;
import sodresoftwares.barbearia.repositories.billing.BusinessPaymentMethodRepository;
import sodresoftwares.barbearia.repositories.billing.PaymentRepository;
import sodresoftwares.barbearia.repositories.billing.PlanRepository;
import sodresoftwares.barbearia.repositories.billing.SubscriptionRepository;

import java.time.LocalDate;
import java.time.ZoneId;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final PlanRepository planRepository;
    private final PaymentGatewayPort paymentGatewayPort;
    private final PaymentRepository paymentRepository;
    private final BusinessPaymentMethodRepository paymentMethodRepository; // Novo repositório injetado

    @Value("${billing.trial.days}")
    private int trialDays;

    @Transactional
    public void createTrialSubscription(Business business) {
        LocalDate today = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        LocalDate trialEnd = today.plusDays(trialDays);

        Subscription subscription = Subscription.builder()
                .business(business)
                .status(SubscriptionStatus.TRIAL)
                .paymentProvider(PaymentProvider.ASAAS)
                .currentPeriodStart(today)
                .currentPeriodEnd(trialEnd)
                .billingAnchorDay(trialEnd.getDayOfMonth())
                .cancelAtPeriodEnd(false)
                .build();

        subscriptionRepository.save(subscription);
        log.info("TRIAL subscription of {} days created for business: {}", trialDays, business.getId());
    }

    @Transactional
    public void processTransparentCheckout(String userId, CheckoutRequest request) {
        // 1. Busca a assinatura do usuário logado
        Subscription subscription = subscriptionRepository.findByUserIdWithBusinessAndPlan(userId)
                .orElseThrow(() -> new AppException(
                        HttpStatus.NOT_FOUND,
                        "SUBSCRIPTION_NOT_FOUND",
                        "No subscription found for this user."
                ));

        Business business = subscription.getBusiness();

        Plan plan = planRepository.findByCode(request.planId())
                .orElseThrow(() -> new AppException(
                        HttpStatus.NOT_FOUND,
                        "PLAN_NOT_FOUND",
                        "The selected plan is not available."
                ));

        String customerId = paymentGatewayPort.createOrUpdateCustomer(business);
        subscription.setGatewayCustomerId(customerId);

        var checkoutResponse = paymentGatewayPort.processCreditCardCheckout(
                business,
                plan,
                customerId,
                request.creditCard(),
                request.creditCardHolderInfo()
        );

        BusinessPaymentMethod paymentMethod = BusinessPaymentMethod.builder()
                .business(business)
                .cardBrand(checkoutResponse.cardBrand())
                .lastFourDigits(checkoutResponse.lastFourDigits())
                .creditCardToken(checkoutResponse.creditCardToken())
                .isDefault(true)
                .build();

        paymentMethodRepository.save(paymentMethod);

        subscription.setGatewaySubscriptionId(checkoutResponse.gatewaySubscriptionId());
        subscription.setPlan(plan);
        subscriptionRepository.save(subscription);

        log.info("Transparent checkout processed successfully for business {}.", business.getId());
    }

    @Transactional
    public void cancelSubscription(String businessId) {
        Subscription subscription = getSubscriptionByBusiness(businessId);

        if (subscription.getStatus() == SubscriptionStatus.CANCELED) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "ALREADY_CANCELED",
                    "This subscription is already canceled."
            );
        }

        if (subscription.getGatewaySubscriptionId() != null) {
            paymentGatewayPort.cancelSubscription(subscription.getGatewaySubscriptionId());
        }

        subscription.setCancelAtPeriodEnd(true);
        subscriptionRepository.save(subscription);

        log.info("Subscription for business {} marked for cancellation at the end of the period.", businessId);
    }

    public SubscriptionResponseDTO getMySubscriptionDetails(String userId) {
        Subscription subscription = subscriptionRepository.findByUserIdWithBusinessAndPlan(userId)
                .orElseThrow(() -> new AppException(
                        HttpStatus.NOT_FOUND,
                        "SUBSCRIPTION_NOT_FOUND",
                        "Subscription not found for this user."
                ));

        String paymentLink = null;
        if (subscription.getStatus() == SubscriptionStatus.SUSPENDED) {
            paymentLink = paymentRepository.findLatestPendingOrOverduePayment(subscription.getId())
                    .map(Payment::getInvoiceUrl)
                    .orElse(null);
        }

        String planName = subscription.getPlan() != null ? subscription.getPlan().getName() : "Trial";

        return new SubscriptionResponseDTO(
                subscription.getStatus(),
                planName,
                subscription.getCurrentPeriodEnd(),
                paymentLink
        );
    }

    private Subscription getSubscriptionByBusiness(String businessId) {
        return subscriptionRepository.findByBusinessIdWithBusiness(businessId)
                .orElseThrow(() -> new AppException(
                        HttpStatus.NOT_FOUND,
                        "SUBSCRIPTION_NOT_FOUND",
                        "No subscription found for this business."
                ));
    }
}