package sodresoftwares.barbearia.services.business;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import sodresoftwares.barbearia.dto.business.BusinessResponseDTO;
import sodresoftwares.barbearia.dto.business.CreateBusinessDTO;
import sodresoftwares.barbearia.dto.business.UpdateBusinessDTO;
import sodresoftwares.barbearia.infra.exception.AppException;
import sodresoftwares.barbearia.model.business.Business;
import sodresoftwares.barbearia.model.team.TeamMember;
import sodresoftwares.barbearia.model.team.TeamRole;
import sodresoftwares.barbearia.model.user.User;
import sodresoftwares.barbearia.model.user.UserRole;
import sodresoftwares.barbearia.repositories.billing.SubscriptionRepository;
import sodresoftwares.barbearia.repositories.business.BusinessRepository;
import sodresoftwares.barbearia.repositories.queue.QueueEntryRepository;
import sodresoftwares.barbearia.repositories.queue.QueueSessionRepository;
import sodresoftwares.barbearia.repositories.team.TeamMemberRepository;
import sodresoftwares.barbearia.repositories.user.UserRepository;
import sodresoftwares.barbearia.services.billing.SubscriptionService;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("BusinessService Tests")
class BusinessServiceTest {

    @Mock
    private BusinessRepository businessRepository;

    @Mock
    private TeamMemberRepository teamMemberRepository;

    @Mock
    private QueueSessionRepository queueSessionRepository;

    @Mock
    private QueueEntryRepository queueEntryRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private SubscriptionService subscriptionService;

    @InjectMocks
    private BusinessService businessService;

    private CreateBusinessDTO createBusinessDTO;
    private Business testBusiness;
    private User testUser;

    private final String USER_ID = "user-123";
    private final String BUSINESS_ID = "biz-123";

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(USER_ID)
                .login("barbeiro@test.com")
                .name("Barbeiro Zé")
                .role(UserRole.PROFESSIONAL)
                .build();

        testBusiness = Business.builder()
                .id(BUSINESS_ID)
                .user(testUser)
                .name("Old Business Name")
                .isActive(true)
                .build();

