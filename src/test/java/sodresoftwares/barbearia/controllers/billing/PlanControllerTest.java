package sodresoftwares.barbearia.controllers.billing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import sodresoftwares.barbearia.dto.billing.PlanResponseDTO;
import sodresoftwares.barbearia.infra.security.SecurityFilter;
import sodresoftwares.barbearia.infra.security.SubscriptionCheckInterceptor;
import sodresoftwares.barbearia.infra.security.WebMvcConfig;
import sodresoftwares.barbearia.model.billing.BillingCycle;
import sodresoftwares.barbearia.services.billing.PlanService;
import sodresoftwares.barbearia.services.billing.SubscriptionService;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = PlanController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {
                        SecurityFilter.class,
                        SubscriptionCheckInterceptor.class,
                        WebMvcConfig.class
                }
        ),
        excludeAutoConfiguration = {
                SecurityAutoConfiguration.class,
                OAuth2ClientAutoConfiguration.class
        }
)
@ActiveProfiles("test")
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("PlanController Tests")
class PlanControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PlanService planService;

    @MockitoBean
    private CacheManager cacheManager;

    @MockitoBean
    private SubscriptionService subscriptionService;

    @Test
    @DisplayName("GET /plans -> Should return 200 OK and a list of active plans")
    void testListPlans_Success() throws Exception {
        // Arrange
        PlanResponseDTO plan1 = new PlanResponseDTO("plan-pro-monthly", "PRO_MONTHLY", "Plano PRO (Mensal)", new BigDecimal("49.90"), BillingCycle.MONTHLY);
        PlanResponseDTO plan2 = new PlanResponseDTO("plan-pro-yearly", "PRO_YEARLY", "Plano PRO (Anual)", new BigDecimal("499.00"), BillingCycle.YEARLY);

        when(planService.getAllActivePlans()).thenReturn(List.of(plan1, plan2));

        // Act & Assert
        mockMvc.perform(get("/plans")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value("plan-pro-monthly"))
                .andExpect(jsonPath("$[0].code").value("PRO_MONTHLY"))
                .andExpect(jsonPath("$[0].name").value("Plano PRO (Mensal)"))
                .andExpect(jsonPath("$[0].price").value(49.9))
                .andExpect(jsonPath("$[0].billingCycle").value("MONTHLY"))
                .andExpect(jsonPath("$[1].code").value("PRO_YEARLY"));

        verify(planService).getAllActivePlans();
    }

    @Test
    @DisplayName("GET /plans -> Should return 200 OK and an empty list when no plans exist")
    void testListPlans_Empty() throws Exception {
        // Arrange
        when(planService.getAllActivePlans()).thenReturn(List.of());

        // Act & Assert
        mockMvc.perform(get("/plans")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        verify(planService).getAllActivePlans();
    }
}