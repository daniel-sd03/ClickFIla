package sodresoftwares.barbearia.services.billing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import sodresoftwares.barbearia.dto.billing.CheckoutResponseDTO;
import sodresoftwares.barbearia.model.business.Business;
import sodresoftwares.barbearia.model.billing.*;
import sodresoftwares.barbearia.ports.PaymentGatewayPort;
import sodresoftwares.barbearia.repositories.billing.PaymentRepository;
import sodresoftwares.barbearia.repositories.billing.PlanRepository;
import sodresoftwares.barbearia.repositories.billing.SubscriptionRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

    @Mock
    private PaymentHistoryService paymentHistoryService;

    @Mock
    private PlanRepository planRepository;

    @Mock
    private PaymentGatewayPort paymentGatewayPort;

    @InjectMocks
    private PaymentWebhookService webhookService;

    private Subscription mockSubscription;
    private Payment mockPayment;
    private Plan mockMonthlyPlan;
    private Plan mockYearlyPlan;

    private final String BUSINESS_ID = "biz-123";
    private final String SUBSCRIPTION_ID = "sub-123";
    private final String INVOICE_ID = "inv_123";

    @BeforeEach
    void setUp() {
        Business mockBusiness = Business.builder()
                .id(BUSINESS_ID)
                .build();

        mockMonthlyPlan = Plan.builder()
                .code("BASIC_MONTHLY")
                .billingCycle(BillingCycle.MONTHLY)
                .price(new BigDecimal("50.00"))
                .build();

        mockYearlyPlan = Plan.builder()
                .billingCycle(BillingCycle.YEARLY)
                .price(new BigDecimal("500.00"))
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


    // ==========================================
    // PAYMENT CREATED TESTS
    // ==========================================

    @Test
    @DisplayName("Should create pending PIX payment, fetch QR Code and log history")
    void testProcessWebhook_PaymentCreated_Success() {
        when(paymentRepository.existsByGatewayInvoiceId(INVOICE_ID)).thenReturn(false);
        when(subscriptionRepository.findByBusinessId(BUSINESS_ID)).thenReturn(Optional.of(mockSubscription));

        CheckoutResponseDTO mockPixData = new CheckoutResponseDTO(null, "imgBase64",
                "copiaECola");
        when(paymentGatewayPort.getPixQrCodeByInvoiceId(INVOICE_ID)).thenReturn(mockPixData);

        Map<String, Object> payload = Map.of(
                "event", "PAYMENT_CREATED",
                "payment", Map.of(
                        "id", INVOICE_ID,
                        "externalReference", BUSINESS_ID,
                        "value", 50.00,
                        "dueDate", LocalDate.now().toString(),
                        "billingType", "PIX",
                        "invoiceUrl", "http://asaas.com/inv_123"
                )
        );

        webhookService.processWebhook(payload);

        verify(paymentRepository).save(argThat(payment ->
                payment.getGatewayInvoiceId().equals(INVOICE_ID) &&
                        payment.getPixCopyPaste().equals("copiaECola")
        ));
    }

    @Test
    @DisplayName("Should ignore PAYMENT_CREATED event if payment already exists (Idempotency)")
    void testProcessWebhook_PaymentCreated_Idempotency() {
        when(paymentRepository.existsByGatewayInvoiceId(INVOICE_ID)).thenReturn(true);

        Map<String, Object> payload = Map.of(
                "event", "PAYMENT_CREATED",
                "payment", Map.of("id", INVOICE_ID, "externalReference", BUSINESS_ID)
        );

        webhookService.processWebhook(payload);

        verify(subscriptionRepository, never()).findByBusinessId(any());
        verify(paymentRepository, never()).save(any());
        verify(paymentHistoryService, never()).logStatusChange(any(), any(), any());
    }


    // ==========================================
    // PAYMENT CONFIRMED / RECEIVED TESTS
    // ==========================================

    @Test
    @DisplayName("Should extend monthly subscription and log histories when PAYMENT_CONFIRMED is received")
    void testProcessWebhook_PaymentConfirmed_MonthlySuccess() {
        when(paymentRepository.findByGatewayInvoiceId(INVOICE_ID)).thenReturn(Optional.of(mockPayment));
        when(subscriptionRepository.findByIdWithLock(SUBSCRIPTION_ID)).thenReturn(Optional.of(mockSubscription));

        Map<String, Object> payload = Map.of(
                "event", "PAYMENT_CONFIRMED",
                "payment", Map.of("id", INVOICE_ID)
        );

        LocalDate expectedNewEndDate = LocalDate.now().plusMonths(1);

        webhookService.processWebhook(payload);

        assertThat(mockPayment.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(mockPayment.getLastGatewayEvent()).isEqualTo("PAYMENT_CONFIRMED");
        assertThat(mockSubscription.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(mockSubscription.getCurrentPeriodEnd()).isEqualTo(expectedNewEndDate);

        verify(paymentRepository).save(mockPayment);
        verify(paymentHistoryService).logStatusChange(mockPayment, PaymentStatus.PAID, "PAYMENT_CONFIRMED");
        verify(subscriptionRepository).save(mockSubscription);
        verify(subscriptionHistoryService).logStatusChange(mockSubscription, SubscriptionStatus.ACTIVE,
                "WEBHOOK: PAYMENT_CONFIRMED");
    }

    @Test
    @DisplayName("Should calculate days to add when PAYMENT_CONFIRMED is received for a YEARLY plan")
    void testProcessWebhook_PaymentConfirmed_YearlySuccess() {
        mockSubscription.setPlan(mockYearlyPlan);
        mockPayment.setAmount(new BigDecimal("500.00"));

        when(paymentRepository.findByGatewayInvoiceId(INVOICE_ID)).thenReturn(Optional.of(mockPayment));
        when(subscriptionRepository.findByIdWithLock(SUBSCRIPTION_ID)).thenReturn(Optional.of(mockSubscription));

        Map<String, Object> payload = Map.of(
                "event", "PAYMENT_CONFIRMED",
                "payment", Map.of("id", INVOICE_ID)
        );

        LocalDate expectedNewEndDate = LocalDate.now().plusDays(365);

        webhookService.processWebhook(payload);

        assertThat(mockSubscription.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(mockSubscription.getCurrentPeriodEnd()).isEqualTo(expectedNewEndDate);
    }

    @Test
    @DisplayName("Should update subscription plan when description (planCode) is provided and different")
    void testProcessWebhook_PaymentConfirmed_WithPlanUpdate() {
        // Arrange
        Plan newProPlan = Plan.builder()
                .code("PRO_MONTHLY")
                .billingCycle(BillingCycle.MONTHLY)
                .price(new BigDecimal("150.00"))
                .build();

        when(paymentRepository.findByGatewayInvoiceId(INVOICE_ID)).thenReturn(Optional.of(mockPayment));
        when(subscriptionRepository.findByIdWithLock(SUBSCRIPTION_ID)).thenReturn(Optional.of(mockSubscription));

        when(planRepository.findByCode("PRO_MONTHLY")).thenReturn(Optional.of(newProPlan));

        Map<String, Object> payload = Map.of(
                "event", "PAYMENT_CONFIRMED",
                "payment", Map.of(
                        "id", INVOICE_ID,
                        "description", "PRO_MONTHLY"
                )
        );

        // Act
        webhookService.processWebhook(payload);

        // Assert
        assertThat(mockSubscription.getPlan()).isEqualTo(newProPlan);
        assertThat(mockSubscription.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);

        verify(planRepository).findByCode("PRO_MONTHLY");
        verify(subscriptionRepository).save(mockSubscription);
    }


    // ==========================================
    // PAYMENT OVERDUE & CANCELLED TESTS
    // ==========================================

    @Test
    @DisplayName("Should mark payment as OVERDUE and log history if subscription is ACTIVE (Real Debt)")
    void testProcessWebhook_PaymentOverdue_RealDebt() {
        mockSubscription.setStatus(SubscriptionStatus.ACTIVE);
        when(paymentRepository.findByGatewayInvoiceId(INVOICE_ID)).thenReturn(Optional.of(mockPayment));

        Map<String, Object> payload = Map.of(
                "event", "PAYMENT_OVERDUE",
                "payment", Map.of("id", INVOICE_ID)
        );

        webhookService.processWebhook(payload);

        assertThat(mockPayment.getStatus()).isEqualTo(PaymentStatus.OVERDUE);
        verify(paymentHistoryService).logStatusChange(mockPayment, PaymentStatus.OVERDUE, "PAYMENT_OVERDUE");
        verify(paymentRepository).save(mockPayment);
    }

    @Test
    @DisplayName("Should mark payment as EXPIRED and log history when PAYMENT_BANK_SLIP_CANCELLED is received")
    void testProcessWebhook_PaymentCancelled() {
        when(paymentRepository.findByGatewayInvoiceId(INVOICE_ID)).thenReturn(Optional.of(mockPayment));

        Map<String, Object> payload = Map.of(
                "event", "PAYMENT_BANK_SLIP_CANCELLED",
                "payment", Map.of("id", INVOICE_ID)
        );

        webhookService.processWebhook(payload);

        assertThat(mockPayment.getStatus()).isEqualTo(PaymentStatus.EXPIRED);
        verify(paymentHistoryService).logStatusChange(mockPayment, PaymentStatus.EXPIRED, "PAYMENT_BANK_SLIP_CANCELLED");
        verify(paymentRepository).save(mockPayment);
    }


    // ==========================================
    // PAYMENT REFUNDED TESTS
    // ==========================================

    @Test
    @DisplayName("Should suspend subscription and mark payment as REFUNDED when money is returned")
    void testProcessWebhook_PaymentRefunded_Success() {
        mockSubscription.setStatus(SubscriptionStatus.ACTIVE);

        when(paymentRepository.findByGatewayInvoiceId(INVOICE_ID)).thenReturn(Optional.of(mockPayment));

        Map<String, Object> payload = Map.of(
                "event", "PAYMENT_REFUNDED",
                "payment", Map.of("id", INVOICE_ID)
        );

        webhookService.processWebhook(payload);

        assertThat(mockPayment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(mockSubscription.getStatus()).isEqualTo(SubscriptionStatus.SUSPENDED);

        verify(paymentHistoryService).logStatusChange(mockPayment, PaymentStatus.REFUNDED, "PAYMENT_REFUNDED");
        verify(paymentRepository).save(mockPayment);
        verify(subscriptionRepository).save(mockSubscription);
        verify(subscriptionHistoryService).logStatusChange(mockSubscription, SubscriptionStatus.SUSPENDED,
                "WEBHOOK: PAYMENT_REFUNDED");
    }


    // ==========================================
    // SUBSCRIPTION EVENT TESTS
    // ==========================================

    @Test
    @DisplayName("Should ignore SUBSCRIPTION_DELETED if gateway ID does not match current database subscription")
    void testProcessWebhook_SubscriptionDeleted_Ignored() {
        mockSubscription.setGatewaySubscriptionId("gw-sub-NOVA");
        when(subscriptionRepository.findByBusinessId(BUSINESS_ID)).thenReturn(Optional.of(mockSubscription));

        Map<String, Object> payload = Map.of(
                "event", "SUBSCRIPTION_DELETED",
                "subscription", Map.of(
                        "externalReference", BUSINESS_ID,
                        "id", "gw-sub-ANTIGA"
                )
        );

        webhookService.processWebhook(payload);

        assertThat(mockSubscription.getStatus()).isNotEqualTo(SubscriptionStatus.CANCELED);
        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should cancel subscription directly when SUBSCRIPTION_DELETED is received for correct ID")
    void testProcessWebhook_SubscriptionDeleted_Success() {
        // Arrange
        mockSubscription.setStatus(SubscriptionStatus.ACTIVE);
        mockSubscription.setGatewaySubscriptionId("gw-sub-ATUAL");
        when(subscriptionRepository.findByBusinessId(BUSINESS_ID)).thenReturn(Optional.of(mockSubscription));

        Map<String, Object> payload = Map.of(
                "event", "SUBSCRIPTION_DELETED",
                "subscription", Map.of(
                        "externalReference", BUSINESS_ID,
                        "id", "gw-sub-ATUAL"
                )
        );

        // Act
        webhookService.processWebhook(payload);

        // Assert
        assertThat(mockSubscription.getStatus()).isEqualTo(SubscriptionStatus.CANCELED);
        verify(subscriptionRepository).save(mockSubscription);
        verify(subscriptionHistoryService).logStatusChange(mockSubscription,
                SubscriptionStatus.CANCELED, "WEBHOOK: SUBSCRIPTION_DELETED");
    }


    // ==========================================
    // EDGE CASES
    // ==========================================

    @Test
    @DisplayName("Should return silently if event is null")
    void testProcessWebhook_NullPayload() {
        webhookService.processWebhook(Map.of());
        verify(paymentRepository, never()).existsByGatewayInvoiceId(any());
    }
}