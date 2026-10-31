package sodresoftwares.barbearia.infra.gateway;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import sodresoftwares.barbearia.dto.billing.CreditCardDTO;
import sodresoftwares.barbearia.dto.billing.CreditCardHolderDTO;
import sodresoftwares.barbearia.infra.exception.AppException;
import sodresoftwares.barbearia.model.business.Business;
import sodresoftwares.barbearia.model.billing.BillingCycle;
import sodresoftwares.barbearia.model.billing.Plan;
import sodresoftwares.barbearia.model.billing.PaymentMethod; // 👈 IMPORT ADICIONADO
import sodresoftwares.barbearia.model.user.User;
import sodresoftwares.barbearia.ports.PaymentGatewayPort.GatewayCreditCardResponse;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AsaasPaymentAdapter Tests")
class AsaasPaymentAdapterTest {

    private AsaasPaymentAdapter adapter;
    private MockRestServiceServer mockServer;

    private Business testBusiness;
    private Plan testPlan;
    private CreditCardDTO testCreditCard;
    private CreditCardHolderDTO testHolder;

    private final String API_URL = "https://sandbox.asaas.com/api/v3";
    private final String API_KEY = "fake-api-key";

    @BeforeEach
    void setUp() {
        adapter = new AsaasPaymentAdapter(API_URL, API_KEY);

        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        RestClient testClient = builder
                .baseUrl(API_URL)
                .defaultHeader("access_token", API_KEY)
                .build();

        ReflectionTestUtils.setField(adapter, "restClient", testClient);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.0.1");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        User testUser = User.builder().login("barbeiro@teste.com").build();
        testBusiness = Business.builder()
                .id("biz-123")
                .name("Barbearia Teste")
                .user(testUser)
                .cpfCnpj("123.456.789-00")
                .build();

        testPlan = Plan.builder()
                .name("PRO")
                .code("PRO_MONTHLY")
                .price(new BigDecimal("50.00"))
                .billingCycle(BillingCycle.MONTHLY)
                .build();

        testCreditCard = new CreditCardDTO("DANIEL S S PAULA", "4111111111111111", "12", "2028", "123");
        testHolder = new CreditCardHolderDTO("Daniel Sodre", "test@test.com", "12345678909", "30123456", "100", null, "31999999999", null);
    }

    @AfterEach
    void tearDown() {
        mockServer.verify();
        RequestContextHolder.resetRequestAttributes();
    }

    // ==================== CREATE CUSTOMER TESTS ====================

