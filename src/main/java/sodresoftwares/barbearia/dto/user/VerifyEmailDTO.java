package sodresoftwares.barbearia.dto.user;

import jakarta.validation.constraints.NotBlank;

public record VerifyEmailDTO(
        @NotBlank(message = "code is required")
        String code
) {}