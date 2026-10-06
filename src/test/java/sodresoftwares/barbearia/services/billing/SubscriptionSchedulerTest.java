package sodresoftwares.barbearia.services.billing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import sodresoftwares.barbearia.model.business.Business;
import sodresoftwares.barbearia.model.billing.Payment;
import sodresoftwares.barbearia.model.billing.PaymentStatus;
import sodresoftwares.barbearia.model.billing.Subscription;
import sodresoftwares.barbearia.model.billing.SubscriptionStatus;
import sodresoftwares.barbearia.ports.PaymentGatewayPort;
import sodresoftwares.barbearia.repositories.billing.PaymentRepository;
import sodresoftwares.barbearia.repositories.billing.SubscriptionRepository;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SubscriptionScheduler Tests")
class SubscriptionSchedulerTest {

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private PaymentGatewayPort paymentGatewayPort;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private SubscriptionHistoryService subscriptionHistoryService;

    @InjectMocks
    private SubscriptionScheduler scheduler;

    private Subscription mockSubscription;
    private Payment mockPayment;
    private Business mockBusiness;

    private final String SUBSCRIPTION_ID = "sub-123";
    private final String BUSINESS_ID = "biz-123";
    private final String GATEWAY_SUB_ID = "gw-sub-123";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(scheduler, "gracePeriodDays", 5);
        ReflectionTestUtils.setField(scheduler, "cancellationDays", 15);

        mockBusiness = Business.builder()
                .id(BUSINESS_ID)
                .build();

        mockSubscription = Subscription.builder()
                .id(SUBSCRIPTION_ID)
                .business(mockBusiness)
                .gatewaySubscriptionId(GATEWAY_SUB_ID)
                .build();

