package sodresoftwares.barbearia.dto.billing;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import sodresoftwares.barbearia.model.billing.PaymentMethod;

public record ChangePlanDTO(
        @NotBlank(message = "Plan ID is required")
        String planId,

        @NotNull(message = "Payment method is required")
        PaymentMethod paymentMethod,

        @Valid
        CreditCardDTO creditCard,

        @Valid
        CreditCardHolderDTO creditCardHolderInfo
) {}