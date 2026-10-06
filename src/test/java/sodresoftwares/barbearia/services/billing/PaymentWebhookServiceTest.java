package sodresoftwares.barbearia.services.billing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import sodresoftwares.barbearia.model.business.Business;
import sodresoftwares.barbearia.model.billing.*;
import sodresoftwares.barbearia.repositories.billing.PaymentRepository;
import sodresoftwares.barbearia.repositories.billing.SubscriptionRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentWebhookService Tests")
class PaymentWebhookServiceTest {

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private SubscriptionHistoryService subscriptionHistoryService;

    @InjectMocks
    private PaymentWebhookService webhookService;

    private Subscription mockSubscription;
    private Payment mockPayment;

    private final String BUSINESS_ID = "biz-123";
    private final String SUBSCRIPTION_ID = "sub-123";
    private final String INVOICE_ID = "inv_123";

    @BeforeEach
    void setUp() {
        Business mockBusiness = Business.builder()
                .id(BUSINESS_ID)
                .build();

        Plan mockMonthlyPlan = Plan.builder()
                .billingCycle(BillingCycle.MONTHLY)
                .price(new BigDecimal("50.00"))
                .build();

        mockSubscription = Subscription.builder()
                .id(SUBSCRIPTION_ID)
                .business(mockBusiness)
                .plan(mockMonthlyPlan)
                .status(SubscriptionStatus.SUSPENDED)
                .currentPeriodEnd(LocalDate.now())
                .build();

        mockPayment = Payment.builder()
                .id("pay-123")
                .gatewayInvoiceId(INVOICE_ID)
                .subscription(mockSubscription)
                .status(PaymentStatus.PENDING)
                .amount(new BigDecimal("50.00"))
                .build();
    }

    // ==================== PAYMENT_CREATED TESTS ====================

    @Test
    @DisplayName("Should create pending payment when PAYMENT_CREATED event is received")
    void testProcessWebhook_PaymentCreated_Success() {
        // Arrange
        when(paymentRepository.existsByGatewayInvoiceId(INVOICE_ID)).thenReturn(false);
        when(subscriptionRepository.findByBusinessId(BUSINESS_ID)).thenReturn(Optional.of(mockSubscription));

        Map<String, Object> payload = Map.of(
                "event", "PAYMENT_CREATED",
                "payment", Map.of(
                        "id", INVOICE_ID,
                        "externalReference", BUSINESS_ID,
                        "value", 50.00,
                        "dueDate", LocalDate.now().toString(),
                        "invoiceUrl", "http://asaas.com/inv_123"
                )
        );

        // Act
        webhookService.processWebhook(payload);

        // Assert
        verify(paymentRepository).save(argThat(payment ->
                payment.getGatewayInvoiceId().equals(INVOICE_ID) &&
                        payment.getStatus().equals(PaymentStatus.PENDING) &&
                        payment.getInvoiceUrl().equals("http://asaas.com/inv_123") &&
                        payment.getAmount().compareTo(new BigDecimal("50.0")) == 0
        ));
    }

    @Test
    @DisplayName("Should ignore PAYMENT_CREATED event if payment already exists (Idempotency)")
    void testProcessWebhook_PaymentCreated_Idempotency() {
        // Arrange
        when(paymentRepository.existsByGatewayInvoiceId(INVOICE_ID)).thenReturn(true);

        Map<String, Object> payload = Map.of(
                "event", "PAYMENT_CREATED",
                "payment", Map.of("id", INVOICE_ID, "externalReference", BUSINESS_ID)
        );

        // Act
        webhookService.processWebhook(payload);

        // Assert
        verify(subscriptionRepository, never()).findByBusinessId(any());
        verify(paymentRepository, never()).save(any());
    }

    // ==================== PAYMENT_CONFIRMED TESTS ====================

