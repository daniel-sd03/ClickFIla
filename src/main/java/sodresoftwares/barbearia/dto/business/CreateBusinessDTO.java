package sodresoftwares.barbearia.dto.business;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateBusinessDTO(
        @NotBlank(message = "Name is required ")
        String name,

        @NotBlank(message = "CPF/CNPJ is required")
        @Size(min = 11, max = 14, message = "CPF/CNPJ must be 11 or 14 characters long.")
        String cpfCnpj
) {}