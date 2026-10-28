package sodresoftwares.barbearia.dto.business;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import sodresoftwares.barbearia.infra.validation.CpfOrCnpj;

public record CreateBusinessDTO(
        @NotBlank(message = "Name is required ")
        String name,

        @NotBlank(message = "CPF or CNPJ is required.")
        @CpfOrCnpj(message = "The provided CPF or CNPJ is invalid.")
        String cpfCnpj
) {}