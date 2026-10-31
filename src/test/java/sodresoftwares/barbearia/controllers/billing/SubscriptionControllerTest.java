package sodresoftwares.barbearia.controllers.billing;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientAutoConfiguration;
import org.springframework.boot.security.oauth2.client.autoconfigure.servlet.OAuth2ClientWebSecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.json.AutoConfigureJsonTesters;
import org.springframework.boot.test.json.JacksonTester;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import sodresoftwares.barbearia.dto.billing.*;
import sodresoftwares.barbearia.infra.security.SecurityFilter;
import sodresoftwares.barbearia.infra.security.SubscriptionCheckInterceptor;
import sodresoftwares.barbearia.infra.security.WebMvcConfig;
import sodresoftwares.barbearia.model.billing.BillingCycle;
import sodresoftwares.barbearia.model.billing.PaymentMethod; // 👈 IMPORT ADICIONADO
import sodresoftwares.barbearia.model.billing.SubscriptionStatus;
import sodresoftwares.barbearia.model.user.User;
import sodresoftwares.barbearia.model.user.UserRole;
import sodresoftwares.barbearia.services.billing.SubscriptionService;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = SubscriptionController.class,
        excludeFilters = {
                @ComponentScan.Filter(
                        type = FilterType.ASSIGNABLE_TYPE,
                        classes = {
                                SecurityFilter.class,
                                SubscriptionCheckInterceptor.class,
                                WebMvcConfig.class
                        }
                )
        },
        excludeAutoConfiguration = {
                OAuth2ClientAutoConfiguration.class,
                OAuth2ClientWebSecurityAutoConfiguration.class
        }
)
@ActiveProfiles("test")
@AutoConfigureMockMvc(addFilters = false)
@AutoConfigureJsonTesters
@DisplayName("SubscriptionController Tests")
class SubscriptionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JacksonTester<Object> jsonTester;

    @MockitoBean
    private SubscriptionService subscriptionService;

    @MockitoBean
    private CacheManager cacheManager;

    private User loggedUser;

    @BeforeEach
    void setUp() {
        loggedUser = User.builder()
                .id("user-123")
                .login("test@test.com")
                .role(UserRole.PROFESSIONAL)
                .build();

        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(loggedUser, null, loggedUser.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ==================== POST CHECKOUT TESTS ====================

    @Test
    @DisplayName("POST /subscriptions/checkout -> Should process transparent checkout successfully")
    void shouldProcessTransparentCheckout() throws Exception {
        CreditCardDTO creditCard = new CreditCardDTO(
                "João F FERNANDES", "4111111111111111", "12", "2028", "123"
        );

        CreditCardHolderDTO holderInfo = new CreditCardHolderDTO(
                "João Fernandes", "test@test.com", "12345678909",
                "30123456", "100", null, "31999999999", null
        );

        CheckoutRequestDTO request = new CheckoutRequestDTO("PRO_MONTHLY", PaymentMethod.CREDIT_CARD,
                creditCard, holderInfo);

        mockMvc.perform(post("/subscriptions/checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonTester.write(request).getJson()))
                .andExpect(status().isNoContent());
    }

    // ==================== PUT UPDATE PAYMENT METHOD TESTS ====================

    @Test
    @DisplayName("PUT /subscriptions/payment-method -> Should update payment method successfully")
    void shouldUpdatePaymentMethod() throws Exception {
        CreditCardDTO creditCard = new CreditCardDTO("João F", "4111111111111111",
                "12", "2028", "123");
        CreditCardHolderDTO holderInfo = new CreditCardHolderDTO("João F", "test@test.com",
                "12345678909", "30123456", "100", null, "31999999999", null);

        CheckoutRequestDTO request = new CheckoutRequestDTO("PRO_MONTHLY", PaymentMethod.CREDIT_CARD,
                creditCard, holderInfo);

        mockMvc.perform(put("/subscriptions/payment-method")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonTester.write(request).getJson()))
                .andExpect(status().isOk());
    }

    // ==================== PUT CHANGE PLAN TESTS ====================

    @Test
    @DisplayName("PUT /subscriptions/plan -> Should change plan successfully")
    void shouldChangePlan() throws Exception {
        ChangePlanDTO request = new ChangePlanDTO("PRO_YEARLY", PaymentMethod.CREDIT_CARD, null, null);

        mockMvc.perform(put("/subscriptions/plan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonTester.write(request).getJson()))
                .andExpect(status().isNoContent());
    }

    // ==================== POST CANCEL SUBSCRIPTION TESTS ====================

    @Test
    @DisplayName("POST /subscriptions/cancel/{businessId} -> Should cancel subscription successfully")
    void shouldCancelSubscription() throws Exception {
        mockMvc.perform(post("/subscriptions/cancel/biz-123"))
                .andExpect(status().isNoContent());
    }

    // ==================== GET MY SUBSCRIPTION TESTS ====================

    @Test
    @DisplayName("GET /subscriptions/me -> Should get logged user subscription details")
    void shouldGetMySubscription() throws Exception {

        PlanResponseDTO planDto = new PlanResponseDTO(
                "plan-123",
                "PRO_MONTHLY",
                "Plano PRO",
                new BigDecimal("50.00"),
                BillingCycle.MONTHLY
        );
        CreditCardSummaryDTO cardDto = new CreditCardSummaryDTO("VISA", "1234");

        SubscriptionResponseDTO responseDTO = new SubscriptionResponseDTO(
                SubscriptionStatus.ACTIVE,
                planDto,
                LocalDate.now().plusDays(30),
                false,
                cardDto
        );

        when(subscriptionService.getMySubscriptionDetails(nullable(String.class)))
                .thenReturn(responseDTO);

        mockMvc.perform(get("/subscriptions/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.plan.code").value("PRO_MONTHLY"))
                .andExpect(jsonPath("$.creditCard.lastFourDigits").value("1234"));
    }
}