        mockPayment = Payment.builder()
                .id("pay-123")
                .status(PaymentStatus.PENDING)
                .build();
    }

    @Test
    @DisplayName("Should process expired trials and suspend them")
    void testProcessDailySubscriptions_ExpiredTrials() {
        // Arrange
        mockSubscription.setStatus(SubscriptionStatus.TRIAL);
        when(subscriptionRepository.findByStatusAndCurrentPeriodEndBefore(
                eq(SubscriptionStatus.TRIAL), any(LocalDate.class)))
                .thenReturn(List.of(mockSubscription));

        // Act
        scheduler.processDailySubscriptions();

        // Assert
        assertThat(mockSubscription.getStatus()).isEqualTo(SubscriptionStatus.SUSPENDED);

        verify(subscriptionHistoryService).logStatusChange(
                mockSubscription,
                SubscriptionStatus.SUSPENDED,
                "SCHEDULER: TRIAL_EXPIRED"
        );
        verify(subscriptionRepository).saveAll(List.of(mockSubscription));
    }

    @Test
    @DisplayName("Should process overdue active subscriptions and suspend them after grace period")
    void testProcessDailySubscriptions_OverdueSuspensions() {
        // Arrange
        mockSubscription.setStatus(SubscriptionStatus.ACTIVE);
        when(subscriptionRepository.findByStatusAndCurrentPeriodEndBefore(
                eq(SubscriptionStatus.ACTIVE), any(LocalDate.class)))
                .thenReturn(List.of(mockSubscription));

        // Act
        scheduler.processDailySubscriptions();

        // Assert
        assertThat(mockSubscription.getStatus()).isEqualTo(SubscriptionStatus.SUSPENDED);

        verify(subscriptionHistoryService).logStatusChange(
                mockSubscription,
                SubscriptionStatus.SUSPENDED,
                "SCHEDULER: 5_DAYS_OVERDUE"
        );
        verify(subscriptionRepository).saveAll(List.of(mockSubscription));
    }

    @Test
    @DisplayName("Should process definitive cancellations, call gateway and update status")
    void testProcessDailySubscriptions_DefinitiveCancellations_Success() {
        // Arrange
        mockSubscription.setStatus(SubscriptionStatus.SUSPENDED);
        when(subscriptionRepository.findByStatusAndCurrentPeriodEndBefore(
                eq(SubscriptionStatus.SUSPENDED), any(LocalDate.class)))
                .thenReturn(List.of(mockSubscription));

        // Act
        scheduler.processDailySubscriptions();

        // Assert
        assertThat(mockSubscription.getStatus()).isEqualTo(SubscriptionStatus.CANCELED);

        verify(paymentGatewayPort).cancelSubscription(GATEWAY_SUB_ID);
        verify(subscriptionHistoryService).logStatusChange(
                mockSubscription,
                SubscriptionStatus.CANCELED,
                "SCHEDULER: 15_DAYS_OVERDUE_CANCELLATION"
        );
        verify(subscriptionRepository).saveAll(List.of(mockSubscription));
    }

    @Test
    @DisplayName("Should proceed with cancellation in DB even if gateway throws exception")
    void testProcessDailySubscriptions_DefinitiveCancellations_GatewayFails() {
        // Arrange
        mockSubscription.setStatus(SubscriptionStatus.SUSPENDED);
        when(subscriptionRepository.findByStatusAndCurrentPeriodEndBefore(
                eq(SubscriptionStatus.SUSPENDED), any(LocalDate.class)))
                .thenReturn(List.of(mockSubscription));

        doThrow(new RuntimeException("Gateway error")).when(paymentGatewayPort).cancelSubscription(anyString());

        // Act
        scheduler.processDailySubscriptions();

        // Assert
        assertThat(mockSubscription.getStatus()).isEqualTo(SubscriptionStatus.CANCELED);
        verify(subscriptionRepository).saveAll(List.of(mockSubscription));
    }

    @Test
    @DisplayName("Should expire pending payments when due date is past")
    void testProcessDailySubscriptions_ExpiredPendingPayments() {
        // Arrange
        when(paymentRepository.findByStatusAndDueDateBefore(
                eq(PaymentStatus.PENDING), any(LocalDate.class)))
                .thenReturn(List.of(mockPayment));

        // Act
        scheduler.processDailySubscriptions();

        // Assert
        assertThat(mockPayment.getStatus()).isEqualTo(PaymentStatus.EXPIRED);
        verify(paymentRepository).saveAll(List.of(mockPayment));
    }

    @Test
    @DisplayName("Should cancel subscriptions scheduled by user to cancel at period end")
    void testProcessDailySubscriptions_ScheduledCancellations() {
        // Arrange
        mockSubscription.setStatus(SubscriptionStatus.ACTIVE);
        mockSubscription.setCancelAtPeriodEnd(true);
        when(subscriptionRepository.findByCancelAtPeriodEndTrueAndCurrentPeriodEndBefore(any(LocalDate.class)))
                .thenReturn(List.of(mockSubscription));

        // Act
        scheduler.processDailySubscriptions();

        // Assert
        assertThat(mockSubscription.getStatus()).isEqualTo(SubscriptionStatus.CANCELED);

        verify(subscriptionHistoryService).logStatusChange(
                mockSubscription,
                SubscriptionStatus.CANCELED,
                "SCHEDULER: USER_SCHEDULED_CANCELLATION"
        );
        verify(subscriptionRepository).saveAll(List.of(mockSubscription));
    }

    @Test
    @DisplayName("Should do nothing when no subscriptions match criteria")
    void testProcessDailySubscriptions_EmptyResults() {
        // Arrange
        when(subscriptionRepository.findByStatusAndCurrentPeriodEndBefore(any(), any()))
                .thenReturn(Collections.emptyList());
        when(subscriptionRepository.findByCancelAtPeriodEndTrueAndCurrentPeriodEndBefore(any()))
                .thenReturn(Collections.emptyList());
        when(paymentRepository.findByStatusAndDueDateBefore(any(), any()))
                .thenReturn(Collections.emptyList());

        // Act
        scheduler.processDailySubscriptions();

        // Assert
        verify(subscriptionHistoryService, never()).logStatusChange(any(), any(), any());
        verify(paymentGatewayPort, never()).cancelSubscription(anyString());
    }
}