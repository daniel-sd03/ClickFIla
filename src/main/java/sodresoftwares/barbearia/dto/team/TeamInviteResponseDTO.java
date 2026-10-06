package sodresoftwares.barbearia.dto.team;

import sodresoftwares.barbearia.model.team.TeamRole;

import java.time.Instant;

public record TeamInviteResponseDTO(
        String id,
        String businessId,
        String businessName,
        String email,
        TeamRole role,
        Instant expiresAt
) {}