package sodresoftwares.barbearia.services.team;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sodresoftwares.barbearia.dto.team.QuickCreateMemberDTO;
import sodresoftwares.barbearia.infra.exception.AppException;
import sodresoftwares.barbearia.model.business.Business;
import sodresoftwares.barbearia.model.team.TeamMember;
import sodresoftwares.barbearia.model.team.TeamRole;
import sodresoftwares.barbearia.repositories.queue.QueueEntryRepository;
import sodresoftwares.barbearia.repositories.team.TeamMemberRepository;
import sodresoftwares.barbearia.services.business.BusinessService;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TeamMemberService {

    private final TeamMemberRepository teamMemberRepository;
    private final QueueEntryRepository queueEntryRepository;
    private final BusinessService businessService;

    @Transactional
    public void quickCreateMember(String loggedUserId, QuickCreateMemberDTO dto) {

        Business business = getBusinessForOwner(loggedUserId);

        TeamMember teamMember = TeamMember.builder()
                .business(business)
                .name(dto.name().trim())
                .user(null)
                .role(TeamRole.STAFF)
                .isActive(true)
                .build();

        teamMemberRepository.save(teamMember);
        log.info("Team member created without a linked user account.");
    }

    @Transactional
    public void removeMember(String loggedUserId, String memberIdToRemove) {
        Business business = getBusinessForOwner(loggedUserId);

        TeamMember memberToRemove = teamMemberRepository.findById(memberIdToRemove)
                .orElseThrow(() -> new AppException(
                        HttpStatus.NOT_FOUND,
                        "MEMBER_NOT_FOUND",
                        "Team member not found."
                ));

        if (!memberToRemove.getBusiness().getId().equals(business.getId())) {
            throw new AppException(
                    HttpStatus.FORBIDDEN,
                    "ACCESS_DENIED",
                    "This member does not belong to your business."
            );
        }

        if (memberToRemove.getRole() == TeamRole.OWNER) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "CANNOT_REMOVE_OWNER",
                    "The business owner cannot be removed."
            );
        }

        if (queueEntryRepository.hasActiveServiceByMemberId(memberToRemove.getId())) {
            throw new AppException(
                    HttpStatus.CONFLICT,
                    "MEMBER_IN_ACTIVE_SERVICE",
                    "Cannot remove a member who is currently calling or serving a client."
            );
        }

        memberToRemove.setIsActive(false);
        teamMemberRepository.save(memberToRemove);
        log.info("Team member deactivated.");
    }

    @Transactional
    public void leaveTeam(String loggedUserId) {
        TeamMember member = teamMemberRepository.findActiveByUserId(loggedUserId)
                .orElseThrow(() -> new AppException(
                        HttpStatus.NOT_FOUND,
                        "MEMBER_NOT_FOUND",
                        "You are not associated with any team."
                ));

        if (member.getRole() == TeamRole.OWNER) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "OWNER_CANNOT_LEAVE",
                    "The owner cannot leave the team. You must delete or transfer the business."
            );
        }

        if (queueEntryRepository.hasActiveServiceByMemberId(member.getId())) {
            throw new AppException(
                    HttpStatus.CONFLICT,
                    "MEMBER_IN_ACTIVE_SERVICE",
                    "Finish your current client service before leaving the team."
            );
        }

        member.setIsActive(false);
        teamMemberRepository.save(member);
        log.info("Team member left the business voluntarily.");
    }

    @Transactional
    public void deactivateProfessionalLinksForUser(String userId) {
        teamMemberRepository.findActiveByUserIdWithBusiness(userId).ifPresent(member -> {
            if (member.getRole() == TeamRole.OWNER) {
                businessService.deactivateBusiness(member.getBusiness());
            } else {
                if (queueEntryRepository.hasActiveServiceByMemberId(member.getId())) {
                    throw new AppException(
                            HttpStatus.CONFLICT,
                            "MEMBER_IN_ACTIVE_SERVICE",
                            "Finish your current client service before changing to a client account."
                    );
                }
                member.setIsActive(false);
                teamMemberRepository.save(member);
            }
        });
    }

    private Business getBusinessForOwner(String loggedUserId) {
        TeamMember member = teamMemberRepository.findActiveByUserIdWithBusiness(loggedUserId)
                .orElseThrow(() -> new AppException(
                        HttpStatus.NOT_FOUND,
                        "TEAM_MEMBER_NOT_FOUND",
                        "User is not associated with any team/business."
                ));

        if (member.getRole() != TeamRole.OWNER) {
            throw new AppException(
                    HttpStatus.FORBIDDEN,
                    "ACCESS_DENIED",
                    "Only the business owner can perform this action."
            );
        }

        return member.getBusiness();
    }
}