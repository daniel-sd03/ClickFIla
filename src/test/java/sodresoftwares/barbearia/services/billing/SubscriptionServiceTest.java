package sodresoftwares.barbearia.services.billing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import sodresoftwares.barbearia.dto.billing.SubscriptionResponseDTO;
import sodresoftwares.barbearia.infra.exception.AppException;
import sodresoftwares.barbearia.model.business.Business;
import sodresoftwares.barbearia.model.billing.*;
import sodresoftwares.barbearia.ports.PaymentGatewayPort;
import sodresoftwares.barbearia.repositories.billing.PaymentRepository;
import sodresoftwares.barbearia.repositories.billing.PlanRepository;
import sodresoftwares.barbearia.repositories.billing.SubscriptionRepository;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SubscriptionService Tests")
class SubscriptionServiceTest {

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private PlanRepository planRepository;

    @Mock
    private PaymentGatewayPort paymentGatewayPort;

    @Mock
    private PaymentRepository paymentRepository;

    @InjectMocks
    private SubscriptionService subscriptionService;

    private Business testBusiness;
    private Subscription testSubscription;
    private Plan testPlan;
    private Payment testPayment;

    private final String USER_ID = "user-123";
    private final String BUSINESS_ID = "biz-123";
    private final String SUBSCRIPTION_ID = "sub-123";
    private final String PLAN_CODE = "PRO_MONTHLY";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(subscriptionService, "trialDays", 7);

        testBusiness = Business.builder()
                .id(BUSINESS_ID)
                .name("Barbearia Teste")
                .build();

        testPlan = Plan.builder()
                .id("plan-123")
                .code(PLAN_CODE)
                .name("Plano PRO")
                .build();

        testSubscription = Subscription.builder()
                .id(SUBSCRIPTION_ID)
                .business(testBusiness)
                .plan(testPlan)
                .status(SubscriptionStatus.ACTIVE)
                .gatewaySubscriptionId("gw-sub-123")
                .gatewayCustomerId("gw-cus-123")
                .currentPeriodEnd(LocalDate.now().plusDays(15))
                .cancelAtPeriodEnd(false)
                .build();

