package sodresoftwares.barbearia.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import sodresoftwares.barbearia.dto.team.CreateTeamInviteDTO;
import sodresoftwares.barbearia.dto.team.TeamInviteResponseDTO;
import sodresoftwares.barbearia.infra.exception.AppException;
import sodresoftwares.barbearia.model.business.Business;
import sodresoftwares.barbearia.model.team.InviteStatus;
import sodresoftwares.barbearia.model.team.TeamInvite;
import sodresoftwares.barbearia.model.team.TeamMember;
import sodresoftwares.barbearia.model.team.TeamRole;
import sodresoftwares.barbearia.model.user.User;
import sodresoftwares.barbearia.repositories.team.TeamInviteRepository;
import sodresoftwares.barbearia.repositories.team.TeamMemberRepository;
import sodresoftwares.barbearia.repositories.user.UserRepository;
import sodresoftwares.barbearia.services.team.TeamInviteService;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TeamInviteService Tests")
class TeamInviteServiceTest {

    @Mock
    private TeamInviteRepository teamInviteRepository;
    @Mock
    private TeamMemberRepository teamMemberRepository;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private TeamInviteService teamInviteService;

    private User ownerUser;
    private User invitedUser;
    private Business business;
    private TeamMember ownerMember;
    private TeamInvite validInvite;

    @BeforeEach
    void setUp() {
        ownerUser = User.builder().id("owner-id").name("Owner").login("owner@test.com").build();
        invitedUser = User.builder().id("invited-id").name("Invited").login("invited@test.com").build();

        business = Business.builder()
                .id("biz-id")
                .name("Barbearia Teste")
                .isActive(true)
                .build();

        ownerMember = TeamMember.builder()
                .id("member-id")
                .user(ownerUser)
                .business(business)
                .role(TeamRole.OWNER)
                .build();

        validInvite = TeamInvite.builder()
                .id("invite-id")
                .business(business)
                .email("invited@test.com")
                .role(TeamRole.STAFF)
                .status(InviteStatus.PENDING)
                .expiresAt(Instant.now().plus(1, ChronoUnit.DAYS))
                .build();
    }

    // =================== GET PENDING INVITES TESTS  ================================

    @Test
    @DisplayName("Should return a list of pending invites mapped to DTO")
    void getMyPendingInvites_Success() {
        when(teamInviteRepository.findAllByEmailAndStatusWithBusiness("invited@test.com", InviteStatus.PENDING))
                .thenReturn(List.of(validInvite));

        List<TeamInviteResponseDTO> result = teamInviteService.getMyPendingInvites("invited@test.com");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo("invite-id");
        assertThat(result.get(0).businessId()).isEqualTo("biz-id");
    }

    // ======================== SEND INVITE TESTS =========================

    @Test
    @DisplayName("Should successfully send an invite when owner requests")
    void sendInvite_Success() {
        CreateTeamInviteDTO dto = new CreateTeamInviteDTO("new@test.com");

        when(teamMemberRepository.findActiveByUserIdWithBusiness("owner-id")).thenReturn(Optional.of(ownerMember));
        when(teamInviteRepository.existsByEmailAndBusinessIdAndStatus("new@test.com", "biz-id", InviteStatus.PENDING))
                .thenReturn(false);

        teamInviteService.sendInvite("owner-id", dto);

        ArgumentCaptor<TeamInvite> inviteCaptor = ArgumentCaptor.forClass(TeamInvite.class);
        verify(teamInviteRepository).save(inviteCaptor.capture());

        TeamInvite savedInvite = inviteCaptor.getValue();

        assertThat(savedInvite.getEmail()).isEqualTo("new@test.com");
        assertThat(savedInvite.getRole()).isEqualTo(TeamRole.STAFF);
        assertThat(savedInvite.getStatus()).isEqualTo(InviteStatus.PENDING);
        assertThat(savedInvite.getBusiness().getId()).isEqualTo("biz-id");
    }

