package sodresoftwares.barbearia.services.business;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import sodresoftwares.barbearia.dto.business.BusinessDashboardDTO;
import sodresoftwares.barbearia.dto.queue.QueueEntryResponseDTO;
import sodresoftwares.barbearia.mappers.QueueMapper;
import sodresoftwares.barbearia.model.billing.Subscription;
import sodresoftwares.barbearia.model.billing.SubscriptionStatus;
import sodresoftwares.barbearia.model.business.Business;
import sodresoftwares.barbearia.model.queue.QueueEntryStatus;
import sodresoftwares.barbearia.model.queue.QueueSession;
import sodresoftwares.barbearia.model.team.InviteStatus;
import sodresoftwares.barbearia.model.team.TeamInvite;
import sodresoftwares.barbearia.model.team.TeamMember;
import sodresoftwares.barbearia.model.team.TeamRole;
import sodresoftwares.barbearia.model.user.User;
import sodresoftwares.barbearia.repositories.billing.SubscriptionRepository;
import sodresoftwares.barbearia.repositories.business.BusinessRepository;
import sodresoftwares.barbearia.repositories.queue.QueueSessionRepository;
import sodresoftwares.barbearia.repositories.team.TeamInviteRepository;
import sodresoftwares.barbearia.repositories.team.TeamMemberRepository;
import sodresoftwares.barbearia.services.queue.QueueCacheService;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DashboardService Tests")
class DashboardServiceTest {

    @Mock
    private TeamMemberRepository teamMemberRepository;

    @Mock
    private QueueSessionRepository queueSessionRepository;

    @Mock
    private TeamInviteRepository teamInviteRepository;

    @Mock
    private BusinessRepository businessRepository;

    @Mock
    private QueueCacheService queueCacheService;

    @Mock
    private QueueMapper queueMapper;

    // Novo Mock
    @Mock
    private SubscriptionRepository subscriptionRepository;

    @InjectMocks
    private DashboardService dashboardService;

    private User mockUser;
    private final String LOGGED_USER_ID = "user-123";
    private final String BUSINESS_ID = "biz-123";
    private final String SESSION_ID = "session-123";

    private Business business;
    private TeamMember teamMember;
    private QueueSession queueSession;

    @BeforeEach
    void setUp() {
        business = Business.builder()
                .id(BUSINESS_ID)
                .name("Barbearia Teste")
                .build();

        mockUser = User.builder()
                .id(LOGGED_USER_ID)
                .login("ze@test.com")
                .name("Zé Barbeiro")
                .emailVerified(true)
                .build();

        teamMember = TeamMember.builder()
                .id("member-123")
                .name("Zé Barbeiro")
                .role(TeamRole.OWNER)
                .business(business)
                .user(mockUser)
                .isActive(true)
                .build();

        queueSession = QueueSession.builder()
                .id(SESSION_ID)
                .business(business)
                .ticketCode("CODE99")
                .isActive(true)
                .toleranceMinutes(15)
                .build();
    }

    // ==================== DASHBOARD PROFESSIONAL ====================

