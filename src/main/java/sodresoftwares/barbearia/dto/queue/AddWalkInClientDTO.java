package sodresoftwares.barbearia.dto.queue;

import jakarta.validation.constraints.NotBlank;

public record AddWalkInClientDTO(
        @NotBlank String queueSessionId,
        @NotBlank String serviceName,
        @NotBlank String clientName,
        @NotBlank String actionMemberId
) {}