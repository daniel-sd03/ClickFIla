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
import sodresoftwares.barbearia.dto.team.QuickCreateMemberDTO;
import sodresoftwares.barbearia.infra.exception.AppException;
import sodresoftwares.barbearia.model.business.Business;
import sodresoftwares.barbearia.model.team.TeamMember;
import sodresoftwares.barbearia.model.team.TeamRole;
import sodresoftwares.barbearia.repositories.queue.QueueEntryRepository;
import sodresoftwares.barbearia.repositories.team.TeamMemberRepository;
import sodresoftwares.barbearia.services.business.BusinessService;
import sodresoftwares.barbearia.services.team.TeamMemberService;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TeamMemberService Tests")
class TeamMemberServiceTest {

    @Mock
    private TeamMemberRepository teamMemberRepository;

    @Mock
    private QueueEntryRepository queueEntryRepository;

    @Mock
    private BusinessService businessService;

    @InjectMocks
    private TeamMemberService teamMemberService;

    private Business business;
    private TeamMember ownerMember;
    private TeamMember staffMember;

    @BeforeEach
    void setUp() {
        business = Business.builder().id("biz-123").name("Barbearia Teste").build();

        ownerMember = TeamMember.builder()
                .id("owner-member-id")
                .business(business)
                .name("Dono da Barbearia")
                .role(TeamRole.OWNER)
                .isActive(true)
                .build();

        staffMember = TeamMember.builder()
                .id("staff-member-id")
                .business(business)
                .name("Funcionário Teste")
                .role(TeamRole.STAFF)
                .isActive(true)
                .build();
    }

    // ====================== QUICK CREATE MEMBER TESTS =====================

    @Test
    @DisplayName("Should create a new team member successfully when requested by owner")
    void quickCreateMember_Success() {
        QuickCreateMemberDTO dto = new QuickCreateMemberDTO("Novo Barbeiro");

        when(teamMemberRepository.findActiveByUserIdWithBusiness("logged-owner-id")).thenReturn(Optional.of(ownerMember));

        teamMemberService.quickCreateMember("logged-owner-id", dto);

        ArgumentCaptor<TeamMember> memberCaptor = ArgumentCaptor.forClass(TeamMember.class);
        verify(teamMemberRepository).save(memberCaptor.capture());

        TeamMember savedMember = memberCaptor.getValue();

        assertThat(savedMember.getName()).isEqualTo("Novo Barbeiro");
        assertThat(savedMember.getBusiness()).isEqualTo(business);
        assertThat(savedMember.getRole()).isEqualTo(TeamRole.STAFF);
        assertThat(savedMember.getIsActive()).isTrue();
        assertThat(savedMember.getUser()).isNull();
    }

