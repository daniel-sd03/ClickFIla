package sodresoftwares.barbearia.services.business;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sodresoftwares.barbearia.dto.business.BusinessDashboardDTO;
import sodresoftwares.barbearia.dto.queue.QueueEntryResponseDTO;
import sodresoftwares.barbearia.dto.team.TeamInviteResponseDTO;
import sodresoftwares.barbearia.dto.team.TeamMemberDTO;
import sodresoftwares.barbearia.mappers.QueueMapper;
import sodresoftwares.barbearia.model.team.InviteStatus;
import sodresoftwares.barbearia.model.queue.QueueSession;
import sodresoftwares.barbearia.model.team.TeamMember;
import sodresoftwares.barbearia.model.user.User;
import sodresoftwares.barbearia.repositories.queue.QueueSessionRepository;
import sodresoftwares.barbearia.repositories.team.TeamInviteRepository;
import sodresoftwares.barbearia.repositories.team.TeamMemberRepository;
import sodresoftwares.barbearia.services.queue.QueueCacheService;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {

    private final TeamMemberRepository teamMemberRepository;
    private final QueueSessionRepository queueSessionRepository;
    private final QueueCacheService queueCacheService;
    private final QueueMapper queueMapper;
    private final TeamInviteRepository teamInviteRepository;

    public BusinessDashboardDTO getProfessionalDashboard(User loggedUser) {

        Optional<TeamMember> loggedMemberOpt = teamMemberRepository.findActiveByUserIdWithBusiness(loggedUser.getId());

        if (loggedMemberOpt.isEmpty()) {
            List<TeamInviteResponseDTO> pendingInvites = teamInviteRepository.findAllByEmailAndStatusWithBusiness(loggedUser.getLogin(), InviteStatus.PENDING)
                    .stream()
                    .map(invite -> new TeamInviteResponseDTO(
                            invite.getId(),
                            invite.getBusiness().getId(),
                            invite.getBusiness().getName(),
                            invite.getEmail(),
                            invite.getRole(),
                            invite.getExpiresAt()
                    )).toList();

            return new BusinessDashboardDTO(
                    null, null, null, null,
                    null, null, false, null,
                    List.of(), List.of(), pendingInvites,
                    loggedUser.isEmailVerified()
            );
        }

        TeamMember loggedMember = loggedMemberOpt.get();
        String businessId = loggedMember.getBusiness().getId();
        String businessName = loggedMember.getBusiness().getName();

        List<TeamMemberDTO> teamDtos = teamMemberRepository.findAllByBusinessIdAndIsActiveTrueWithUser(businessId)
                .stream()
                .map(member -> new TeamMemberDTO(
                        member.getId(),
                        member.getName(),
                        member.getRole()
                )).toList();

        Optional<QueueSession> sessionOpt = queueSessionRepository.findByBusinessIdWithBusiness(businessId);

        if (sessionOpt.isEmpty()) {
            return new BusinessDashboardDTO(
                    businessId, businessName, loggedMember.getId(), loggedMember.getRole(),
                    null, null, false, null,
                    List.of(), teamDtos, List.of(),
                    loggedUser.isEmailVerified()
            );
        }

        QueueSession session = sessionOpt.get();

        List<QueueEntryResponseDTO> activeQueueDtos = queueCacheService.getActiveEntriesDTO(session.getId());

        return new BusinessDashboardDTO(
                businessId, businessName, loggedMember.getId(), loggedMember.getRole(),
                session.getId(), session.getTicketCode(), session.getIsActive(),
                session.getToleranceMinutes(), activeQueueDtos, teamDtos, List.of(),
                loggedUser.isEmailVerified()
        );
    }
}