        createBusinessDTO = new CreateBusinessDTO(
                "Barbearia do Zé",
                "12345678909"
        );
    }

    // ==================== GET MY BUSINESS PROFILE TESTS ====================

    @Test
    @DisplayName("Should return business profile including user data when it exists")
    void testGetMyBusinessProfile_Success() {
        // Arrange
        when(businessRepository.findActiveByUserIdWithUser(USER_ID)).thenReturn(Optional.of(testBusiness));

        // Act
        BusinessResponseDTO result = businessService.getMyBusinessProfile(USER_ID);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(BUSINESS_ID);
        assertThat(result.name()).isEqualTo("Old Business Name");

        assertThat(result.user()).isNotNull();
        assertThat(result.user().id()).isEqualTo(USER_ID);
        assertThat(result.user().name()).isEqualTo("Barbeiro Zé");

        verify(businessRepository).findActiveByUserIdWithUser(USER_ID);
    }

    @Test
    @DisplayName("Should throw not found exception when business profile does not exist on get")
    void testGetMyBusinessProfile_NotFound() {
        // Arrange
        when(businessRepository.findActiveByUserIdWithUser(USER_ID)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> businessService.getMyBusinessProfile(USER_ID))
                .isInstanceOf(AppException.class)
                .hasMessage("Business profile not found for this user.")
                .extracting(e -> ((AppException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ==================== CREATE BUSINESS TESTS ====================

    @Test
    @DisplayName("Should create new business and owner team member successfully")
    void testCreateBusiness_Successful() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(testUser));
        when(businessRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());
        when(businessRepository.save(any(Business.class))).thenReturn(testBusiness);

        businessService.createBusiness(USER_ID, createBusinessDTO);

        verify(userRepository).findById(USER_ID);
        verify(businessRepository).findByUserId(USER_ID);

        verify(businessRepository).save(argThat(business ->
                business.getUser().getId().equals(USER_ID) &&
                        business.getName().equals("Barbearia do Zé") &&
                        Boolean.TRUE.equals(business.getIsActive())
        ));

        verify(teamMemberRepository).save(argThat(member ->
                member.getBusiness().getId().equals(BUSINESS_ID) &&
                        member.getUser().getId().equals(USER_ID) &&
                        member.getRole().equals(TeamRole.OWNER) &&
                        member.getName().equals("Barbeiro Zé") &&
                        Boolean.TRUE.equals(member.getIsActive())
        ));

        verify(subscriptionService).createTrialSubscription(testBusiness);
    }

    @Test
    @DisplayName("Should throw exception when trying to create but user already has an inactive business")
    void testCreateBusiness_UserHasInactiveBusiness_ThrowsException() {
        testBusiness.setIsActive(false);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(testUser));
        when(businessRepository.findByUserId(USER_ID)).thenReturn(Optional.of(testBusiness));

        assertThatThrownBy(() -> businessService.createBusiness(USER_ID, createBusinessDTO))
                .isInstanceOf(AppException.class)
                .hasMessage("This user already owns an inactive business. Please reactivate it instead of creating a new one.")
                .extracting(e -> ((AppException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT);

        verify(businessRepository, never()).save(any(Business.class));
    }

    @Test
    @DisplayName("Should throw exception when trying to create a business for a user that already has an active one")
    void testCreateBusiness_UserAlreadyHasActiveBusiness() {
        testBusiness.setIsActive(true);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(testUser));
        when(businessRepository.findByUserId(USER_ID)).thenReturn(Optional.of(testBusiness));

        assertThatThrownBy(() -> businessService.createBusiness(USER_ID, createBusinessDTO))
                .isInstanceOf(AppException.class)
                .hasMessage("This user already owns an active registered business.")
                .extracting(e -> ((AppException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT);

        verify(businessRepository, never()).save(any(Business.class));
        verify(teamMemberRepository, never()).save(any(TeamMember.class));
    }

    @Test
    @DisplayName("Should throw exception when user is not found during business creation")
    void testCreateBusiness_UserNotFound() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> businessService.createBusiness(USER_ID, createBusinessDTO))
                .isInstanceOf(AppException.class)
                .hasMessage("User not found.")
                .extracting(e -> ((AppException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND);

        verify(businessRepository, never()).findByUserId(any());
        verify(businessRepository, never()).save(any(Business.class));
        verify(teamMemberRepository, never()).save(any(TeamMember.class));
    }

    // ==================== UPDATE BUSINESS PROFILE TESTS ====================

    @Test
    @DisplayName("Should update business name when valid DTO is provided")
    void testUpdateBusinessProfile_Success() {
        // Arrange
        UpdateBusinessDTO updateDTO = new UpdateBusinessDTO("New Business Name");

        when(businessRepository.findActiveByUserIdWithUser(USER_ID)).thenReturn(Optional.of(testBusiness));
        when(businessRepository.save(any(Business.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        BusinessResponseDTO result = businessService.updateBusinessProfile(USER_ID, updateDTO);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.name()).isEqualTo("New Business Name");

        verify(businessRepository).save(testBusiness);
    }

    @Test
    @DisplayName("Should NOT update business name if DTO value is blank")
    void testUpdateBusinessProfile_Success_BlankNameIgnored() {
        // Arrange
        UpdateBusinessDTO updateDTO = new UpdateBusinessDTO("   ");

        when(businessRepository.findActiveByUserIdWithUser(USER_ID)).thenReturn(Optional.of(testBusiness));
        when(businessRepository.save(any(Business.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        BusinessResponseDTO result = businessService.updateBusinessProfile(USER_ID, updateDTO);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.name()).isEqualTo("Old Business Name");

        verify(businessRepository).save(testBusiness);
    }

    @Test
    @DisplayName("Should throw not found exception when business profile does not exist for the user")
    void testUpdateBusinessProfile_BusinessNotFound() {
        // Arrange
        UpdateBusinessDTO updateDTO = new UpdateBusinessDTO("New Business Name");
        when(businessRepository.findActiveByUserIdWithUser(USER_ID)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> businessService.updateBusinessProfile(USER_ID, updateDTO))
                .isInstanceOf(AppException.class)
                .hasMessage("Business profile not found for this user.")
                .extracting(e -> ((AppException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND);

        verify(businessRepository, never()).save(any());
    }

    // ==================== DEACTIVATE BUSINESS TESTS ====================

    @Test
    @DisplayName("Should deactivate business and all its team members when no active queue or clients exist")
    void testDeactivateBusiness_Success() {
        when(queueSessionRepository.existsByBusinessIdAndIsActiveTrue(BUSINESS_ID)).thenReturn(false);
        when(queueEntryRepository.hasActiveEntriesByBusinessId(BUSINESS_ID)).thenReturn(false);

        businessService.deactivateBusiness(testBusiness);

        assertThat(testBusiness.getIsActive()).isFalse();
        verify(businessRepository).save(testBusiness);
        verify(teamMemberRepository).deactivateAllByBusinessId(BUSINESS_ID);
    }

    @Test
    @DisplayName("Should throw CONFLICT when trying to deactivate business with an active queue session")
    void testDeactivateBusiness_ActiveQueueSession() {
        when(queueSessionRepository.existsByBusinessIdAndIsActiveTrue(BUSINESS_ID)).thenReturn(true);

        assertThatThrownBy(() -> businessService.deactivateBusiness(testBusiness))
                .isInstanceOf(AppException.class)
                .hasMessage("Cannot deactivate business while there is an active queue session. Close the queue first.")
                .extracting(e -> ((AppException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT);

        verify(businessRepository, never()).save(any());
        verify(teamMemberRepository, never()).deactivateAllByBusinessId(any());
    }

    @Test
    @DisplayName("Should throw CONFLICT when trying to deactivate business with clients still in queue")
    void testDeactivateBusiness_ClientsStillInQueue() {
        when(queueSessionRepository.existsByBusinessIdAndIsActiveTrue(BUSINESS_ID)).thenReturn(false);
        when(queueEntryRepository.hasActiveEntriesByBusinessId(BUSINESS_ID)).thenReturn(true);

        assertThatThrownBy(() -> businessService.deactivateBusiness(testBusiness))
                .isInstanceOf(AppException.class)
                .hasMessage("Cannot deactivate business while there are still clients waiting or being served in the queue.")
                .extracting(e -> ((AppException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT);

        verify(businessRepository, never()).save(any());
        verify(teamMemberRepository, never()).deactivateAllByBusinessId(any());
    }

    // ==================== REACTIVATE BUSINESS TESTS ====================

    @Test
    @DisplayName("Should reactivate inactive business and its owner")
    void testReactivateBusiness_Success() {
        testBusiness.setIsActive(false);
        TeamMember inactiveOwner = TeamMember.builder().business(testBusiness).user(testUser).isActive(false).build();

        when(businessRepository.findByUserId(USER_ID)).thenReturn(Optional.of(testBusiness));
        when(teamMemberRepository.findByBusinessIdAndUserId(BUSINESS_ID, USER_ID)).thenReturn(Optional.of(inactiveOwner));
        when(subscriptionRepository.existsByBusinessId(BUSINESS_ID)).thenReturn(true);

        businessService.reactivateBusiness(USER_ID);

        assertThat(testBusiness.getIsActive()).isTrue();
        assertThat(inactiveOwner.getIsActive()).isTrue();
        verify(businessRepository).save(testBusiness);
        verify(teamMemberRepository).save(inactiveOwner);
    }

    @Test
    @DisplayName("Should throw CONFLICT if business is already active during reactivation")
    void testReactivateBusiness_AlreadyActive() {
        testBusiness.setIsActive(true);
        when(businessRepository.findByUserId(USER_ID)).thenReturn(Optional.of(testBusiness));

        assertThatThrownBy(() -> businessService.reactivateBusiness(USER_ID))
                .isInstanceOf(AppException.class)
                .hasMessage("This business is already active.")
                .extracting(e -> ((AppException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT);
    }
}