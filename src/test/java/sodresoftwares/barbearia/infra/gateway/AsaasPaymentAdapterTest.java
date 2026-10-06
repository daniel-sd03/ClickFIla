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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import sodresoftwares.barbearia.infra.exception.AppException;
import sodresoftwares.barbearia.model.business.Business;
import sodresoftwares.barbearia.model.billing.BillingCycle;
import sodresoftwares.barbearia.model.billing.Plan;
import sodresoftwares.barbearia.model.user.User;
import sodresoftwares.barbearia.ports.PaymentGatewayPort.GatewayCheckoutResponse;

import java.math.BigDecimal;

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

        User testUser = User.builder().login("barbeiro@teste.com").build();
        testBusiness = Business.builder()
                .id("biz-123")
                .name("Barbearia Teste")
                .user(testUser)
                .cpfCnpj("12345678900")
                .build();

        testPlan = Plan.builder()
                .name("PRO")
                .price(new BigDecimal("50.00"))
                .billingCycle(BillingCycle.MONTHLY)
                .build();
    }

    @AfterEach
    void tearDown() {
        mockServer.verify();
    }

    // ==================== CREATE CUSTOMER TESTS ====================

    @Test
    @DisplayName("Should create customer and return ID successfully")
    void shouldCreateCustomerSuccessfully() {
        // Arrange
        String expectedJsonResponse = "{\"id\": \"cus_000001\"}";

        mockServer.expect(requestTo(API_URL + "/customers"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("access_token", API_KEY))
                .andExpect(jsonPath("$.name").value("Barbearia Teste"))
                .andRespond(withSuccess(expectedJsonResponse, MediaType.APPLICATION_JSON));

        // Act
        String customerId = adapter.createOrUpdateCustomer(testBusiness);

        // Assert
        assertThat(customerId).isEqualTo("cus_000001");
    }

    @Test
    @DisplayName("Should throw Exception when Asaas response does not contain ID on customer creation")
    void shouldThrowWhenCreateCustomerFails() {
        // Arrange
        String failureJsonResponse = "{\"error\": \"invalid_data\"}"; // Resposta sem ID

        mockServer.expect(requestTo(API_URL + "/customers"))
                .andRespond(withSuccess(failureJsonResponse, MediaType.APPLICATION_JSON));

        // Act & Assert
        assertThatThrownBy(() -> adapter.createOrUpdateCustomer(testBusiness))
                .isInstanceOf(AppException.class)
                .hasMessage("Failed to communicate with the payment gateway.")
                .extracting(e -> ((AppException) e).getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    // ==================== SUBSCRIPTION CHECKOUT TESTS ====================

    @Test
    @DisplayName("Should generate MONTHLY checkout link successfully")
    void shouldCreateMonthlyCheckout() {
        // Arrange
        String expectedJsonResponse = "{\"id\": \"sub_001\", \"url\": \"https://asaas.com/checkout/123\"}";

        mockServer.expect(requestTo(API_URL + "/paymentLinks"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.chargeType").value("RECURRENT"))
                .andExpect(jsonPath("$.customer").value("cus_123"))
                .andRespond(withSuccess(expectedJsonResponse, MediaType.APPLICATION_JSON));

        // Act
        GatewayCheckoutResponse response = adapter.createSubscriptionCheckout(testBusiness, testPlan, "cus_123");

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.gatewaySubscriptionId()).isEqualTo("sub_001");
        assertThat(response.checkoutUrl()).isEqualTo("https://asaas.com/checkout/123");
    }

    @Test
    @DisplayName("Should generate YEARLY checkout link successfully")
    void shouldCreateYearlyCheckout() {
        // Arrange
        testPlan.setBillingCycle(BillingCycle.YEARLY);
        String expectedJsonResponse = "{\"id\": \"sub_002\", \"url\": \"https://asaas.com/checkout/456\"}";

        mockServer.expect(requestTo(API_URL + "/paymentLinks"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.chargeType").value("INSTALLMENT"))
                .andExpect(jsonPath("$.maxInstallmentCount").value(12))
                .andRespond(withSuccess(expectedJsonResponse, MediaType.APPLICATION_JSON));

        // Act
        GatewayCheckoutResponse response = adapter.createSubscriptionCheckout(testBusiness, testPlan, "cus_123");

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.gatewaySubscriptionId()).isEqualTo("sub_002");
        assertThat(response.checkoutUrl()).isEqualTo("https://asaas.com/checkout/456");
    }

    @Test
    @DisplayName("Should throw BAD_REQUEST when plan has no billing cycle")
    void shouldThrowWhenPlanCycleIsNull() {
        // Arrange
        testPlan.setBillingCycle(null);

        mockServer.reset();

        // Act & Assert
        assertThatThrownBy(() -> adapter.createSubscriptionCheckout(testBusiness, testPlan, "cus_123"))
                .isInstanceOf(AppException.class)
                .hasMessage("The selected plan has an invalid billing cycle.")
                .extracting(e -> ((AppException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ==================== CANCEL SUBSCRIPTION TESTS ====================

    @Test
    @DisplayName("Should send DELETE request to cancel subscription")
    void shouldCancelSubscription() {
        // Arrange
        String gatewaySubscriptionId = "sub_999";

        mockServer.expect(requestTo(API_URL + "/paymentLinks/" + gatewaySubscriptionId))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withNoContent()); // HTTP 204

        // Act
        adapter.cancelSubscription(gatewaySubscriptionId);

    }
}