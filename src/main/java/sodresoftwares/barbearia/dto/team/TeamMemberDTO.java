package sodresoftwares.barbearia.dto.team;

import sodresoftwares.barbearia.model.team.TeamRole;

public record TeamMemberDTO(
        String id,
        String name,
        TeamRole role
) {}