    @Test
    @DisplayName("Should throw exception when trying to create member and logged user is not found")
    void quickCreateMember_UserNotFound() {
        QuickCreateMemberDTO dto = new QuickCreateMemberDTO("Novo Barbeiro");

        when(teamMemberRepository.findActiveByUserIdWithBusiness("unknown-id")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> teamMemberService.quickCreateMember("unknown-id", dto))
                .isInstanceOf(AppException.class)
                .hasMessage("User is not associated with any team/business.")
                .extracting(e -> ((AppException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND);

        verify(teamMemberRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw exception when trying to create member and logged user is not the owner")
    void quickCreateMember_NotOwner() {
        QuickCreateMemberDTO dto = new QuickCreateMemberDTO("Novo Barbeiro");

        ownerMember.setRole(TeamRole.STAFF);
        when(teamMemberRepository.findActiveByUserIdWithBusiness("logged-staff-id")).thenReturn(Optional.of(ownerMember));

        assertThatThrownBy(() -> teamMemberService.quickCreateMember("logged-staff-id", dto))
                .isInstanceOf(AppException.class)
                .hasMessage("Only the business owner can perform this action.")
                .extracting(e -> ((AppException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN);

        verify(teamMemberRepository, never()).save(any());
    }

    // ======================= REMOVE MEMBER TESTS ======================

    @Test
    @DisplayName("Should deactivate member successfully when requested by owner")
    void removeMember_Success() {
        when(teamMemberRepository.findActiveByUserIdWithBusiness("logged-owner-id")).thenReturn(Optional.of(ownerMember));
        when(teamMemberRepository.findById("staff-member-id")).thenReturn(Optional.of(staffMember));

        teamMemberService.removeMember("logged-owner-id", "staff-member-id");

        assertThat(staffMember.getIsActive()).isFalse();
        verify(teamMemberRepository).save(staffMember);
    }

    @Test
    @DisplayName("Should throw exception when trying to remove a non-existent member")
    void removeMember_MemberNotFound() {
        when(teamMemberRepository.findActiveByUserIdWithBusiness("logged-owner-id")).thenReturn(Optional.of(ownerMember));
        when(teamMemberRepository.findById("unknown-member-id")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> teamMemberService.removeMember("logged-owner-id", "unknown-member-id"))
                .isInstanceOf(AppException.class)
                .hasMessage("Team member not found.")
                .extracting(e -> ((AppException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND);

        verify(teamMemberRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw exception when trying to remove a member from another business")
    void removeMember_WrongBusiness() {
        Business otherBusiness = Business.builder().id("other-biz-id").name("Outra Barbearia").build();
        staffMember.setBusiness(otherBusiness);

        when(teamMemberRepository.findActiveByUserIdWithBusiness("logged-owner-id")).thenReturn(Optional.of(ownerMember));
        when(teamMemberRepository.findById("staff-member-id")).thenReturn(Optional.of(staffMember));

        assertThatThrownBy(() -> teamMemberService.removeMember("logged-owner-id", "staff-member-id"))
                .isInstanceOf(AppException.class)
                .hasMessage("This member does not belong to your business.")
                .extracting(e -> ((AppException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN);

        verify(teamMemberRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw exception when owner tries to remove themselves")
    void removeMember_CannotRemoveOwner() {
        when(teamMemberRepository.findActiveByUserIdWithBusiness("logged-owner-id")).thenReturn(Optional.of(ownerMember));
        when(teamMemberRepository.findById("owner-member-id")).thenReturn(Optional.of(ownerMember));

        assertThatThrownBy(() -> teamMemberService.removeMember("logged-owner-id", "owner-member-id"))
                .isInstanceOf(AppException.class)
                .hasMessage("The business owner cannot be removed.")
                .extracting(e -> ((AppException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);

        assertThat(ownerMember.getIsActive()).isTrue();
        verify(teamMemberRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw CONFLICT when trying to remove a member currently serving a client")
    void removeMember_MemberInActiveService() {
        when(teamMemberRepository.findActiveByUserIdWithBusiness("logged-owner-id")).thenReturn(Optional.of(ownerMember));
        when(teamMemberRepository.findById("staff-member-id")).thenReturn(Optional.of(staffMember));
        when(queueEntryRepository.hasActiveServiceByMemberId("staff-member-id")).thenReturn(true);

        assertThatThrownBy(() -> teamMemberService.removeMember("logged-owner-id", "staff-member-id"))
                .isInstanceOf(AppException.class)
                .hasMessage("Cannot remove a member who is currently calling or serving a client.")
                .extracting(e -> ((AppException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT);

        verify(teamMemberRepository, never()).save(any());
    }

    // ==================== LEAVE TEAM ====================

    @Test
    @DisplayName("Should set isActive to false when a STAFF member leaves the team voluntarily")
    void leaveTeam_Success() {
        // Arrange
        when(teamMemberRepository.findActiveByUserId("logged-staff-id")).thenReturn(Optional.of(staffMember));

        // Act
        teamMemberService.leaveTeam("logged-staff-id");

        // Assert
        assertThat(staffMember.getIsActive()).isFalse();
        verify(teamMemberRepository).save(staffMember);
    }

    @Test
    @DisplayName("Should throw exception if an OWNER tries to leave the team")
    void leaveTeam_OwnerCannotLeave() {
        // Arrange
        when(teamMemberRepository.findActiveByUserId("logged-owner-id")).thenReturn(Optional.of(ownerMember));

        // Act & Assert
        assertThatThrownBy(() -> teamMemberService.leaveTeam("logged-owner-id"))
                .isInstanceOf(AppException.class)
                .hasMessage("The owner cannot leave the team. You must delete or transfer the business.")
                .extracting(e -> ((AppException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);

        verify(teamMemberRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw CONFLICT if a STAFF member tries to leave while serving a client")
    void leaveTeam_MemberInActiveService() {
        when(teamMemberRepository.findActiveByUserId("logged-staff-id")).thenReturn(Optional.of(staffMember));
        when(queueEntryRepository.hasActiveServiceByMemberId("staff-member-id")).thenReturn(true);

        assertThatThrownBy(() -> teamMemberService.leaveTeam("logged-staff-id"))
                .isInstanceOf(AppException.class)
                .hasMessage("Finish your current client service before leaving the team.")
                .extracting(e -> ((AppException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT);

        verify(teamMemberRepository, never()).save(any());
    }

    // ==================== DEACTIVATE PROFESSIONAL LINKS ====================

    @Test
    @DisplayName("Should call businessService.deactivateBusiness when user is an OWNER")
    void deactivateProfessionalLinksForUser_Owner() {
        when(teamMemberRepository.findActiveByUserIdWithBusiness("logged-owner-id")).thenReturn(Optional.of(ownerMember));

        teamMemberService.deactivateProfessionalLinksForUser("logged-owner-id");

        verify(businessService).deactivateBusiness(business);
        verify(teamMemberRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should deactivate member when user is STAFF and not in active service")
    void deactivateProfessionalLinksForUser_StaffSuccess() {
        when(teamMemberRepository.findActiveByUserIdWithBusiness("logged-staff-id")).thenReturn(Optional.of(staffMember));
        when(queueEntryRepository.hasActiveServiceByMemberId("staff-member-id")).thenReturn(false);

        teamMemberService.deactivateProfessionalLinksForUser("logged-staff-id");

        assertThat(staffMember.getIsActive()).isFalse();
        verify(teamMemberRepository).save(staffMember);
    }

    @Test
    @DisplayName("Should throw CONFLICT when STAFF user is in active service during downgrade")
    void deactivateProfessionalLinksForUser_StaffInActiveService() {
        when(teamMemberRepository.findActiveByUserIdWithBusiness("logged-staff-id")).thenReturn(Optional.of(staffMember));
        when(queueEntryRepository.hasActiveServiceByMemberId("staff-member-id")).thenReturn(true);

        assertThatThrownBy(() -> teamMemberService.deactivateProfessionalLinksForUser("logged-staff-id"))
                .isInstanceOf(AppException.class)
                .hasMessage("Finish your current client service before changing to a client account.")
                .extracting(e -> ((AppException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT);

        verify(teamMemberRepository, never()).save(any());
    }
}