    @Test
    @DisplayName("Should extend monthly subscription and log history when PAYMENT_CONFIRMED is received")
    void testProcessWebhook_PaymentConfirmed_MonthlySuccess() {
        // Arrange
        when(paymentRepository.findByGatewayInvoiceId(INVOICE_ID)).thenReturn(Optional.of(mockPayment));
        when(subscriptionRepository.findByIdWithLock(SUBSCRIPTION_ID)).thenReturn(Optional.of(mockSubscription));

        Map<String, Object> payload = Map.of(
                "event", "PAYMENT_CONFIRMED",
                "payment", Map.of("id", INVOICE_ID, "externalReference", BUSINESS_ID)
        );

        LocalDate expectedNewEndDate = LocalDate.now().plusMonths(1);

        // Act
        webhookService.processWebhook(payload);

        // Assert
        assertThat(mockPayment.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(mockSubscription.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(mockSubscription.getCurrentPeriodEnd()).isEqualTo(expectedNewEndDate);

        verify(paymentRepository).save(mockPayment);
        verify(subscriptionRepository).save(mockSubscription);

        verify(subscriptionHistoryService).logStatusChange(
                mockSubscription,
                SubscriptionStatus.ACTIVE,
                "WEBHOOK: PAYMENT_CONFIRMED"
        );
    }

    @Test
    @DisplayName("Should ignore PAYMENT_CONFIRMED event if payment is already PAID (Idempotency)")
    void testProcessWebhook_PaymentConfirmed_Idempotency() {
        // Arrange
        mockPayment.setStatus(PaymentStatus.PAID);
        when(paymentRepository.findByGatewayInvoiceId(INVOICE_ID)).thenReturn(Optional.of(mockPayment));

        Map<String, Object> payload = Map.of(
                "event", "PAYMENT_CONFIRMED",
                "payment", Map.of("id", INVOICE_ID)
        );

        // Act
        webhookService.processWebhook(payload);

        // Assert
        verify(subscriptionRepository, never()).findByIdWithLock(any());
        verify(paymentRepository, never()).save(any());
    }

    // ==================== PAYMENT_OVERDUE TESTS ====================

    @Test
    @DisplayName("Should mark payment as OVERDUE if subscription is ACTIVE (Real Debt)")
    void testProcessWebhook_PaymentOverdue_RealDebt() {
        // Arrange
        mockSubscription.setStatus(SubscriptionStatus.ACTIVE);
        when(paymentRepository.findByGatewayInvoiceId(INVOICE_ID)).thenReturn(Optional.of(mockPayment));

        Map<String, Object> payload = Map.of(
                "event", "PAYMENT_OVERDUE",
                "payment", Map.of("id", INVOICE_ID)
        );

        // Act
        webhookService.processWebhook(payload);

        // Assert
        assertThat(mockPayment.getStatus()).isEqualTo(PaymentStatus.OVERDUE);
        verify(paymentRepository).save(mockPayment);
    }

    @Test
    @DisplayName("Should mark payment as EXPIRED if subscription is NOT ACTIVE (Abandoned Checkout)")
    void testProcessWebhook_PaymentOverdue_AbandonedCheckout() {
        // Arrange
        mockSubscription.setStatus(SubscriptionStatus.TRIAL);
        when(paymentRepository.findByGatewayInvoiceId(INVOICE_ID)).thenReturn(Optional.of(mockPayment));

        Map<String, Object> payload = Map.of(
                "event", "PAYMENT_OVERDUE",
                "payment", Map.of("id", INVOICE_ID)
        );

        // Act
        webhookService.processWebhook(payload);

        // Assert
        assertThat(mockPayment.getStatus()).isEqualTo(PaymentStatus.EXPIRED);
        verify(paymentRepository).save(mockPayment);
    }

    // ==================== PAYMENT_REFUNDED TESTS ====================

    @Test
    @DisplayName("Should suspend subscription and mark payment as REFUNDED when money is returned")
    void testProcessWebhook_PaymentRefunded_Success() {
        // Arrange
        when(paymentRepository.findByGatewayInvoiceId(INVOICE_ID)).thenReturn(Optional.of(mockPayment));

        Map<String, Object> payload = Map.of(
                "event", "PAYMENT_REFUNDED",
                "payment", Map.of("id", INVOICE_ID)
        );

        // Act
        webhookService.processWebhook(payload);

        // Assert
        assertThat(mockPayment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(mockSubscription.getStatus()).isEqualTo(SubscriptionStatus.SUSPENDED);

        verify(paymentRepository).save(mockPayment);
        verify(subscriptionRepository).save(mockSubscription);

        verify(subscriptionHistoryService).logStatusChange(
                mockSubscription,
                SubscriptionStatus.SUSPENDED,
                "WEBHOOK: PAYMENT_REFUNDED"
        );
    }

    // ==================== EDGE CASES ====================

    @Test
    @DisplayName("Should return silently if event or payment object is null")
    void testProcessWebhook_NullPayload() {
        // Act
        webhookService.processWebhook(Map.of());

        // Assert
        verify(paymentRepository, never()).existsByGatewayInvoiceId(any());
        verify(subscriptionRepository, never()).findByBusinessId(any());
    }
}