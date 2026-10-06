package sodresoftwares.barbearia.dto.business;

import sodresoftwares.barbearia.dto.queue.QueueEntryResponseDTO;
import sodresoftwares.barbearia.dto.team.TeamInviteResponseDTO;
import sodresoftwares.barbearia.dto.team.TeamMemberDTO;
import sodresoftwares.barbearia.model.team.TeamRole;

import java.util.List;

public record BusinessDashboardDTO(
        String businessId,
        String businessName,
        String loggedMemberId,
        TeamRole loggedMemberRole,
        String sessionId,
        String ticketCode,
        boolean isActive,
        Integer toleranceMinutes,
        List<QueueEntryResponseDTO> activeQueue,
        List<TeamMemberDTO> team,
        List<TeamInviteResponseDTO> pendingInvites,
        boolean emailVerified
) {}