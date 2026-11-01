package sodresoftwares.barbearia.services.business;

import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sodresoftwares.barbearia.dto.business.BusinessDashboardDTO;
import sodresoftwares.barbearia.dto.queue.QueueEntryResponseDTO;
import sodresoftwares.barbearia.dto.team.TeamInviteResponseDTO;
import sodresoftwares.barbearia.dto.team.TeamMemberDTO;
import sodresoftwares.barbearia.model.billing.SubscriptionStatus;
import sodresoftwares.barbearia.model.business.Business;
import sodresoftwares.barbearia.model.team.InviteStatus;
import sodresoftwares.barbearia.model.queue.QueueSession;
import sodresoftwares.barbearia.model.team.TeamMember;
import sodresoftwares.barbearia.model.user.User;
import sodresoftwares.barbearia.repositories.billing.SubscriptionRepository;
import sodresoftwares.barbearia.repositories.business.BusinessRepository;
import sodresoftwares.barbearia.repositories.queue.QueueSessionRepository;
import sodresoftwares.barbearia.repositories.team.TeamInviteRepository;
import sodresoftwares.barbearia.repositories.team.TeamMemberRepository;
import sodresoftwares.barbearia.repositories.user.UserRepository;
import sodresoftwares.barbearia.services.queue.QueueCacheService;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {

    private final TeamMemberRepository teamMemberRepository;
    private final BusinessRepository businessRepository;
    private final QueueSessionRepository queueSessionRepository;
    private final QueueCacheService queueCacheService;
    private final TeamInviteRepository teamInviteRepository;
    private final SubscriptionRepository subscriptionRepository;

    private record SubInfo(SubscriptionStatus status, LocalDate end) {}

    private SubInfo getSubscriptionInfo(String businessId) {
        return subscriptionRepository.findByBusinessId(businessId)
                .map(sub -> new SubInfo(sub.getStatus(), sub.getCurrentPeriodEnd()))
                .orElse(new SubInfo(null, null));
    }

    public BusinessDashboardDTO getProfessionalDashboard(User loggedUser) {

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

        Optional<TeamMember> loggedMemberOpt = teamMemberRepository.findActiveByUserIdWithBusiness(loggedUser.getId());

        if (loggedMemberOpt.isEmpty()) {
            Optional<Business> ownedBusinessOpt = businessRepository.findByUserId(loggedUser.getId());


            if (ownedBusinessOpt.isPresent()) {
                Business inactiveBusiness = ownedBusinessOpt.get();
                SubInfo subInfo = getSubscriptionInfo(inactiveBusiness.getId());
                return new BusinessDashboardDTO(
                        inactiveBusiness.getId(), inactiveBusiness.getName(), null, null,
                        null, null, false, null,
                        List.of(), List.of(), pendingInvites,
                        loggedUser.getEmailVerified(),
                        false,
                        subInfo.status(), subInfo.end()
                );
            }

            return new BusinessDashboardDTO(
                    null, null, null, null,
                    null, null, false, null,
                    List.of(), List.of(), pendingInvites, loggedUser.getEmailVerified(),
                    null, null ,null
            );
        }

        TeamMember loggedMember = loggedMemberOpt.get();
        String businessId = loggedMember.getBusiness().getId();
        String businessName = loggedMember.getBusiness().getName();
        SubInfo subInfo = getSubscriptionInfo(businessId);

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
                    List.of(), teamDtos, pendingInvites,
                    loggedUser.getEmailVerified(), true,
                    subInfo.status(), subInfo.end()
            );
        }

        QueueSession session = sessionOpt.get();

        List<QueueEntryResponseDTO> activeQueueDtos = queueCacheService.getActiveEntriesDTO(session.getId());

        return new BusinessDashboardDTO(
                businessId, businessName, loggedMember.getId(), loggedMember.getRole(),
                session.getId(), session.getTicketCode(), session.getIsActive(),
                session.getToleranceMinutes(), activeQueueDtos, teamDtos, pendingInvites,
                loggedUser.getEmailVerified(), true,
                subInfo.status(), subInfo.end()
        );

    }
}