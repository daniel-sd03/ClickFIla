package sodresoftwares.barbearia.dto.billing;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CheckoutRequest(
        @NotBlank(message = "Plan ID is required")
        String planId,

        @NotBlank(message = "Payment method is required (CREDIT_CARD or PIX)")
        String paymentMethod,

        @NotNull(message = "Credit card data is required")
        @Valid
        CreditCardDTO creditCard,

        @NotNull(message = "Credit card holder information is required")
        @Valid
        CreditCardHolderDTO creditCardHolderInfo
) {}