        testPayment = Payment.builder()
                .id("pay-123")
                .status(PaymentStatus.OVERDUE)
                .invoiceUrl("https://sandbox.asaas.com/i/123456")
                .build();
    }

    // ==================== CREATE TRIAL TESTS ====================

    @Test
    @DisplayName("Should create a TRIAL subscription successfully")
    void testCreateTrialSubscription_Success() {
        // Act
        subscriptionService.createTrialSubscription(testBusiness);

        // Assert
        LocalDate expectedEndDate = LocalDate.now().plusDays(7);

        verify(subscriptionRepository).save(argThat(subscription ->
                subscription.getBusiness().getId().equals(BUSINESS_ID) &&
                        subscription.getStatus() == SubscriptionStatus.TRIAL &&
                        subscription.getPaymentProvider() == PaymentProvider.ASAAS &&
                        subscription.getCurrentPeriodEnd().equals(expectedEndDate) &&
                        Boolean.FALSE.equals(subscription.getCancelAtPeriodEnd())
        ));
    }

    // ==================== GENERATE CHECKOUT TESTS ====================

    @Test
    @DisplayName("Should generate checkout URL successfully")
    void testGenerateCheckout_Success() {
        // Arrange
        when(subscriptionRepository.findByBusinessIdWithBusiness(BUSINESS_ID))
                .thenReturn(Optional.of(testSubscription));
        when(planRepository.findByCode(PLAN_CODE)).thenReturn(Optional.of(testPlan));

        when(paymentGatewayPort.createOrUpdateCustomer(testBusiness)).thenReturn("gw-cus-999");

        PaymentGatewayPort.GatewayCheckoutResponse mockResponse =
                new PaymentGatewayPort.GatewayCheckoutResponse("gw-sub-999", "https://checkout.url");

        when(paymentGatewayPort.createSubscriptionCheckout(testBusiness, testPlan, "gw-cus-999")).thenReturn(mockResponse);

        // Act
        String checkoutUrl = subscriptionService.generateCheckout(BUSINESS_ID, PLAN_CODE);

        // Assert
        assertThat(checkoutUrl).isEqualTo("https://checkout.url");

        verify(subscriptionRepository).save(argThat(sub ->
                sub.getGatewayCustomerId().equals("gw-cus-999") &&
                        sub.getGatewaySubscriptionId().equals("gw-sub-999") &&
                        sub.getPlan().getCode().equals(PLAN_CODE)
        ));
    }

    @Test
    @DisplayName("Should throw exception when plan is not found during checkout")
    void testGenerateCheckout_PlanNotFound() {
        // Arrange
        when(subscriptionRepository.findByBusinessIdWithBusiness(BUSINESS_ID)).thenReturn(Optional.of(testSubscription));
        when(planRepository.findByCode(PLAN_CODE)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> subscriptionService.generateCheckout(BUSINESS_ID, PLAN_CODE))
                .isInstanceOf(AppException.class)
                .hasMessage("The selected plan is not available.")
                .extracting(e -> ((AppException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND);

        verify(paymentGatewayPort, never()).createSubscriptionCheckout(any(), any(), anyString());
        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw exception when subscription is not found during checkout")
    void testGenerateCheckout_SubscriptionNotFound() {
        // Arrange
        when(subscriptionRepository.findByBusinessIdWithBusiness(BUSINESS_ID)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> subscriptionService.generateCheckout(BUSINESS_ID, PLAN_CODE))
                .isInstanceOf(AppException.class)
                .hasMessage("No subscription found for this business.")
                .extracting(e -> ((AppException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ==================== CANCEL SUBSCRIPTION TESTS ====================

    @Test
    @DisplayName("Should schedule cancellation at period end and call gateway")
    void testCancelSubscription_Success() {
        // Arrange
        when(subscriptionRepository.findByBusinessIdWithBusiness(BUSINESS_ID)).thenReturn(Optional.of(testSubscription));

        // Act
        subscriptionService.cancelSubscription(BUSINESS_ID);

        // Assert
        assertThat(testSubscription.getCancelAtPeriodEnd()).isTrue();

        verify(paymentGatewayPort).cancelSubscription("gw-sub-123");
        verify(subscriptionRepository).save(testSubscription);
    }

    @Test
    @DisplayName("Should NOT call gateway if gatewaySubscriptionId is null")
    void testCancelSubscription_NullGatewayId() {
        // Arrange
        testSubscription.setGatewaySubscriptionId(null);
        when(subscriptionRepository.findByBusinessIdWithBusiness(BUSINESS_ID)).thenReturn(Optional.of(testSubscription));

        // Act
        subscriptionService.cancelSubscription(BUSINESS_ID);

        // Assert
        assertThat(testSubscription.getCancelAtPeriodEnd()).isTrue();

        verify(paymentGatewayPort, never()).cancelSubscription(anyString());
        verify(subscriptionRepository).save(testSubscription);
    }

    @Test
    @DisplayName("Should throw exception if subscription is already canceled")
    void testCancelSubscription_AlreadyCanceled() {
        // Arrange
        testSubscription.setStatus(SubscriptionStatus.CANCELED);
        when(subscriptionRepository.findByBusinessIdWithBusiness(BUSINESS_ID)).thenReturn(Optional.of(testSubscription));

        // Act & Assert
        assertThatThrownBy(() -> subscriptionService.cancelSubscription(BUSINESS_ID))
                .isInstanceOf(AppException.class)
                .hasMessage("This subscription is already canceled.")
                .extracting(e -> ((AppException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);

        verify(paymentGatewayPort, never()).cancelSubscription(anyString());
        verify(subscriptionRepository, never()).save(any());
    }

    // ==================== GET MY SUBSCRIPTION DETAILS TESTS ====================

    @Test
    @DisplayName("Should return subscription details without payment link if ACTIVE")
    void testGetMySubscriptionDetails_ActiveSuccess() {
        // Arrange
        when(subscriptionRepository.findByUserIdWithBusinessAndPlan(USER_ID)).thenReturn(Optional.of(testSubscription));

        // Act
        SubscriptionResponseDTO result = subscriptionService.getMySubscriptionDetails(USER_ID);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(result.planName()).isEqualTo("Plano PRO");
        assertThat(result.paymentLink()).isNull();

        verify(paymentRepository, never()).findLatestPendingOrOverduePayment(anyString());
    }

    @Test
    @DisplayName("Should return 'Trial' as plan name if plan is null")
    void testGetMySubscriptionDetails_TrialPlanNull() {
        // Arrange
        testSubscription.setPlan(null);
        testSubscription.setStatus(SubscriptionStatus.TRIAL);
        when(subscriptionRepository.findByUserIdWithBusinessAndPlan(USER_ID)).thenReturn(Optional.of(testSubscription));

        // Act
        SubscriptionResponseDTO result = subscriptionService.getMySubscriptionDetails(USER_ID);

        // Assert
        assertThat(result.planName()).isEqualTo("Trial");
    }

    @Test
    @DisplayName("Should return payment link if subscription is SUSPENDED and has overdue payment")
    void testGetMySubscriptionDetails_SuspendedWithInvoice() {
        // Arrange
        testSubscription.setStatus(SubscriptionStatus.SUSPENDED);
        when(subscriptionRepository.findByUserIdWithBusinessAndPlan(USER_ID)).thenReturn(Optional.of(testSubscription));
        when(paymentRepository.findLatestPendingOrOverduePayment(SUBSCRIPTION_ID)).thenReturn(Optional.of(testPayment));

        // Act
        SubscriptionResponseDTO result = subscriptionService.getMySubscriptionDetails(USER_ID);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(SubscriptionStatus.SUSPENDED);
        assertThat(result.paymentLink()).isEqualTo("https://sandbox.asaas.com/i/123456");
    }
}