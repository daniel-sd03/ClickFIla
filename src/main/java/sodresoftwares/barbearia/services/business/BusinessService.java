package sodresoftwares.barbearia.services.business;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sodresoftwares.barbearia.dto.business.BusinessResponseDTO;
import sodresoftwares.barbearia.dto.business.CreateBusinessDTO;
import sodresoftwares.barbearia.dto.business.UpdateBusinessDTO;
import sodresoftwares.barbearia.infra.exception.AppException;
import sodresoftwares.barbearia.model.business.Business;
import sodresoftwares.barbearia.model.team.TeamMember;
import sodresoftwares.barbearia.model.team.TeamRole;
import sodresoftwares.barbearia.model.user.User;
import sodresoftwares.barbearia.repositories.billing.SubscriptionRepository;
import sodresoftwares.barbearia.repositories.business.BusinessRepository;
import sodresoftwares.barbearia.repositories.queue.QueueEntryRepository;
import sodresoftwares.barbearia.repositories.queue.QueueSessionRepository;
import sodresoftwares.barbearia.repositories.team.TeamMemberRepository;
import sodresoftwares.barbearia.repositories.user.UserRepository;
import sodresoftwares.barbearia.services.billing.SubscriptionService;

import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BusinessService {

    private final BusinessRepository businessRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final UserRepository userRepository;
    private final QueueSessionRepository queueSessionRepository;
    private final QueueEntryRepository queueEntryRepository;
    private final SubscriptionService subscriptionService;
    private final SubscriptionRepository subscriptionRepository;

    public BusinessResponseDTO getMyBusinessProfile(String userId) {

        Business business = businessRepository.findActiveByUserIdWithUser(userId)
                .orElseThrow(() -> new AppException(
                        HttpStatus.NOT_FOUND,
                        "BUSINESS_NOT_FOUND",
                        "Business profile not found for this user."
                ));

        return BusinessResponseDTO.fromEntity(business);
    }

    @Transactional
    public void createBusiness(String userId, CreateBusinessDTO data) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(
                        HttpStatus.NOT_FOUND,
                        "USER_NOT_FOUND",
                        "User not found."
                ));

        Optional<Business> existingBusinessOpt = businessRepository.findByUserId(userId);

        if (existingBusinessOpt.isPresent()) {
            Business existingBusiness = existingBusinessOpt.get();

            if (existingBusiness.getIsActive()) {
                throw new AppException(
                        HttpStatus.CONFLICT,
                        "BUSINESS_ALREADY_EXISTS",
                        "This user already owns an active registered business."
                );
            }

            existingBusiness.setName(data.name().trim());
            existingBusiness.setIsActive(true);
            businessRepository.save(existingBusiness);

            TeamMember ownerMember = teamMemberRepository
                    .findByBusinessIdAndUserId(existingBusiness.getId(), userId)
                    .map(existingMember -> {
                        existingMember.setName(user.getName());
                        existingMember.setRole(TeamRole.OWNER);
                        existingMember.setIsActive(true);
                        return existingMember;
                    })
                    .orElseGet(() -> TeamMember.builder()
                            .business(existingBusiness)
                            .name(user.getName())
                            .user(user)
                            .role(TeamRole.OWNER)
                            .isActive(true)
                            .build());

            teamMemberRepository.save(ownerMember);

            if (!subscriptionRepository.existsByBusinessId(existingBusiness.getId())) {
                subscriptionService.createTrialSubscription(existingBusiness);
            }

            log.info("Existing business reactivated successfully for user {}", userId);
            return;
        }

        Business newBusiness = Business.builder()
                .user(user)
                .name(data.name().trim())
                .isActive(true)
                .build();

        Business savedBusiness = businessRepository.save(newBusiness);

        TeamMember ownerMember = TeamMember.builder()
                .business(savedBusiness)
                .name(user.getName())
                .user(user)
                .role(TeamRole.OWNER)
                .isActive(true)
                .build();

        teamMemberRepository.save(ownerMember);

        subscriptionService.createTrialSubscription(savedBusiness);

        log.info("Business and Owner Team Member registered successfully");
    }

    @Transactional
    public BusinessResponseDTO updateBusinessProfile(String userId, UpdateBusinessDTO dto) {
        Business business = businessRepository.findActiveByUserIdWithUser(userId)
                .orElseThrow(() -> new AppException(
                        HttpStatus.NOT_FOUND,
                        "BUSINESS_NOT_FOUND",
                        "Business profile not found for this user."
                ));

        if (dto.name() != null && !dto.name().isBlank()) {
            business.setName(dto.name().trim());
        }

        Business updatedBusiness = businessRepository.save(business);

        log.info("Business profile updated successfully");
        return BusinessResponseDTO.fromEntity(updatedBusiness);
    }

    @Transactional
    public void deactivateBusiness(Business business) {
        if (queueSessionRepository.existsByBusinessIdAndIsActiveTrue(business.getId())) {
            throw new AppException(
                    HttpStatus.CONFLICT,
                    "ACTIVE_QUEUE_SESSION",
                    "Cannot deactivate business while there is an active queue session. Close the queue first."
            );
        }

        if (queueEntryRepository.hasActiveEntriesByBusinessId(business.getId())) {
            throw new AppException(
                    HttpStatus.CONFLICT,
                    "CLIENTS_STILL_IN_QUEUE",
                    "Cannot deactivate business while there are still clients waiting or being served in the queue."
            );
        }

        business.setIsActive(false);
        businessRepository.save(business);

        teamMemberRepository.deactivateAllByBusinessId(business.getId());
        log.info("Business {} and all its active team members were deactivated.", business.getId());
    }
}