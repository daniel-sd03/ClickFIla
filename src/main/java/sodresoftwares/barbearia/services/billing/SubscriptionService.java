package sodresoftwares.barbearia.services.billing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sodresoftwares.barbearia.dto.billing.CheckoutRequest;
import sodresoftwares.barbearia.dto.billing.CreditCardSummaryDTO;
import sodresoftwares.barbearia.dto.billing.PlanResponseDTO;
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
    private final BusinessPaymentMethodRepository paymentMethodRepository;

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

        BusinessPaymentMethod paymentMethod = paymentMethodRepository.findByBusinessId(business.getId())
                .orElseGet(() -> BusinessPaymentMethod.builder()
                        .business(business)
                        .isDefault(true)
                        .build());

        paymentMethod.setCardBrand(checkoutResponse.cardBrand());
        paymentMethod.setLastFourDigits(checkoutResponse.lastFourDigits());
        paymentMethod.setCreditCardToken(checkoutResponse.creditCardToken());

        paymentMethodRepository.save(paymentMethod);

        subscription.setGatewaySubscriptionId(checkoutResponse.gatewaySubscriptionId());
        subscription.setPlan(plan);
        subscriptionRepository.save(subscription);

        log.info("Transparent checkout processed successfully for business {}.", business.getId());
    }

    @Transactional
    public void updateCreditCard(String userId, CheckoutRequest request) {
        Subscription subscription = subscriptionRepository.findByUserIdWithBusinessAndPlan(userId)
                .orElseThrow(() -> new AppException(
                        HttpStatus.NOT_FOUND,
                        "SUBSCRIPTION_NOT_FOUND",
                        "No subscription found for this user."
                ));

        if (subscription.getGatewaySubscriptionId() == null) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_SUBSCRIPTION",
                    "This subscription does not have an active gateway link to update."
            );
        }

        var gatewayResponse = paymentGatewayPort.updateSubscriptionCreditCard(
                subscription.getGatewaySubscriptionId(),
                request.creditCard(),
                request.creditCardHolderInfo()
        );

        Business business = subscription.getBusiness();

        BusinessPaymentMethod paymentMethod = paymentMethodRepository.findByBusinessId(business.getId())
                .orElseGet(() -> BusinessPaymentMethod.builder()
                        .business(business)
                        .isDefault(true)
                        .build());

        paymentMethod.setCardBrand(gatewayResponse.cardBrand());
        paymentMethod.setLastFourDigits(gatewayResponse.lastFourDigits());
        paymentMethod.setCreditCardToken(gatewayResponse.creditCardToken());

        paymentMethodRepository.save(paymentMethod);

        log.info("Credit card updated successfully for business {}.", business.getId());
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

        PlanResponseDTO planDto = subscription.getPlan() != null
                ? PlanResponseDTO.fromEntity(subscription.getPlan())
                : null;

        CreditCardSummaryDTO cardDto = paymentMethodRepository.findByBusinessId(subscription.getBusiness().getId())
                .map(method -> new CreditCardSummaryDTO(method.getCardBrand(), method.getLastFourDigits()))
                .orElse(null);

        return new SubscriptionResponseDTO(
                subscription.getStatus(),
                planDto,
                subscription.getCurrentPeriodEnd(),
                subscription.getCancelAtPeriodEnd(),
                cardDto
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