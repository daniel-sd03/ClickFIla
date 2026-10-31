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

        @Valid
        CreditCardDTO creditCard,

        @Valid
        CreditCardHolderDTO creditCardHolderInfo
) {
        @JsonIgnore
        @AssertTrue(message = "Credit card data and holder info are required when payment method is CREDIT_CARD")
        public boolean isCreditCardValid() {
                if (this.paymentMethod == PaymentMethod.CREDIT_CARD) {
                        return this.creditCard != null && this.creditCardHolderInfo != null;
                }
                return true;
        }
}