    @Test
    @DisplayName("Should create customer, remove CPF punctuation and return ID successfully")
    void shouldCreateCustomerSuccessfully() {
        String expectedJsonResponse = "{\"id\": \"cus_000001\"}";

        mockServer.expect(requestTo(API_URL + "/customers"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("access_token", API_KEY))
                .andExpect(jsonPath("$.name").value("Barbearia Teste"))
                .andExpect(jsonPath("$.cpfCnpj").value("12345678900"))
                .andRespond(withSuccess(expectedJsonResponse, MediaType.APPLICATION_JSON));

        String customerId = adapter.createOrUpdateCustomer(testBusiness);

        assertThat(customerId).isEqualTo("cus_000001");
    }

    @Test
    @DisplayName("Should throw Exception when business has no CPF or CNPJ")
    void shouldThrowWhenCpfIsMissing() {
        Business businessWithoutCpf = Business.builder().id("biz-456").name("Barbearia Sem Documento").cpfCnpj(null).build();
        mockServer.reset();

        assertThatThrownBy(() -> adapter.createOrUpdateCustomer(businessWithoutCpf))
                .isInstanceOf(AppException.class)
                .hasMessage("CPF or CNPJ is required to process the payment.")
                .extracting(e -> ((AppException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ==================== TRANSPARENT CHECKOUT TESTS ====================

    @Test
    @DisplayName("Should process credit card checkout and return tokenized data successfully")
    void shouldProcessCreditCardCheckout() {
        String expectedJsonResponse = """
                {
                    "id": "sub_123",
                    "creditCard": {
                        "creditCardNumber": "1111",
                        "creditCardBrand": "VISA",
                        "creditCardToken": "tok_abc123"
                    }
                }
                """;

        mockServer.expect(requestTo(API_URL + "/subscriptions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.billingType").value("CREDIT_CARD"))
                .andExpect(jsonPath("$.customer").value("cus_123"))
                .andExpect(jsonPath("$.cycle").value("MONTHLY"))
                .andExpect(jsonPath("$.remoteIp").value("192.168.0.1"))
                .andExpect(jsonPath("$.creditCard.holderName").value("DANIEL S S PAULA"))
                .andExpect(jsonPath("$.creditCardHolderInfo.cpfCnpj").value("12345678909"))
                .andRespond(withSuccess(expectedJsonResponse, MediaType.APPLICATION_JSON));

        GatewayCreditCardResponse response = adapter.processCreditCardCheckout(testBusiness, testPlan, "cus_123", testCreditCard, testHolder);

        assertThat(response).isNotNull();
        assertThat(response.gatewaySubscriptionId()).isEqualTo("sub_123");
        assertThat(response.creditCardToken()).isEqualTo("tok_abc123");
        assertThat(response.cardBrand()).isEqualTo("VISA");
        assertThat(response.lastFourDigits()).isEqualTo("1111");
    }

    @Test
    @DisplayName("Should throw BAD_REQUEST when credit card is rejected by gateway")
    void shouldThrowWhenCardIsRejected() {
        mockServer.expect(requestTo(API_URL + "/subscriptions"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> adapter.processCreditCardCheckout(testBusiness, testPlan, "cus_123", testCreditCard, testHolder))
                .isInstanceOf(AppException.class)
                .hasMessage("The card was declined or the provided data is invalid. Please check the information and try again.")
                .extracting(e -> ((AppException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ==================== UPDATE SUBSCRIPTION TESTS ====================

    @Test
    @DisplayName("Should update credit card and return tokenized data successfully")
    void shouldUpdateCreditCardSuccessfully() {
        String expectedJsonResponse = """
                {
                    "id": "sub_123",
                    "creditCard": {
                        "creditCardNumber": "4444",
                        "creditCardBrand": "MASTERCARD",
                        "creditCardToken": "tok_new123"
                    }
                }
                """;

        String gatewaySubscriptionId = "sub_123";

        mockServer.expect(requestTo(API_URL + "/subscriptions/" + gatewaySubscriptionId))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(jsonPath("$.billingType").value("CREDIT_CARD"))
                .andExpect(jsonPath("$.creditCard.holderName").value("DANIEL S S PAULA"))
                .andRespond(withSuccess(expectedJsonResponse, MediaType.APPLICATION_JSON));

        GatewayCreditCardResponse response = adapter.updateSubscriptionPaymentMethod(
                gatewaySubscriptionId, PaymentMethod.CREDIT_CARD, testCreditCard, testHolder
        );

        assertThat(response).isNotNull();
        assertThat(response.gatewaySubscriptionId()).isEqualTo("sub_123");
        assertThat(response.creditCardToken()).isEqualTo("tok_new123");
        assertThat(response.cardBrand()).isEqualTo("MASTERCARD");
        assertThat(response.lastFourDigits()).isEqualTo("4444");
    }

    // ==================== SCHEDULED SUBSCRIPTION TESTS ====================

    @Test
    @DisplayName("Should create scheduled subscription successfully using existing token")
    void shouldCreateScheduledSubscription() {
        String expectedJsonResponse = """
                {
                    "id": "sub_scheduled_123",
                    "creditCard": {
                        "creditCardNumber": "4444",
                        "creditCardBrand": "MASTERCARD",
                        "creditCardToken": "tok_existing123"
                    }
                }
                """;

        LocalDate nextDueDate = LocalDate.now().plusDays(10);

        mockServer.expect(requestTo(API_URL + "/subscriptions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.billingType").value("CREDIT_CARD"))
                .andExpect(jsonPath("$.nextDueDate").value(nextDueDate.toString()))
                .andExpect(jsonPath("$.creditCardToken").value("tok_existing123"))
                .andExpect(jsonPath("$.cycle").value("MONTHLY"))
                .andRespond(withSuccess(expectedJsonResponse, MediaType.APPLICATION_JSON));

        GatewayCreditCardResponse response = adapter.createScheduledSubscription(
                testBusiness, testPlan, "cus_123", PaymentMethod.CREDIT_CARD, "tok_existing123", null, null, nextDueDate
        );

        assertThat(response).isNotNull();
        assertThat(response.gatewaySubscriptionId()).isEqualTo("sub_scheduled_123");
        assertThat(response.creditCardToken()).isEqualTo("tok_existing123");
    }

    // ==================== PIX CHECKOUT TESTS ====================

    @Test
    @DisplayName("Should process PIX checkout and return QR Code data successfully")
    void shouldProcessPixCheckoutSuccessfully() {
        String subscriptionJsonResponse = """
                {
                    "id": "sub_pix123"
                }
                """;

        String paymentsJsonResponse = """
                {
                    "data": [
                        { "id": "pay_pix123" }
                    ]
                }
                """;

        String qrCodeJsonResponse = """
                {
                    "encodedImage": "base64_image_data",
                    "payload": "pix_copy_paste_data"
                }
                """;

        mockServer.expect(requestTo(API_URL + "/subscriptions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.billingType").value("PIX"))
                .andExpect(jsonPath("$.customer").value("cus_123"))
                .andRespond(withSuccess(subscriptionJsonResponse, MediaType.APPLICATION_JSON));

        mockServer.expect(requestTo(API_URL + "/payments?subscription=sub_pix123"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(paymentsJsonResponse, MediaType.APPLICATION_JSON));

        mockServer.expect(requestTo(API_URL + "/payments/pay_pix123/pixQrCode"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(qrCodeJsonResponse, MediaType.APPLICATION_JSON));

        sodresoftwares.barbearia.dto.billing.CheckoutResponseDTO response =
                adapter.processPixCheckout(testBusiness, testPlan, "cus_123");

        assertThat(response).isNotNull();
        assertThat(response.gatewaySubscriptionId()).isEqualTo("sub_pix123");
        assertThat(response.pixQrCodeBase64()).isEqualTo("base64_image_data");
        assertThat(response.pixCopiaECola()).isEqualTo("pix_copy_paste_data");
    }

    // ==================== CANCEL SUBSCRIPTION TESTS ====================

    @Test
    @DisplayName("Should send DELETE request to cancel subscription using new endpoint")
    void shouldCancelSubscription() {
        String gatewaySubscriptionId = "sub_999";

        mockServer.expect(requestTo(API_URL + "/subscriptions/" + gatewaySubscriptionId))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withNoContent());

        adapter.cancelSubscription(gatewaySubscriptionId);
    }
}