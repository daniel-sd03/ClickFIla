package sodresoftwares.barbearia.services;

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
import sodresoftwares.barbearia.model.business.Business;
import sodresoftwares.barbearia.model.queue.QueueEntryStatus;
import sodresoftwares.barbearia.model.queue.QueueSession;
import sodresoftwares.barbearia.model.team.InviteStatus;
import sodresoftwares.barbearia.model.team.TeamInvite;
import sodresoftwares.barbearia.model.team.TeamMember;
import sodresoftwares.barbearia.model.team.TeamRole;
import sodresoftwares.barbearia.model.user.User;
import sodresoftwares.barbearia.repositories.queue.QueueSessionRepository;
import sodresoftwares.barbearia.repositories.team.TeamInviteRepository;
import sodresoftwares.barbearia.repositories.team.TeamMemberRepository;
import sodresoftwares.barbearia.services.business.DashboardService;
import sodresoftwares.barbearia.services.queue.QueueCacheService;

import java.time.Instant;
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
    private QueueCacheService queueCacheService;

    @Mock
    private QueueMapper queueMapper;

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
    @DisplayName("Should return empty dashboard with pending invites when user is not associated with any team")
    void getProfessionalDashboard_NoTeamMember() {
        // Arrange
        when(teamMemberRepository.findActiveByUserIdWithBusiness(LOGGED_USER_ID)).thenReturn(Optional.empty());

        TeamInvite mockInvite = TeamInvite.builder()
                .id("inv-1")
                .business(business)
                .email("ze@test.com")
                .role(TeamRole.STAFF)
                .status(InviteStatus.PENDING)
                .expiresAt(Instant.now())
                .build();

        when(teamInviteRepository.findAllByEmailAndStatusWithBusiness("ze@test.com", InviteStatus.PENDING))
                .thenReturn(List.of(mockInvite));

        // Act
        BusinessDashboardDTO result = dashboardService.getProfessionalDashboard(mockUser);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.businessId()).isNull();
        assertThat(result.loggedMemberRole()).isNull();
        assertThat(result.pendingInvites()).hasSize(1);
        assertThat(result.pendingInvites().get(0).id()).isEqualTo("inv-1");
        assertThat(result.emailVerified()).isTrue();

        verifyNoInteractions(queueSessionRepository, queueCacheService, queueMapper);
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

        when(teamMemberRepository.findActiveByUserIdWithBusiness(LOGGED_USER_ID)).thenReturn(Optional.of(teamMember));
        when(teamMemberRepository.findAllByBusinessIdAndIsActiveTrueWithUser(BUSINESS_ID))
                .thenReturn(List.of(teamMember, shadowMember));
        when(queueSessionRepository.findByBusinessIdWithBusiness(BUSINESS_ID)).thenReturn(Optional.empty());

        // Act
        BusinessDashboardDTO result = dashboardService.getProfessionalDashboard(mockUser);

        // Assert
        assertThat(result.team()).hasSize(2);
        assertThat(result.team().get(1).name()).isEqualTo("Fantasma Silva");
        assertThat(result.emailVerified()).isTrue();
    }

    @Test
    @DisplayName("Should return business data but no session data when queue session does not exist")
    void getProfessionalDashboard_NoActiveSession() {
        // Arrange
        when(teamMemberRepository.findActiveByUserIdWithBusiness(LOGGED_USER_ID)).thenReturn(Optional.of(teamMember));
        when(teamMemberRepository.findAllByBusinessIdAndIsActiveTrueWithUser(BUSINESS_ID)).thenReturn(List.of(teamMember));
        when(queueSessionRepository.findByBusinessIdWithBusiness(BUSINESS_ID)).thenReturn(Optional.empty());

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
        when(teamMemberRepository.findActiveByUserIdWithBusiness(LOGGED_USER_ID)).thenReturn(Optional.of(teamMember));
        when(teamMemberRepository.findAllByBusinessIdAndIsActiveTrueWithUser(BUSINESS_ID)).thenReturn(List.of(teamMember));
        when(queueSessionRepository.findByBusinessIdWithBusiness(BUSINESS_ID)).thenReturn(Optional.of(queueSession));

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

        verify(queueCacheService).getActiveEntriesDTO(SESSION_ID);
    }
}