package sodresoftwares.barbearia.dto.billing;

import sodresoftwares.barbearia.model.billing.SubscriptionStatus;
import java.time.LocalDate;

public record SubscriptionResponseDTO(
        SubscriptionStatus status,
        PlanResponseDTO plan,
        LocalDate currentPeriodEnd,
        boolean cancelAtPeriodEnd,
        CreditCardSummaryDTO creditCard
) {}