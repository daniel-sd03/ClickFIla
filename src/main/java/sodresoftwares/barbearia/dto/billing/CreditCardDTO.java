package sodresoftwares.barbearia.dto.billing;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CreditCardDTO(
        @NotBlank(message = "Card holder name is required")
        String holderName,

        @NotBlank(message = "Card number is required")
        @Pattern(regexp = "\\d{13,16}", message = "Invalid card number")
        String number,

        @NotBlank(message = "Expiration month is required")
        @Pattern(regexp = "0[1-9]|1[0-2]", message = "Month must have 2 digits (01 to 12)")
        String expiryMonth,

        @NotBlank(message = "Expiration year is required")
        @Pattern(regexp = "\\d{4}", message = "Year must have 4 digits (e.g., 2028)")
        String expiryYear,

        @NotBlank(message = "Security code (CVV) is required")
        @Pattern(regexp = "\\d{3,4}", message = "CVV must have 3 or 4 digits")
        String ccv
) {}