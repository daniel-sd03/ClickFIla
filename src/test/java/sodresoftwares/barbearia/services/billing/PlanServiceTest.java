package sodresoftwares.barbearia.services.billing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import sodresoftwares.barbearia.dto.billing.PlanResponseDTO;
import sodresoftwares.barbearia.model.billing.BillingCycle;
import sodresoftwares.barbearia.model.billing.Plan;
import sodresoftwares.barbearia.repositories.billing.PlanRepository;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PlanService Tests")
class PlanServiceTest {

    @Mock
    private PlanRepository planRepository;

    @InjectMocks
    private PlanService planService;

    @Test
    @DisplayName("Should return a list of active plans mapped to DTOs")
    void testGetAllActivePlans_Success() {
        // Arrange
        Plan plan1 = Plan.builder()
                .id("plan-pro-monthly-001")
                .code("PRO_MONTHLY")
                .name("Plano PRO (Mensal)")
                .price(new BigDecimal("49.90"))
                .billingCycle(BillingCycle.MONTHLY)
                .isActive(true)
                .build();

        Plan plan2 = Plan.builder()
                .id("plan-pro-yearly-001")
                .code("PRO_YEARLY")
                .name("Plano PRO (Anual)")
                .price(new BigDecimal("499.00"))
                .billingCycle(BillingCycle.YEARLY)
                .isActive(true)
                .build();

        when(planRepository.findByIsActiveTrue()).thenReturn(List.of(plan1, plan2));

        // Act
        List<PlanResponseDTO> result = planService.getAllActivePlans();

        // Assert
        assertThat(result).hasSize(2);

        assertThat(result.get(0).id()).isEqualTo("plan-pro-monthly-001");
        assertThat(result.get(0).code()).isEqualTo("PRO_MONTHLY");
        assertThat(result.get(0).price()).isEqualTo(new BigDecimal("49.90"));

        assertThat(result.get(1).id()).isEqualTo("plan-pro-yearly-001");
        assertThat(result.get(1).code()).isEqualTo("PRO_YEARLY");
        assertThat(result.get(1).price()).isEqualTo(new BigDecimal("499.00"));

        verify(planRepository).findByIsActiveTrue();
    }

    @Test
    @DisplayName("Should return an empty list when no active plans are found")
    void testGetAllActivePlans_Empty() {
        // Arrange
        when(planRepository.findByIsActiveTrue()).thenReturn(List.of());

        // Act
        List<PlanResponseDTO> result = planService.getAllActivePlans();

        // Assert
        assertThat(result).isEmpty();
        verify(planRepository).findByIsActiveTrue();
    }
}