package sodresoftwares.barbearia.dto.billing;

import sodresoftwares.barbearia.model.billing.BillingCycle;
import sodresoftwares.barbearia.model.billing.Plan;

import java.math.BigDecimal;

public record PlanResponseDTO(
        String id,
        String code,
        String name,
        BigDecimal price,
        BillingCycle billingCycle
) {
    public static PlanResponseDTO fromEntity(Plan plan) {
        return new PlanResponseDTO(
                plan.getId(),
                plan.getCode(),
                plan.getName(),
                plan.getPrice(),
                plan.getBillingCycle()
        );
    }
}