    @Test
    @DisplayName("Should throw exception if a STAFF tries to send an invite")
    void sendInvite_FailsWhenNotOwner() {
        ownerMember.setRole(TeamRole.STAFF); // Changing role to trigger error
        CreateTeamInviteDTO dto = new CreateTeamInviteDTO("new@test.com");

        when(teamMemberRepository.findActiveByUserIdWithBusiness("owner-id")).thenReturn(Optional.of(ownerMember));

        assertThatThrownBy(() -> teamInviteService.sendInvite("owner-id", dto))
                .isInstanceOf(AppException.class)
                .hasMessage("Only the business owner can perform this action.")
                .extracting(e -> ((AppException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("Should throw exception if invite is already pending")
    void sendInvite_FailsWhenAlreadyInvited() {
        CreateTeamInviteDTO dto = new CreateTeamInviteDTO("new@test.com");

        when(teamMemberRepository.findActiveByUserIdWithBusiness("owner-id")).thenReturn(Optional.of(ownerMember));
        when(teamInviteRepository.existsByEmailAndBusinessIdAndStatus("new@test.com", "biz-id", InviteStatus.PENDING))
                .thenReturn(true); // Simulating existing invite

        assertThatThrownBy(() -> teamInviteService.sendInvite("owner-id", dto))
                .isInstanceOf(AppException.class)
                .hasMessage("There is already a pending invite for this email.")
                .extracting(e -> ((AppException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT);
    }

    // ====================== ACCEPT INVITE TESTS ======================

    @Test
    @DisplayName("Should accept invite and create a new TeamMember")
    void acceptInvite_Success() {
        when(teamMemberRepository.existsByUserIdAndIsActiveTrue("invited-id")).thenReturn(false);
        when(teamInviteRepository.findByIdWithBusiness("invite-id")).thenReturn(Optional.of(validInvite));
        when(userRepository.findById("invited-id")).thenReturn(Optional.of(invitedUser));

        teamInviteService.acceptInvite("invite-id", "invited-id", "invited@test.com");

        // Assert Invite Status changed
        assertThat(validInvite.getStatus()).isEqualTo(InviteStatus.ACCEPTED);
        verify(teamInviteRepository).save(validInvite);

        // Assert new member was created
        ArgumentCaptor<TeamMember> memberCaptor = ArgumentCaptor.forClass(TeamMember.class);
        verify(teamMemberRepository).save(memberCaptor.capture());

        TeamMember savedMember = memberCaptor.getValue();

        assertThat(savedMember.getUser().getId()).isEqualTo("invited-id");
        assertThat(savedMember.getBusiness().getId()).isEqualTo("biz-id");
        assertThat(savedMember.getRole()).isEqualTo(TeamRole.STAFF);
        assertThat(savedMember.getIsActive()).isTrue();
    }

    @Test
    @DisplayName("Should throw exception if user is already in a team")
    void acceptInvite_FailsWhenAlreadyInTeam() {
        when(teamMemberRepository.existsByUserIdAndIsActiveTrue("invited-id")).thenReturn(true);

        assertThatThrownBy(() -> teamInviteService.acceptInvite("invite-id", "invited-id", "invited@test.com"))
                .isInstanceOf(AppException.class)
                .hasMessage("You are already part of a team. Leave your current team to accept a new invite.")
                .extracting(e -> ((AppException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("Should throw exception if invite is expired")
    void acceptInvite_FailsWhenExpired() {
        validInvite.setExpiresAt(Instant.now().minus(1, ChronoUnit.DAYS)); // Expired yesterday

        when(teamMemberRepository.existsByUserIdAndIsActiveTrue("invited-id")).thenReturn(false);
        when(teamInviteRepository.findByIdWithBusiness("invite-id")).thenReturn(Optional.of(validInvite));

        assertThatThrownBy(() -> teamInviteService.acceptInvite("invite-id", "invited-id", "invited@test.com"))
                .isInstanceOf(AppException.class)
                .hasMessage("This invite has expired.")
                .extracting(e -> ((AppException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);

        // Assert status was updated to EXPIRED
        assertThat(validInvite.getStatus()).isEqualTo(InviteStatus.EXPIRED);
        verify(teamInviteRepository).save(validInvite);
    }

    @Test
    @DisplayName("Should throw exception if email does not match")
    void acceptInvite_FailsWhenWrongEmail() {
        when(teamMemberRepository.existsByUserIdAndIsActiveTrue("invited-id")).thenReturn(false);
        when(teamInviteRepository.findByIdWithBusiness("invite-id")).thenReturn(Optional.of(validInvite));

        assertThatThrownBy(() -> teamInviteService.acceptInvite("invite-id", "invited-id", "HACKER@test.com"))
                .isInstanceOf(AppException.class)
                .hasMessage("This invite does not belong to you.")
                .extracting(e -> ((AppException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ====================== DECLINE INVITE TESTS ======================

    @Test
    @DisplayName("Should decline invite successfully")
    void declineInvite_Success() {
        when(teamInviteRepository.findByIdWithBusiness("invite-id")).thenReturn(Optional.of(validInvite));

        teamInviteService.declineInvite("invite-id", "invited@test.com");

        assertThat(validInvite.getStatus()).isEqualTo(InviteStatus.DECLINED);
        verify(teamInviteRepository).save(validInvite);
        verify(teamMemberRepository, never()).save(any(TeamMember.class));
    }
}