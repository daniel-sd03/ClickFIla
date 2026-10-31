package sodresoftwares.barbearia.dto.billing;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import sodresoftwares.barbearia.model.billing.PaymentMethod;

public record CheckoutRequestDTO(
        @NotBlank(message = "Plan ID is required")
        String planId,

        @NotNull(message = "Payment method is required")
        PaymentMethod paymentMethod,

        @NotNull(message = "Credit card data is required")
        @Valid
        CreditCardDTO creditCard,

        @NotNull(message = "Credit card holder information is required")
        @Valid
        CreditCardHolderDTO creditCardHolderInfo
) {}