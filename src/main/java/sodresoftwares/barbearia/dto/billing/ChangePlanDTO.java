package sodresoftwares.barbearia.dto.billing;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

public record ChangePlanDTO(
        @NotBlank(message = "Plan ID is required")
        String planId,

        @Valid
        CreditCardDTO creditCard,

        @Valid
        CreditCardHolderDTO creditCardHolderInfo
) {}