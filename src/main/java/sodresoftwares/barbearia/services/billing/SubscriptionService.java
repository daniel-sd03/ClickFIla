package sodresoftwares.barbearia.services.billing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sodresoftwares.barbearia.dto.billing.*;
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
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final PlanRepository planRepository;
    private final PaymentGatewayPort paymentGatewayPort;
    private final BusinessPaymentMethodRepository paymentMethodRepository;
    private final PaymentRepository paymentRepository;

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
    public CheckoutResponseDTO processTransparentCheckout(String userId, CheckoutRequestDTO request) {
        Subscription subscription = getSubscriptionByUser(userId);

        if (subscription.getGatewaySubscriptionId() != null && subscription.getStatus() != SubscriptionStatus.CANCELED) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "SUBSCRIPTION_ALREADY_EXISTS",
                    "You already have an ongoing or active subscription. Use the payment method update endpoint."
            );
        }

        Business business = subscription.getBusiness();
        Plan plan = getPlanByCode(request.planId());
        String customerId = paymentGatewayPort.createOrUpdateCustomer(business);

        subscription.setGatewayCustomerId(customerId);
        subscription.setCancelAtPeriodEnd(false);

        if (request.paymentMethod() == PaymentMethod.CREDIT_CARD) {
            return handleCreditCardCheckout(subscription, business, plan, customerId, request);
        } else if (request.paymentMethod() == PaymentMethod.PIX) {
            return handlePixCheckout(subscription, business, plan, customerId);
        } else {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_PAYMENT_METHOD",
                    "Supported methods are CREDIT_CARD or PIX."
            );
        }
    }

    @Transactional
    public CheckoutResponseDTO updatePaymentMethod(String userId, CheckoutRequestDTO request) {
        Subscription subscription = getSubscriptionByUser(userId);
        requireGatewaySubscriptionId(subscription);

        if (request.paymentMethod() == PaymentMethod.PIX) {
            paymentGatewayPort.updateSubscriptionPaymentMethod(
                    subscription.getGatewaySubscriptionId(),
                    PaymentMethod.PIX,
                    null,
                    null,
                    null
            );

            paymentMethodRepository.findByBusinessId(subscription.getBusiness().getId())
                    .ifPresent(paymentMethodRepository::delete);

            subscription.setPaymentMethod(PaymentMethod.PIX);
            subscriptionRepository.save(subscription);

            CheckoutResponseDTO pixResponse = paymentGatewayPort.getPendingPixQrCode(subscription.getGatewaySubscriptionId());

            log.info("Payment method updated to PIX for business {}.", subscription.getBusiness().getId());
            return pixResponse;

        } else if (request.paymentMethod() == PaymentMethod.CREDIT_CARD) {

            String savedToken = null;
            if (request.savedCardId() != null) {
                BusinessPaymentMethod existingMethod = getPaymentMethodByIdAndBusiness(request.savedCardId(), subscription.getBusiness().getId());
                savedToken = existingMethod.getCreditCardToken();
            }

            var gatewayResponse = paymentGatewayPort.updateSubscriptionPaymentMethod(
                    subscription.getGatewaySubscriptionId(),
                    PaymentMethod.CREDIT_CARD,
                    savedToken,
                    request.creditCard(),
                    request.creditCardHolderInfo()
            );

            if (request.creditCard() != null) {
                BusinessPaymentMethod paymentMethod = getOrCreatePaymentMethod(subscription.getBusiness());
                paymentMethod.setCardBrand(gatewayResponse.cardBrand());
                paymentMethod.setLastFourDigits(gatewayResponse.lastFourDigits());
                paymentMethod.setCreditCardToken(gatewayResponse.creditCardToken());
                paymentMethodRepository.save(paymentMethod);
            }

            subscription.setPaymentMethod(PaymentMethod.CREDIT_CARD);
            subscriptionRepository.save(subscription);

            log.info("Credit card updated successfully for business {}.", subscription.getBusiness().getId());
            return null;
        } else {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_PAYMENT_METHOD",
                    "Supported methods are CREDIT_CARD or PIX."
            );
        }
    }

    @Transactional
    public void changePlan(String userId, ChangePlanDTO request) {
        Subscription subscription = getSubscriptionByUser(userId);
        requireGatewaySubscriptionId(subscription);

        if (subscription.getStatus() != SubscriptionStatus.ACTIVE) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_STATUS",
                    "You can only change the plan of an active subscription."
            );
        }
        if (subscription.getPlan().getCode().equals(request.planId())) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "SAME_PLAN",
                    "You are already subscribed to this plan."
            );
        }

        Plan newPlan = getPlanByCode(request.planId());
        String savedToken = null;

        if (request.paymentMethod() == PaymentMethod.CREDIT_CARD && request.savedCardId() != null) {
            BusinessPaymentMethod paymentMethod = getPaymentMethodByIdAndBusiness(request.savedCardId(), subscription.getBusiness().getId());
            savedToken = paymentMethod.getCreditCardToken();
        }

        paymentGatewayPort.cancelSubscription(subscription.getGatewaySubscriptionId());

        var gatewayResponse = paymentGatewayPort.createScheduledSubscription(
                subscription.getBusiness(),
                newPlan,
                subscription.getGatewayCustomerId(),
                request.paymentMethod(),
                savedToken,
                request.creditCard(),
                request.creditCardHolderInfo(),
                subscription.getCurrentPeriodEnd()
        );

        if (request.paymentMethod() == PaymentMethod.CREDIT_CARD) {
            if (request.creditCard() != null) {
                BusinessPaymentMethod paymentMethod = getOrCreatePaymentMethod(subscription.getBusiness());
                paymentMethod.setCardBrand(gatewayResponse.cardBrand());
                paymentMethod.setLastFourDigits(gatewayResponse.lastFourDigits());
                paymentMethod.setCreditCardToken(gatewayResponse.creditCardToken());
                paymentMethodRepository.save(paymentMethod);
            }
        } else if (request.paymentMethod() == PaymentMethod.PIX) {
            paymentMethodRepository.findByBusinessId(subscription.getBusiness().getId())
                    .ifPresent(paymentMethodRepository::delete);
        }

        subscription.setPaymentMethod(request.paymentMethod());
        subscription.setGatewaySubscriptionId(gatewayResponse.gatewaySubscriptionId());
        subscriptionRepository.save(subscription);

        log.info("Plan changed successfully for business {} to plan {}. Scheduled to start charging on {}",
                subscription.getBusiness().getId(), newPlan.getCode(), subscription.getCurrentPeriodEnd());
    }

    public CheckoutResponseDTO getPendingPixQrCode(String userId) {
        Subscription subscription = getSubscriptionByUser(userId);

        return paymentRepository.findFirstBySubscriptionIdAndStatusOrderByIdDesc(
                        subscription.getId(), PaymentStatus.PENDING
                )
                .filter(payment -> payment.getPixCopyPaste() != null)
                .map(payment -> new CheckoutResponseDTO(
                        subscription.getGatewaySubscriptionId(),
                        payment.getPixQrCodeBase64(),
                        payment.getPixCopyPaste()
                ))
                .orElse(null);
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
        Subscription subscription = getSubscriptionByUser(userId);

        PlanResponseDTO planDto = subscription.getPlan() != null
                ? PlanResponseDTO.fromEntity(subscription.getPlan())
                : null;

        CreditCardSummaryDTO cardDto = paymentMethodRepository.findByBusinessId(subscription.getBusiness().getId())
                .map(method -> new CreditCardSummaryDTO(method.getId(), method.getCardBrand(), method.getLastFourDigits()))
                .orElse(null);

        LocalDate alertDateThreshold = LocalDate.now(ZoneId.of("America/Sao_Paulo")).plusDays(5);

        boolean hasPending = paymentRepository.existsBySubscriptionIdAndStatusInAndDueDateBeforeOrEqual(
                subscription.getId(),
                List.of(PaymentStatus.PENDING, PaymentStatus.OVERDUE),
                alertDateThreshold
        );

        return new SubscriptionResponseDTO(
                subscription.getStatus(),
                planDto,
                subscription.getCurrentPeriodEnd(),
                subscription.getCancelAtPeriodEnd(),
                cardDto,
                hasPending,
                subscription.getPaymentMethod()
        );
    }

    // ==========================================
    // AUXILIARY METHODS & DATA FETCHING
    // ==========================================

    private CheckoutResponseDTO handleCreditCardCheckout(
            Subscription subscription, Business business, Plan plan, String customerId, CheckoutRequestDTO request) {

        boolean hasValidDays = subscription.getCurrentPeriodEnd() != null
                && !subscription.getCurrentPeriodEnd().isBefore(LocalDate.now(ZoneId.of("America/Sao_Paulo")));

        PaymentGatewayPort.GatewayCreditCardResponse gatewayResponse;

        String savedToken = null;
        if (request.savedCardId() != null) {
            BusinessPaymentMethod existingMethod = getPaymentMethodByIdAndBusiness(request.savedCardId(), business.getId());
            savedToken = existingMethod.getCreditCardToken();
        }

        if (hasValidDays) {
            gatewayResponse = paymentGatewayPort.createScheduledSubscription(
                    business,
                    plan,
                    customerId,
                    PaymentMethod.CREDIT_CARD,
                    savedToken,
                    request.creditCard(),
                    request.creditCardHolderInfo(),
                    subscription.getCurrentPeriodEnd()
            );

            subscription.setStatus(SubscriptionStatus.ACTIVE);
            log.info("Subscription reactivated and scheduled to charge on {}", subscription.getCurrentPeriodEnd());
        } else {
            gatewayResponse = paymentGatewayPort.processCreditCardCheckout(
                    business, plan, customerId, savedToken, request.creditCard(), request.creditCardHolderInfo()
            );
            subscription.setStatus(SubscriptionStatus.PENDING);
        }

        if (request.creditCard() != null) {
            BusinessPaymentMethod paymentMethod = getOrCreatePaymentMethod(business);
            String brand = gatewayResponse.cardBrand();
            String lastFour = gatewayResponse.lastFourDigits() != null ? gatewayResponse.lastFourDigits() : request.creditCard().number().substring(request.creditCard().number().length() - 4);

            paymentMethod.setCardBrand(brand);
            paymentMethod.setLastFourDigits(lastFour);
            paymentMethod.setCreditCardToken(gatewayResponse.creditCardToken());
            paymentMethodRepository.save(paymentMethod);
        }

        subscription.setPaymentMethod(PaymentMethod.CREDIT_CARD);
        subscription.setPlan(plan);
        subscription.setGatewaySubscriptionId(gatewayResponse.gatewaySubscriptionId());
        subscription.setCancelAtPeriodEnd(false);
        subscriptionRepository.save(subscription);

        log.info("Credit Card checkout processed for business {}.", business.getId());

        return null;
    }

    private CheckoutResponseDTO handlePixCheckout(Subscription subscription, Business business, Plan plan, String customerId) {

        boolean hasValidDays = subscription.getCurrentPeriodEnd() != null
                && !subscription.getCurrentPeriodEnd().isBefore(LocalDate.now(ZoneId.of("America/Sao_Paulo")));

        CheckoutResponseDTO pixResponse;

        if (hasValidDays) {
            var gatewayResponse = paymentGatewayPort.createScheduledSubscription(
                    business,
                    plan,
                    customerId,
                    PaymentMethod.PIX,
                    null,
                    null,
                    null,
                    subscription.getCurrentPeriodEnd()
            );

            pixResponse = new CheckoutResponseDTO(gatewayResponse.gatewaySubscriptionId(), null, null);

            subscription.setStatus(SubscriptionStatus.ACTIVE);
            log.info("Subscription reactivated and scheduled to start charging PIX on {}", subscription.getCurrentPeriodEnd());
        } else {
            pixResponse = paymentGatewayPort.processPixCheckout(business, plan, customerId);
            subscription.setStatus(SubscriptionStatus.PENDING);
        }

        subscription.setPaymentMethod(PaymentMethod.PIX);
        subscription.setPlan(plan);
        subscription.setGatewaySubscriptionId(pixResponse.gatewaySubscriptionId());
        subscription.setCancelAtPeriodEnd(false);
        subscriptionRepository.save(subscription);

        log.info("PIX checkout processed for business {}.", business.getId());
        return pixResponse;
    }

    private Subscription getSubscriptionByUser(String userId) {
        return subscriptionRepository.findByUserIdWithBusinessAndPlan(userId)
                .orElseThrow(() -> new AppException(
                        HttpStatus.NOT_FOUND,
                        "SUBSCRIPTION_NOT_FOUND",
                        "No subscription found for this user."
                ));
    }

    private Subscription getSubscriptionByBusiness(String businessId) {
        return subscriptionRepository.findByBusinessIdWithBusiness(businessId)
                .orElseThrow(() -> new AppException(
                        HttpStatus.NOT_FOUND,
                        "SUBSCRIPTION_NOT_FOUND",
                        "No subscription found for this business."
                ));
    }

    private Plan getPlanByCode(String planCode) {
        return planRepository.findByCode(planCode)
                .orElseThrow(() -> new AppException(
                        HttpStatus.NOT_FOUND,
                        "PLAN_NOT_FOUND",
                        "The selected plan is not available."
                ));
    }

    private BusinessPaymentMethod getOrCreatePaymentMethod(Business business) {
        return paymentMethodRepository.findByBusinessId(business.getId())
                .orElseGet(() -> BusinessPaymentMethod.builder()
                        .business(business)
                        .isDefault(true)
                        .build());
    }

    private BusinessPaymentMethod getExistingPaymentMethod(String businessId) {
        return paymentMethodRepository.findByBusinessId(businessId)
                .orElseThrow(() -> new AppException(
                        HttpStatus.NOT_FOUND,
                        "PAYMENT_METHOD_NOT_FOUND",
                        "Payment method not found."
                ));
    }

    private BusinessPaymentMethod getPaymentMethodByIdAndBusiness(String cardId, String businessId) {
        BusinessPaymentMethod method = paymentMethodRepository.findById(cardId)
                .orElseThrow(() -> new AppException(
                        HttpStatus.NOT_FOUND,
                        "PAYMENT_METHOD_NOT_FOUND",
                        "Saved payment method not found."
                ));

        if (!method.getBusiness().getId().equals(businessId)) {
            throw new AppException(
                    HttpStatus.FORBIDDEN,
                    "PAYMENT_METHOD_DENIED",
                    "This payment method does not belong to your business."
            );
        }
        return method;
    }

    private void requireGatewaySubscriptionId(Subscription subscription) {
        if (subscription.getGatewaySubscriptionId() == null) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_SUBSCRIPTION",
                    "This subscription does not have an active gateway link."
            );
        }
    }
}