    @Test
    @DisplayName("Should return empty dashboard when user is not associated with any team and has no business")
    void getProfessionalDashboard_NoTeamMember() {
        // Arrange
        when(teamMemberRepository.findActiveByUserIdWithBusiness(LOGGED_USER_ID)).thenReturn(Optional.empty());
        when(businessRepository.findByUserId(LOGGED_USER_ID)).thenReturn(Optional.empty());

        TeamInvite mockInvite = TeamInvite.builder()
                .id("inv-1").business(business).email("ze@test.com").role(TeamRole.STAFF).status(InviteStatus.PENDING).expiresAt(Instant.now()).build();

        when(teamInviteRepository.findAllByEmailAndStatusWithBusiness("ze@test.com", InviteStatus.PENDING))
                .thenReturn(List.of(mockInvite));

        // Act
        BusinessDashboardDTO result = dashboardService.getProfessionalDashboard(mockUser);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.businessId()).isNull();
        assertThat(result.businessIsActive()).isNull();
        assertThat(result.pendingInvites()).hasSize(1);
        assertThat(result.subscriptionStatus()).isNull();
    }

    @Test
    @DisplayName("Should return dashboard with businessIsActive false and subscription when user owns an inactive business")
    void getProfessionalDashboard_InactiveBusiness() {
        // Arrange
        when(teamMemberRepository.findActiveByUserIdWithBusiness(LOGGED_USER_ID)).thenReturn(Optional.empty());

        Business inactiveBusiness = Business.builder().id("biz-inactive").name("Inativa").isActive(false).build();
        when(businessRepository.findByUserId(LOGGED_USER_ID)).thenReturn(Optional.of(inactiveBusiness));

        Subscription mockSub = Subscription.builder()
                .status(SubscriptionStatus.SUSPENDED)
                .currentPeriodEnd(LocalDate.now().minusDays(1))
                .build();
        when(subscriptionRepository.findByBusinessId("biz-inactive")).thenReturn(Optional.of(mockSub));

        // Act
        BusinessDashboardDTO result = dashboardService.getProfessionalDashboard(mockUser);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.businessId()).isEqualTo("biz-inactive");
        assertThat(result.businessName()).isEqualTo("Inativa");
        assertThat(result.businessIsActive()).isFalse();
        assertThat(result.subscriptionStatus()).isEqualTo(SubscriptionStatus.SUSPENDED);
    }

    @Test
    @DisplayName("Should map team members correctly even if they are Shadow Profiles (user is null)")
    void getProfessionalDashboard_ShadowProfileMapping() {
        // Arrange
        TeamMember shadowMember = TeamMember.builder()
                .id("shadow-123")
                .name("Fantasma Silva")
                .user(null)
                .role(TeamRole.STAFF)
                .business(business)
                .isActive(true)
                .build();

        when(teamInviteRepository.findAllByEmailAndStatusWithBusiness(mockUser.getLogin(), InviteStatus.PENDING))
                .thenReturn(List.of());
        when(teamMemberRepository.findActiveByUserIdWithBusiness(LOGGED_USER_ID)).thenReturn(Optional.of(teamMember));
        when(teamMemberRepository.findAllByBusinessIdAndIsActiveTrueWithUser(BUSINESS_ID))
                .thenReturn(List.of(teamMember, shadowMember));
        when(queueSessionRepository.findByBusinessIdWithBusiness(BUSINESS_ID)).thenReturn(Optional.empty());
        when(subscriptionRepository.findByBusinessId(BUSINESS_ID)).thenReturn(Optional.empty());

        // Act
        BusinessDashboardDTO result = dashboardService.getProfessionalDashboard(mockUser);

        // Assert
        assertThat(result.team()).hasSize(2);
        assertThat(result.team().get(1).name()).isEqualTo("Fantasma Silva");
        assertThat(result.emailVerified()).isTrue();
        assertThat(result.subscriptionStatus()).isNull();
    }

    @Test
    @DisplayName("Should return business data but no session data when queue session does not exist")
    void getProfessionalDashboard_NoActiveSession() {
        // Arrange
        when(teamInviteRepository.findAllByEmailAndStatusWithBusiness(mockUser.getLogin(), InviteStatus.PENDING))
                .thenReturn(List.of());
        when(teamMemberRepository.findActiveByUserIdWithBusiness(LOGGED_USER_ID)).thenReturn(Optional.of(teamMember));
        when(teamMemberRepository.findAllByBusinessIdAndIsActiveTrueWithUser(BUSINESS_ID)).thenReturn(List.of(teamMember));
        when(queueSessionRepository.findByBusinessIdWithBusiness(BUSINESS_ID)).thenReturn(Optional.empty());

        Subscription mockSub = Subscription.builder()
                .status(SubscriptionStatus.TRIAL)
                .currentPeriodEnd(LocalDate.now().plusDays(7))
                .build();
        when(subscriptionRepository.findByBusinessId(BUSINESS_ID)).thenReturn(Optional.of(mockSub));

        // Act
        BusinessDashboardDTO result = dashboardService.getProfessionalDashboard(mockUser);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.businessId()).isEqualTo(BUSINESS_ID);
        assertThat(result.businessName()).isEqualTo("Barbearia Teste");
        assertThat(result.loggedMemberRole()).isEqualTo(TeamRole.OWNER);
        assertThat(result.sessionId()).isNull();
        assertThat(result.activeQueue()).isEmpty();
        assertThat(result.pendingInvites()).isEmpty();
        assertThat(result.emailVerified()).isTrue();
        assertThat(result.subscriptionStatus()).isEqualTo(SubscriptionStatus.TRIAL);

        verifyNoInteractions(queueCacheService, queueMapper);
    }

    @Test
    @DisplayName("Should return full dashboard data when team member and active session exist")
    void getProfessionalDashboard_FullData() {
        QueueEntryResponseDTO mockDto = new QueueEntryResponseDTO(
                "entry-123", 1, "user-1", "Cliente", "Corte",
                QueueEntryStatus.WAITING, null, null, null, Instant.now(), null, 15
        );

        // Arrange
        when(teamInviteRepository.findAllByEmailAndStatusWithBusiness(mockUser.getLogin(), InviteStatus.PENDING))
                .thenReturn(List.of());
        when(teamMemberRepository.findActiveByUserIdWithBusiness(LOGGED_USER_ID)).thenReturn(Optional.of(teamMember));
        when(teamMemberRepository.findAllByBusinessIdAndIsActiveTrueWithUser(BUSINESS_ID)).thenReturn(List.of(teamMember));
        when(queueSessionRepository.findByBusinessIdWithBusiness(BUSINESS_ID)).thenReturn(Optional.of(queueSession));

        Subscription mockSub = Subscription.builder()
                .status(SubscriptionStatus.ACTIVE)
                .currentPeriodEnd(LocalDate.now().plusDays(30))
                .build();
        when(subscriptionRepository.findByBusinessId(BUSINESS_ID)).thenReturn(Optional.of(mockSub));

        List<QueueEntryResponseDTO> mockDtos = List.of(mockDto);
        when(queueCacheService.getActiveEntriesDTO(SESSION_ID)).thenReturn(mockDtos);

        // Act
        BusinessDashboardDTO result = dashboardService.getProfessionalDashboard(mockUser);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.businessId()).isEqualTo(BUSINESS_ID);
        assertThat(result.sessionId()).isEqualTo(SESSION_ID);
        assertThat(result.activeQueue()).hasSize(1);
        assertThat(result.emailVerified()).isTrue();
        assertThat(result.subscriptionStatus()).isEqualTo(SubscriptionStatus.ACTIVE);

        verify(queueCacheService).getActiveEntriesDTO(SESSION_ID);
    }
}