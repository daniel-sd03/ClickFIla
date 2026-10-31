package sodresoftwares.barbearia.services.billing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import sodresoftwares.barbearia.dto.billing.ChangePlanDTO;
import sodresoftwares.barbearia.dto.billing.CheckoutRequestDTO;
import sodresoftwares.barbearia.dto.billing.CreditCardDTO;
import sodresoftwares.barbearia.dto.billing.CreditCardHolderDTO;
import sodresoftwares.barbearia.infra.exception.AppException;
import sodresoftwares.barbearia.model.business.Business;
import sodresoftwares.barbearia.model.billing.*;
import sodresoftwares.barbearia.ports.PaymentGatewayPort;
import sodresoftwares.barbearia.ports.PaymentGatewayPort.GatewayCreditCardResponse;
import sodresoftwares.barbearia.repositories.billing.BusinessPaymentMethodRepository;
import sodresoftwares.barbearia.repositories.billing.PaymentRepository;
import sodresoftwares.barbearia.repositories.billing.PlanRepository;
import sodresoftwares.barbearia.repositories.billing.SubscriptionRepository;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
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

    @Mock
    private BusinessPaymentMethodRepository paymentMethodRepository;

    @InjectMocks
    private SubscriptionService subscriptionService;

    private Business testBusiness;
    private Subscription testSubscription;
    private Plan testPlan;
    private Payment testPayment;
    private CheckoutRequestDTO testCheckoutRequest;

    private final String USER_ID = "user-123";
    private final String BUSINESS_ID = "biz-123";
    private final String SUBSCRIPTION_ID = "sub-123";
    private final String PLAN_CODE = "PRO_MONTHLY";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(subscriptionService, "trialDays", 7);

        testBusiness = Business.builder().id(BUSINESS_ID).name("Barbearia Teste").build();

        testPlan = Plan.builder().id("plan-123").code(PLAN_CODE).name("Plano PRO").build();

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

        testPayment = Payment.builder().id("pay-123").status(PaymentStatus.OVERDUE).invoiceUrl("https://link").build();

        CreditCardDTO ccDto = new CreditCardDTO(
                "DANIEL",
                "4111111111111111",
                "12",
                "2028",
                "123"
        );
        CreditCardHolderDTO holderDto = new CreditCardHolderDTO(
                "Daniel",
                "test@test.com",
                "12345678909",
                "30123456",
                "100",
                null,
                "31999999999",
                null
        );
        testCheckoutRequest = new CheckoutRequestDTO(
                PLAN_CODE,
                PaymentMethod.CREDIT_CARD, // 👈 Alterado para Enum
                ccDto, holderDto);
    }

    // ==================== CREATE TRIAL TESTS ====================

    @Test
    @DisplayName("Should create a TRIAL subscription successfully")
    void testCreateTrialSubscription_Success() {
        subscriptionService.createTrialSubscription(testBusiness);

        LocalDate expectedEndDate = LocalDate.now(ZoneId.of("America/Sao_Paulo")).plusDays(7);

        verify(subscriptionRepository).save(argThat(subscription ->
                subscription.getBusiness().getId().equals(BUSINESS_ID) &&
                        subscription.getStatus() == SubscriptionStatus.TRIAL &&
                        subscription.getCurrentPeriodEnd().equals(expectedEndDate)
        ));
    }

    // ==================== TRANSPARENT CHECKOUT TESTS ====================

    @Test
    @DisplayName("Should process transparent checkout, save card token and update subscription")
    void testProcessTransparentCheckout_Success() {
        testSubscription.setStatus(SubscriptionStatus.TRIAL);
        testSubscription.setGatewaySubscriptionId(null);

        // Arrange
        when(subscriptionRepository.findByUserIdWithBusinessAndPlan(USER_ID)).thenReturn(Optional.of(testSubscription));
        when(planRepository.findByCode(PLAN_CODE)).thenReturn(Optional.of(testPlan));
        when(paymentGatewayPort.createOrUpdateCustomer(testBusiness)).thenReturn("gw-cus-999");

        GatewayCreditCardResponse mockResponse = new GatewayCreditCardResponse("gw-sub-999", "tok_123", "VISA", "1111");
        when(paymentGatewayPort.processCreditCardCheckout(eq(testBusiness), eq(testPlan), eq("gw-cus-999"), any(), any())).thenReturn(mockResponse);

        // Act
        subscriptionService.processTransparentCheckout(USER_ID, testCheckoutRequest);

        // Assert
        verify(paymentMethodRepository).save(argThat(method ->
                method.getCreditCardToken().equals("tok_123") &&
                        method.getCardBrand().equals("VISA") &&
                        method.isDefault()
        ));

        verify(subscriptionRepository).save(argThat(sub ->
                sub.getGatewayCustomerId().equals("gw-cus-999") &&
                        sub.getGatewaySubscriptionId().equals("gw-sub-999")
        ));
    }

    @Test
    @DisplayName("Should reactivate a previously canceled subscription during transparent checkout (Churn Recovery)")
    void testProcessTransparentCheckout_ReactivateCanceledSubscription() {
        // Arrange
        testSubscription.setStatus(SubscriptionStatus.CANCELED);
        testSubscription.setCancelAtPeriodEnd(true);

        when(subscriptionRepository.findByUserIdWithBusinessAndPlan(USER_ID)).thenReturn(Optional.of(testSubscription));
        when(planRepository.findByCode(PLAN_CODE)).thenReturn(Optional.of(testPlan));
        when(paymentGatewayPort.createOrUpdateCustomer(testBusiness)).thenReturn("gw-cus-999");

        GatewayCreditCardResponse mockResponse = new GatewayCreditCardResponse("gw-sub-new123", "tok_new", "VISA", "1234");
        when(paymentGatewayPort.processCreditCardCheckout(eq(testBusiness), eq(testPlan), eq("gw-cus-999"), any(), any())).thenReturn(mockResponse);

        // Act
        subscriptionService.processTransparentCheckout(USER_ID, testCheckoutRequest);

        // Assert
        verify(subscriptionRepository).save(argThat(sub ->
                sub.getGatewaySubscriptionId().equals("gw-sub-new123") &&
                        sub.getStatus() == SubscriptionStatus.PENDING &&
                        sub.getCancelAtPeriodEnd() == false
        ));
    }

    @Test
    @DisplayName("Should throw exception when plan is not found during transparent checkout")
    void testProcessTransparentCheckout_PlanNotFound() {
        testSubscription.setStatus(SubscriptionStatus.TRIAL);

        when(subscriptionRepository.findByUserIdWithBusinessAndPlan(USER_ID)).thenReturn(Optional.of(testSubscription));
        when(planRepository.findByCode(PLAN_CODE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> subscriptionService.processTransparentCheckout(USER_ID, testCheckoutRequest))
                .isInstanceOf(AppException.class)
                .hasMessage("The selected plan is not available.")
                .extracting(e -> ((AppException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND);

        verify(paymentMethodRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw exception when user has no subscription during checkout")
    void testProcessTransparentCheckout_SubscriptionNotFound() {
        when(subscriptionRepository.findByUserIdWithBusinessAndPlan(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> subscriptionService.processTransparentCheckout(USER_ID, testCheckoutRequest))
                .isInstanceOf(AppException.class)
                .hasMessage("No subscription found for this user.")
                .extracting(e -> ((AppException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ==================== UPDATE PAYMENT METHOD TESTS ====================

    @Test
    @DisplayName("Should update credit card successfully by updating existing payment method")
    void testUpdatePaymentMethod_CreditCard_Success() {
        // Arrange
        BusinessPaymentMethod existingCard = BusinessPaymentMethod.builder()
                .id("card-1")
                .business(testBusiness)
                .cardBrand("VISA")
                .lastFourDigits("1111")
                .creditCardToken("tok_old")
                .build();

        when(subscriptionRepository.findByUserIdWithBusinessAndPlan(USER_ID)).thenReturn(Optional.of(testSubscription));
        when(paymentMethodRepository.findByBusinessId(BUSINESS_ID)).thenReturn(Optional.of(existingCard));

        GatewayCreditCardResponse mockResponse = new GatewayCreditCardResponse("gw-sub-123", "tok_new999", "MASTERCARD", "4444");
        when(paymentGatewayPort.updateSubscriptionPaymentMethod(eq("gw-sub-123"), eq(PaymentMethod.CREDIT_CARD), any(), any())).thenReturn(mockResponse);

        // Act
        subscriptionService.updatePaymentMethod(USER_ID, testCheckoutRequest);

        // Assert
        verify(paymentMethodRepository).save(argThat(method ->
                method.getId().equals("card-1") && // Manteve o ID antigo (Update)
                        method.getCreditCardToken().equals("tok_new999") &&
                        method.getCardBrand().equals("MASTERCARD") &&
                        method.getLastFourDigits().equals("4444")
        ));
    }

    @Test
    @DisplayName("Should update payment method to PIX and delete old credit card")
    void testUpdatePaymentMethod_Pix_Success() {
        // Arrange
        CheckoutRequestDTO pixRequest = new CheckoutRequestDTO(PLAN_CODE, PaymentMethod.PIX, null, null);
        BusinessPaymentMethod existingCard = BusinessPaymentMethod.builder().id("card-1").business(testBusiness).build();

        when(subscriptionRepository.findByUserIdWithBusinessAndPlan(USER_ID)).thenReturn(Optional.of(testSubscription));
        when(paymentMethodRepository.findByBusinessId(BUSINESS_ID)).thenReturn(Optional.of(existingCard));

        // Act
        subscriptionService.updatePaymentMethod(USER_ID, pixRequest);

        // Assert
        verify(paymentGatewayPort).updateSubscriptionPaymentMethod(eq("gw-sub-123"), eq(PaymentMethod.PIX), isNull(), isNull());
        verify(paymentMethodRepository).delete(existingCard);
    }

    @Test
    @DisplayName("Should update credit card successfully by creating new payment method if none exists")
    void testUpdatePaymentMethod_NewCard_Success() {
        // Arrange
        when(subscriptionRepository.findByUserIdWithBusinessAndPlan(USER_ID)).thenReturn(Optional.of(testSubscription));
        when(paymentMethodRepository.findByBusinessId(BUSINESS_ID)).thenReturn(Optional.empty());

        GatewayCreditCardResponse mockResponse = new GatewayCreditCardResponse("gw-sub-123", "tok_new999", "MASTERCARD", "4444");
        when(paymentGatewayPort.updateSubscriptionPaymentMethod(eq("gw-sub-123"), eq(PaymentMethod.CREDIT_CARD), any(), any())).thenReturn(mockResponse);

        // Act
        subscriptionService.updatePaymentMethod(USER_ID, testCheckoutRequest);

        // Assert
        verify(paymentMethodRepository).save(argThat(method ->
                method.getId() == null &&
                        method.getCreditCardToken().equals("tok_new999") &&
                        method.getBusiness().getId().equals(BUSINESS_ID)
        ));
    }

    @Test
    @DisplayName("Should throw exception when trying to update method for subscription without gateway link")
    void testUpdatePaymentMethod_NoGatewayId() {
        Subscription invalidSub = Subscription.builder()
                .id("sub-555")
                .business(testBusiness)
                .gatewaySubscriptionId(null)
                .build();
        when(subscriptionRepository.findByUserIdWithBusinessAndPlan(USER_ID)).thenReturn(Optional.of(invalidSub));

        assertThatThrownBy(() -> subscriptionService.updatePaymentMethod(USER_ID, testCheckoutRequest))
                .isInstanceOf(AppException.class)
                .hasMessage("This subscription does not have an active gateway link.")
                .extracting(e -> ((AppException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);

        verify(paymentGatewayPort, never()).updateSubscriptionPaymentMethod(any(), any(), any(), any());
    }

    // ==================== CHANGE PLAN TESTS ====================

    @Test
    @DisplayName("Should change plan successfully using saved credit card token")
    void testChangePlan_Success_SavedToken() {
        // Arrange
        when(subscriptionRepository.findByUserIdWithBusinessAndPlan(USER_ID)).thenReturn(Optional.of(testSubscription));

        Plan newPlan = Plan.builder().id("plan-456").code("PRO_YEARLY").name("Plano Anual").build();
        when(planRepository.findByCode("PRO_YEARLY")).thenReturn(Optional.of(newPlan));

        BusinessPaymentMethod savedMethod = BusinessPaymentMethod.builder()
                .business(testBusiness)
                .creditCardToken("tok_123")
                .build();
        when(paymentMethodRepository.findByBusinessId(BUSINESS_ID)).thenReturn(Optional.of(savedMethod));

        GatewayCreditCardResponse mockResponse = new GatewayCreditCardResponse("gw-sub-new123", "tok_123", "VISA", "1111");

        when(paymentGatewayPort.createScheduledSubscription(
                eq(testBusiness), eq(newPlan), eq("gw-cus-123"), eq(PaymentMethod.CREDIT_CARD), eq("tok_123"), isNull(), isNull(), eq(testSubscription.getCurrentPeriodEnd())
        )).thenReturn(mockResponse);

        ChangePlanDTO request = new ChangePlanDTO("PRO_YEARLY", PaymentMethod.CREDIT_CARD, null, null);

        // Act
        subscriptionService.changePlan(USER_ID, request);

        // Assert
        verify(paymentGatewayPort).cancelSubscription("gw-sub-123");

        verify(subscriptionRepository).save(argThat(sub ->
                sub.getGatewaySubscriptionId().equals("gw-sub-new123")
        ));
    }

    @Test
    @DisplayName("Should throw exception when trying to change to the same plan")
    void testChangePlan_SamePlan() {
        when(subscriptionRepository.findByUserIdWithBusinessAndPlan(USER_ID)).thenReturn(Optional.of(testSubscription));

        ChangePlanDTO request = new ChangePlanDTO(PLAN_CODE, PaymentMethod.CREDIT_CARD, null, null);

        assertThatThrownBy(() -> subscriptionService.changePlan(USER_ID, request))
                .isInstanceOf(AppException.class)
                .hasMessage("You are already subscribed to this plan.")
                .extracting(e -> ((AppException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);

        verify(paymentGatewayPort, never()).cancelSubscription(any());
    }

    @Test
    @DisplayName("Should throw exception when trying to change plan on an inactive subscription")
    void testChangePlan_InactiveSubscription() {
        testSubscription.setStatus(SubscriptionStatus.CANCELED);
        when(subscriptionRepository.findByUserIdWithBusinessAndPlan(USER_ID)).thenReturn(Optional.of(testSubscription));

        ChangePlanDTO request = new ChangePlanDTO("PRO_YEARLY", PaymentMethod.CREDIT_CARD, null, null);

        assertThatThrownBy(() -> subscriptionService.changePlan(USER_ID, request))
                .isInstanceOf(AppException.class)
                .hasMessage("You can only change the plan of an active subscription.")
                .extracting(e -> ((AppException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ==================== CANCEL SUBSCRIPTION TESTS ====================

    @Test
    @DisplayName("Should schedule cancellation at period end and call gateway")
    void testCancelSubscription_Success() {
        when(subscriptionRepository.findByBusinessIdWithBusiness(BUSINESS_ID)).thenReturn(Optional.of(testSubscription));

        subscriptionService.cancelSubscription(BUSINESS_ID);

        assertThat(testSubscription.getCancelAtPeriodEnd()).isTrue();
        verify(paymentGatewayPort).cancelSubscription("gw-sub-123");
        verify(subscriptionRepository).save(testSubscription);
    }
}