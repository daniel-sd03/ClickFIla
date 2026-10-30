package sodresoftwares.barbearia.services.billing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sodresoftwares.barbearia.model.billing.Payment;
import sodresoftwares.barbearia.model.billing.PaymentStatus;
import sodresoftwares.barbearia.model.billing.Subscription;
import sodresoftwares.barbearia.model.billing.SubscriptionStatus;
import sodresoftwares.barbearia.ports.PaymentGatewayPort;
import sodresoftwares.barbearia.repositories.billing.PaymentRepository;
import sodresoftwares.barbearia.repositories.billing.SubscriptionRepository;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SubscriptionScheduler {

    private final SubscriptionRepository subscriptionRepository;
    private final PaymentGatewayPort paymentGatewayPort;
    private final PaymentRepository paymentRepository;
    private final SubscriptionHistoryService subscriptionHistoryService;

    @Value("${billing.grace.period.days}")
    private int gracePeriodDays;

    @Value("${billing.cancellation.days}")
    private int cancellationDays;

    // Runs every day at 02:00 AM
    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void processDailySubscriptions() {
        log.info("Starting nightly subscription verification...");
        LocalDate today = LocalDate.now();

        processExpiredTrials(today);
        processOverdueSuspensions(today);
        processDefinitiveCancellations(today);
        processExpiredPendingPayments(today);
        processScheduledCancellations(today);
        processAbandonedPendingSubscriptions(today);

        log.info("Nightly subscription verification completed.");
    }

    private void processExpiredTrials(LocalDate today) {
        List<Subscription> expiredTrials = subscriptionRepository
                .findByStatusAndCurrentPeriodEndBefore(SubscriptionStatus.TRIAL, today);

        for (Subscription subscription : expiredTrials) {
            subscriptionHistoryService.logStatusChange(
                    subscription,
                    SubscriptionStatus.SUSPENDED,
                    "SCHEDULER: TRIAL_EXPIRED"
            );
            subscription.setStatus(SubscriptionStatus.SUSPENDED);
            log.info("Trial expired for business {}. Status changed to SUSPENDED.",
                    subscription.getBusiness().getId());
        }
        subscriptionRepository.saveAll(expiredTrials);
    }

    private void processOverdueSuspensions(LocalDate today) {
        // Suspends active subscriptions that are more than 5 days overdue
        LocalDate cutoffDate = today.minusDays(gracePeriodDays);
        List<Subscription> overdueSubscriptions = subscriptionRepository
                .findByStatusAndCurrentPeriodEndBefore(SubscriptionStatus.ACTIVE, cutoffDate);

        for (Subscription subscription : overdueSubscriptions) {
            subscriptionHistoryService.logStatusChange(
                    subscription,
                    SubscriptionStatus.SUSPENDED,
                    "SCHEDULER: 5_DAYS_OVERDUE"
            );
            subscription.setStatus(SubscriptionStatus.SUSPENDED);
            log.info("Subscription {} is more than {} days overdue. Status changed to SUSPENDED.",
                    subscription.getId(), gracePeriodDays);
        }
        subscriptionRepository.saveAll(overdueSubscriptions);
    }

    private void processDefinitiveCancellations(LocalDate today) {
        // Cancels suspended subscriptions that have been overdue for more than 15 days
        LocalDate cutoffDate = today.minusDays(cancellationDays);
        List<Subscription> subscriptionsToCancel = subscriptionRepository
                .findByStatusAndCurrentPeriodEndBefore(SubscriptionStatus.SUSPENDED, cutoffDate);

        for (Subscription subscription : subscriptionsToCancel) {
            subscriptionHistoryService.logStatusChange(
                    subscription,
                    SubscriptionStatus.CANCELED,
                    "SCHEDULER: 15_DAYS_OVERDUE_CANCELLATION"
            );
            subscription.setStatus(SubscriptionStatus.CANCELED);

            if (subscription.getGatewaySubscriptionId() != null) {
                try {
                    paymentGatewayPort.cancelSubscription(subscription.getGatewaySubscriptionId());
                } catch (Exception e) {
                    log.error("Failed to cancel subscription {} in gateway.",
                            subscription.getGatewaySubscriptionId(), e);
                }
            }
            log.info("Subscription {} definitively CANCELED due to {} days of non-payment.",
                    subscription.getId(), cancellationDays);
        }
        subscriptionRepository.saveAll(subscriptionsToCancel);
    }

    private void processExpiredPendingPayments(LocalDate today) {
        List<Payment> expiredPayments = paymentRepository
                .findByStatusAndDueDateBefore(PaymentStatus.PENDING, today);

        for (Payment payment : expiredPayments) {
            payment.setStatus(PaymentStatus.EXPIRED);
            log.info("Checkout abandoned: Payment {} marked as EXPIRED.", payment.getId());
        }
        paymentRepository.saveAll(expiredPayments);
    }

    private void processScheduledCancellations(LocalDate today) {
        List<Subscription> scheduledCancellations = subscriptionRepository
                .findByCancelAtPeriodEndTrueAndCurrentPeriodEndBefore(today);

        for (Subscription subscription : scheduledCancellations) {

            subscriptionHistoryService.logStatusChange(
                    subscription,
                    SubscriptionStatus.CANCELED,
                    "SCHEDULER: USER_SCHEDULED_CANCELLATION"
            );

            subscription.setStatus(SubscriptionStatus.CANCELED);
            log.info("Subscription {} CANCELED at period end by user request.", subscription.getId());
        }
        subscriptionRepository.saveAll(scheduledCancellations);
    }

    private void processAbandonedPendingSubscriptions(LocalDate today) {
        LocalDate cutoffDate = today.minusDays(2);

        List<Subscription> abandonedSubscriptions = subscriptionRepository
                .findByStatusAndCurrentPeriodStartBefore(SubscriptionStatus.PENDING, cutoffDate);

        for (Subscription subscription : abandonedSubscriptions) {
            subscriptionHistoryService.logStatusChange(
                    subscription,
                    SubscriptionStatus.CANCELED,
                    "SCHEDULER: ABANDONED_CHECKOUT"
            );

            subscription.setStatus(SubscriptionStatus.CANCELED);

            if (subscription.getGatewaySubscriptionId() != null) {
                try {
                    paymentGatewayPort.cancelSubscription(subscription.getGatewaySubscriptionId());
                } catch (Exception e) {
                    log.error("Failed to cancel abandoned subscription {} in gateway.",
                            subscription.getGatewaySubscriptionId(), e);
                }
            }

            log.info("Abandoned PENDING subscription {} CANCELED by scheduler.", subscription.getId());
        }
        subscriptionRepository.saveAll(abandonedSubscriptions);
    }
}