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
import sodresoftwares.barbearia.dto.billing.*;
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
import java.util.List;
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
                PaymentMethod.CREDIT_CARD,
                null,
                ccDto,
                holderDto
        );
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
    @DisplayName("Should process IMMEDIATE CC checkout when no valid days remain")
    void testProcessTransparentCheckout_ImmediateCC_Success() {
        testSubscription.setStatus(SubscriptionStatus.TRIAL);
        testSubscription.setGatewaySubscriptionId(null);
        testSubscription.setCurrentPeriodEnd(LocalDate.now().minusDays(1));

        when(subscriptionRepository.findByUserIdWithBusinessAndPlan(USER_ID)).thenReturn(Optional.of(testSubscription));
        when(planRepository.findByCode(PLAN_CODE)).thenReturn(Optional.of(testPlan));
        when(paymentGatewayPort.createOrUpdateCustomer(testBusiness)).thenReturn("gw-cus-999");
        GatewayCreditCardResponse tokenResponse = new GatewayCreditCardResponse(null,
                "tok_123", "VISA", "1111");
        when(paymentGatewayPort.tokenizeCreditCard(eq("gw-cus-999"), any(), any())).thenReturn(tokenResponse);

        GatewayCreditCardResponse mockResponse = new GatewayCreditCardResponse("gw-sub-999",
                "tok_123", "VISA", "1111");
        when(paymentGatewayPort.processCreditCardCheckout(eq(testBusiness), eq(testPlan), eq("gw-cus-999"),
                eq("tok_123"), isNull(), isNull())).thenReturn(mockResponse);

        subscriptionService.processTransparentCheckout(USER_ID, testCheckoutRequest);

        verify(paymentMethodRepository).save(argThat(method ->
                method.getCreditCardToken().equals("tok_123") &&
                        method.getCardBrand().equals("VISA")
        ));

        verify(subscriptionRepository).save(argThat(sub ->
                sub.getGatewaySubscriptionId().equals("gw-sub-999") &&
                        sub.getStatus() == SubscriptionStatus.PENDING
        ));
    }

    @Test
    @DisplayName("Should process SCHEDULED CC reactivation when valid days remain")
    void testProcessTransparentCheckout_ScheduledCC_Success() {
        testSubscription.setStatus(SubscriptionStatus.CANCELED);
        testSubscription.setCancelAtPeriodEnd(true);

        when(subscriptionRepository.findByUserIdWithBusinessAndPlan(USER_ID)).thenReturn(Optional.of(testSubscription));
        when(planRepository.findByCode(PLAN_CODE)).thenReturn(Optional.of(testPlan));
        when(paymentGatewayPort.createOrUpdateCustomer(testBusiness)).thenReturn("gw-cus-999");
        GatewayCreditCardResponse tokenResponse = new GatewayCreditCardResponse(null,
                "tok_new", "VISA", "1234");
        when(paymentGatewayPort.tokenizeCreditCard(eq("gw-cus-999"), any(), any())).thenReturn(tokenResponse);
        GatewayCreditCardResponse mockResponse = new GatewayCreditCardResponse("gw-sub-new123",
                "tok_new", "VISA", "1234");
        when(paymentGatewayPort.createScheduledSubscription(any(), any(), any(), eq(PaymentMethod.CREDIT_CARD),
                eq("tok_new"), isNull(), isNull(), eq(testSubscription.getCurrentPeriodEnd()))).thenReturn(mockResponse);

        subscriptionService.processTransparentCheckout(USER_ID, testCheckoutRequest);

        verify(subscriptionRepository).save(argThat(sub ->
                sub.getGatewaySubscriptionId().equals("gw-sub-new123") &&
                        sub.getStatus() == SubscriptionStatus.ACTIVE &&
                        !sub.getCancelAtPeriodEnd()
        ));
    }

    @Test
    @DisplayName("Should process IMMEDIATE PIX checkout when no valid days remain")
    void testProcessTransparentCheckout_ImmediatePix_Success() {
        testSubscription.setStatus(SubscriptionStatus.TRIAL);
        testSubscription.setGatewaySubscriptionId(null);
        testSubscription.setCurrentPeriodEnd(LocalDate.now().minusDays(1));

        CheckoutRequestDTO pixRequest = new CheckoutRequestDTO(PLAN_CODE, PaymentMethod.PIX,
                null, null, null);

        when(subscriptionRepository.findByUserIdWithBusinessAndPlan(USER_ID)).thenReturn(Optional.of(testSubscription));
        when(planRepository.findByCode(PLAN_CODE)).thenReturn(Optional.of(testPlan));
        when(paymentGatewayPort.createOrUpdateCustomer(testBusiness)).thenReturn("gw-cus-999");

        CheckoutResponseDTO mockPixResponse = new CheckoutResponseDTO("gw-sub-pix123",
                "base64", "copiacola");
        when(paymentGatewayPort.processPixCheckout(testBusiness, testPlan, "gw-cus-999")).thenReturn(mockPixResponse);

        CheckoutResponseDTO result = subscriptionService.processTransparentCheckout(USER_ID, pixRequest);

        assertThat(result.pixCopiaECola()).isEqualTo("copiacola");
        verify(subscriptionRepository).save(argThat(sub ->
                sub.getGatewaySubscriptionId().equals("gw-sub-pix123") &&
                        sub.getStatus() == SubscriptionStatus.PENDING
        ));
    }

    @Test
    @DisplayName("Should process SCHEDULED PIX reactivation when valid days remain")
    void testProcessTransparentCheckout_ScheduledPix_Success() {
        testSubscription.setStatus(SubscriptionStatus.CANCELED);
        testSubscription.setCancelAtPeriodEnd(true);

        CheckoutRequestDTO pixRequest = new CheckoutRequestDTO(PLAN_CODE, PaymentMethod.PIX,
                null, null, null);

        when(subscriptionRepository.findByUserIdWithBusinessAndPlan(USER_ID)).thenReturn(Optional.of(testSubscription));
        when(planRepository.findByCode(PLAN_CODE)).thenReturn(Optional.of(testPlan));
        when(paymentGatewayPort.createOrUpdateCustomer(testBusiness)).thenReturn("gw-cus-999");

        GatewayCreditCardResponse mockResponse = new GatewayCreditCardResponse("gw-sub-sch-pix",
                null, null, null);
        when(paymentGatewayPort.createScheduledSubscription(any(), any(), any(), eq(PaymentMethod.PIX), isNull(),
                isNull(), isNull(), eq(testSubscription.getCurrentPeriodEnd()))).thenReturn(mockResponse);

        CheckoutResponseDTO result = subscriptionService.processTransparentCheckout(USER_ID, pixRequest);

        assertThat(result.pixCopiaECola()).isNull();
        verify(subscriptionRepository).save(argThat(sub ->
                sub.getGatewaySubscriptionId().equals("gw-sub-sch-pix") &&
                        sub.getStatus() == SubscriptionStatus.ACTIVE &&
                        !sub.getCancelAtPeriodEnd()
        ));
    }

    @Test
    @DisplayName("Should throw exception when plan is not found during transparent checkout")
    void testProcessTransparentCheckout_PlanNotFound() {
        testSubscription.setStatus(SubscriptionStatus.TRIAL);
        testSubscription.setGatewaySubscriptionId(null);

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

    @Test
    @DisplayName("Should throw exception when trying to checkout with an ongoing subscription")
    void testProcessTransparentCheckout_AlreadyExists() {
        // testSubscription is ACTIVE with gatewayId in setUp()
        when(subscriptionRepository.findByUserIdWithBusinessAndPlan(USER_ID)).thenReturn(Optional.of(testSubscription));

        assertThatThrownBy(() -> subscriptionService.processTransparentCheckout(USER_ID, testCheckoutRequest))
                .isInstanceOf(AppException.class)
                .hasMessage("You already have an ongoing or active subscription. Use the payment method update endpoint.")
                .extracting(e -> ((AppException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);

        verify(paymentGatewayPort, never()).processCreditCardCheckout(any(), any(), any(), any(), any(), any());
    }

    // ==================== UPDATE PAYMENT METHOD TESTS ====================

    @Test
    @DisplayName("Should update credit card successfully")
    void testUpdatePaymentMethod_CreditCard_Success() {
        BusinessPaymentMethod existingCard = BusinessPaymentMethod.builder().id("card-1").business(testBusiness).build();

        when(subscriptionRepository.findByUserIdWithBusinessAndPlan(USER_ID)).thenReturn(Optional.of(testSubscription));
        when(paymentMethodRepository.findByBusinessId(BUSINESS_ID)).thenReturn(Optional.of(existingCard));

        GatewayCreditCardResponse tokenResponse = new GatewayCreditCardResponse(null,
                "tok_new999", "MASTERCARD", "4444");
        when(paymentGatewayPort.tokenizeCreditCard(eq("gw-cus-123"), any(), any())).thenReturn(tokenResponse);

        GatewayCreditCardResponse mockResponse = new GatewayCreditCardResponse("gw-sub-123",
                "tok_new", "VISA", "1111");
        when(paymentGatewayPort.updateSubscriptionPaymentMethod(any(), any(), any(), any(), any())).thenReturn(mockResponse);

        subscriptionService.updatePaymentMethod(USER_ID, testCheckoutRequest);

        verify(paymentMethodRepository).save(any());
    }

    @Test
    @DisplayName("Should update payment method to PIX, delete old card and fetch new QR Code when PENDING")
    void testUpdatePaymentMethod_Pix_Pending_Success() {
        CheckoutRequestDTO pixRequest = new CheckoutRequestDTO(PLAN_CODE, PaymentMethod.PIX,
                null, null, null);
        BusinessPaymentMethod existingCard = BusinessPaymentMethod.builder().id("card-1").business(testBusiness).build();

        testSubscription.setStatus(SubscriptionStatus.PENDING);

        when(subscriptionRepository.findByUserIdWithBusinessAndPlan(USER_ID)).thenReturn(Optional.of(testSubscription));
        when(paymentMethodRepository.findByBusinessId(BUSINESS_ID)).thenReturn(Optional.of(existingCard));

        CheckoutResponseDTO mockPixResponse = new CheckoutResponseDTO("gw-sub-123",
                "base64", "copiacola");
        when(paymentGatewayPort.getPendingPixQrCode("gw-sub-123")).thenReturn(mockPixResponse);

        CheckoutResponseDTO result = subscriptionService.updatePaymentMethod(USER_ID, pixRequest);

        verify(paymentGatewayPort).updateSubscriptionPaymentMethod(eq("gw-sub-123"), eq(PaymentMethod.PIX),
                isNull(), isNull(), isNull());
        verify(paymentMethodRepository).delete(existingCard);
        assertThat(result.pixCopiaECola()).isEqualTo("copiacola");
    }

    @Test
    @DisplayName("Should update payment method to PIX and NOT fetch QR Code when ACTIVE")
    void testUpdatePaymentMethod_Pix_Active_Success() {
        CheckoutRequestDTO pixRequest = new CheckoutRequestDTO(PLAN_CODE, PaymentMethod.PIX,
                null, null, null);
        BusinessPaymentMethod existingCard = BusinessPaymentMethod.builder().id("card-1").business(testBusiness).build();

        when(subscriptionRepository.findByUserIdWithBusinessAndPlan(USER_ID)).thenReturn(Optional.of(testSubscription));
        when(paymentMethodRepository.findByBusinessId(BUSINESS_ID)).thenReturn(Optional.of(existingCard));

        CheckoutResponseDTO result = subscriptionService.updatePaymentMethod(USER_ID, pixRequest);

        verify(paymentGatewayPort).updateSubscriptionPaymentMethod(eq("gw-sub-123"), eq(PaymentMethod.PIX),
                isNull(), isNull(), isNull());
        verify(paymentMethodRepository).delete(existingCard);

        verify(paymentGatewayPort, never()).getPendingPixQrCode(anyString());
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("Should update credit card successfully by creating new payment method if none exists")
    void testUpdatePaymentMethod_NewCard_Success() {
        // Arrange
        when(subscriptionRepository.findByUserIdWithBusinessAndPlan(USER_ID)).thenReturn(Optional.of(testSubscription));
        when(paymentMethodRepository.findByBusinessId(BUSINESS_ID)).thenReturn(Optional.empty());
        GatewayCreditCardResponse tokenResponse = new GatewayCreditCardResponse(null,
                "tok_new999", "MASTERCARD", "4444");
        when(paymentGatewayPort.tokenizeCreditCard(eq("gw-cus-123"), any(), any())).thenReturn(tokenResponse);

        GatewayCreditCardResponse mockResponse = new GatewayCreditCardResponse("gw-sub-123",
                "tok_new999", "MASTERCARD", "4444");
        when(paymentGatewayPort.updateSubscriptionPaymentMethod(eq("gw-sub-123"), eq(PaymentMethod.CREDIT_CARD),
                eq("tok_new999"), isNull(), isNull())).thenReturn(mockResponse);

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

        verify(paymentGatewayPort, never()).updateSubscriptionPaymentMethod(any(), any(), any(), any(), any());
    }

    // ==================== CHANGE PLAN TESTS ====================

    @Test
    @DisplayName("Should change plan successfully tokenizing a new credit card")
    void testChangePlan_Success_NewCard() {
        // Arrange
        when(subscriptionRepository.findByUserIdWithBusinessAndPlan(USER_ID)).thenReturn(Optional.of(testSubscription));
        Plan newPlan = Plan.builder().id("plan-456").code("PRO_YEARLY").name("Plano Anual").build();
        when(planRepository.findByCode("PRO_YEARLY")).thenReturn(Optional.of(newPlan));
        when(paymentMethodRepository.findByBusinessId(BUSINESS_ID)).thenReturn(Optional.empty());
        GatewayCreditCardResponse tokenResponse = new GatewayCreditCardResponse(null,
                "tok_777", "AMEX", "8888");
        when(paymentGatewayPort.tokenizeCreditCard(eq("gw-cus-123"), any(), any())).thenReturn(tokenResponse);

        GatewayCreditCardResponse mockResponse = new GatewayCreditCardResponse("gw-sub-new123",
                "tok_777", "AMEX", "8888");

        when(paymentGatewayPort.createScheduledSubscription(
                eq(testBusiness), eq(newPlan), eq("gw-cus-123"), eq(PaymentMethod.CREDIT_CARD), eq("tok_777"),
                isNull(), isNull(), eq(testSubscription.getCurrentPeriodEnd())
        )).thenReturn(mockResponse);
        ChangePlanDTO request = new ChangePlanDTO("PRO_YEARLY", PaymentMethod.CREDIT_CARD, null,
                testCheckoutRequest.creditCard(), testCheckoutRequest.creditCardHolderInfo());

        // Act
        subscriptionService.changePlan(USER_ID, request);

        // Assert
        verify(paymentGatewayPort).cancelSubscription("gw-sub-123");
        verify(paymentMethodRepository).save(argThat(method ->
                method.getCreditCardToken().equals("tok_777")
        ));
    }

    // ==================== PENDING PIX QR CODE TESTS ====================

    @Test
    @DisplayName("Should get pending PIX QR Code from local payment repository")
    void testGetPendingPixQrCode_Found() {
        when(subscriptionRepository.findByUserIdWithBusinessAndPlan(USER_ID)).thenReturn(Optional.of(testSubscription));

        Payment pendingPayment = Payment.builder()
                .pixQrCodeBase64("base64_cached")
                .pixCopyPaste("copiacola_cached")
                .build();

        when(paymentRepository.findFirstBySubscriptionIdAndStatusOrderByIdDesc(testSubscription.getId(), PaymentStatus.PENDING))
                .thenReturn(Optional.of(pendingPayment));

        CheckoutResponseDTO result = subscriptionService.getPendingPixQrCode(USER_ID);

        assertThat(result).isNotNull();
        assertThat(result.pixCopiaECola()).isEqualTo("copiacola_cached");
        assertThat(result.gatewaySubscriptionId()).isEqualTo("gw-sub-123");
    }

    @Test
    @DisplayName("Should return null if no pending PIX is found in local cache")
    void testGetPendingPixQrCode_NotFound() {
        when(subscriptionRepository.findByUserIdWithBusinessAndPlan(USER_ID)).thenReturn(Optional.of(testSubscription));

        when(paymentRepository.findFirstBySubscriptionIdAndStatusOrderByIdDesc(testSubscription.getId(), PaymentStatus.PENDING))
                .thenReturn(Optional.empty());

        CheckoutResponseDTO result = subscriptionService.getPendingPixQrCode(USER_ID);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("Should throw exception when trying to change to the same plan")
    void testChangePlan_SamePlan() {
        when(subscriptionRepository.findByUserIdWithBusinessAndPlan(USER_ID)).thenReturn(Optional.of(testSubscription));

        ChangePlanDTO request = new ChangePlanDTO(PLAN_CODE, PaymentMethod.CREDIT_CARD,
                null, null, null);

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

        ChangePlanDTO request = new ChangePlanDTO("PRO_YEARLY", PaymentMethod.CREDIT_CARD,
                null, null, null);

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

    // ==================== GET MY SUBSCRIPTION DETAILS TESTS ====================

    @Test
    @DisplayName("Should get subscription details and calculate pending payments with date threshold")
    void testGetMySubscriptionDetails_Success() {
        // Arrange
        testSubscription.setPaymentMethod(PaymentMethod.CREDIT_CARD);
        when(subscriptionRepository.findByUserIdWithBusinessAndPlan(USER_ID)).thenReturn(Optional.of(testSubscription));

        BusinessPaymentMethod existingCard = BusinessPaymentMethod.builder()
                .cardBrand("MASTERCARD")
                .lastFourDigits("9999")
                .build();
        when(paymentMethodRepository.findByBusinessId(BUSINESS_ID)).thenReturn(Optional.of(existingCard));

        when(paymentRepository.existsBySubscriptionIdAndStatusInAndDueDateBeforeOrEqual(
                eq(testSubscription.getId()),
                eq(List.of(PaymentStatus.PENDING, PaymentStatus.OVERDUE)),
                any(LocalDate.class)
        )).thenReturn(true);

        // Act
        SubscriptionResponseDTO result = subscriptionService.getMySubscriptionDetails(USER_ID);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(result.plan().code()).isEqualTo(PLAN_CODE);
        assertThat(result.creditCard().brand()).isEqualTo("MASTERCARD");
        assertThat(result.creditCard().lastFourDigits()).isEqualTo("9999");
        assertThat(result.hasPendingPayment()).isTrue();

        assertThat(result.activePaymentMethod()).isEqualTo(PaymentMethod.CREDIT_CARD);

        verify(paymentRepository).existsBySubscriptionIdAndStatusInAndDueDateBeforeOrEqual(
                eq(testSubscription.getId()),
                anyList(),
                any(LocalDate.class)
        );
    }
}