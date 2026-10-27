package sodresoftwares.barbearia.dto.billing;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CreditCardHolderDTO(
        @NotBlank(message = "Holder name is required")
        String name,

        @NotBlank(message = "Holder email is required")
        @Email(message = "Invalid email format")
        String email,

        @NotBlank(message = "Holder CPF or CNPJ is required")
        @Pattern(regexp = "\\d{11}|\\d{14}", message = "CPF or CNPJ must contain only numbers (11 or 14 digits)")
        String cpfCnpj,

        @NotBlank(message = "Postal code is required")
        @Pattern(regexp = "\\d{8}", message = "Postal code must contain 8 digits")
        String postalCode,

        @NotBlank(message = "Address number is required")
        String addressNumber,

        String addressComplement,

        @NotBlank(message = "Phone number is required")
        String phone,

        String mobilePhone
) {}