package sodresoftwares.barbearia.dto.billing;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import sodresoftwares.barbearia.model.billing.PaymentMethod;

public record CheckoutRequestDTO(
        @NotBlank(message = "Plan ID is required")
        String planId,

        @NotNull(message = "Payment method is required")
        PaymentMethod paymentMethod,

        String savedCardId,

        @Valid
        CreditCardDTO creditCard,

        @Valid
        CreditCardHolderDTO creditCardHolderInfo
) {}