package sodresoftwares.barbearia.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import sodresoftwares.barbearia.dto.queue.QueueEntryResponseDTO;
import sodresoftwares.barbearia.dto.queue.QueueSessionBusinessResponseDTO;
import sodresoftwares.barbearia.dto.queue.UpdateQueueSessionDTO;
import sodresoftwares.barbearia.infra.exception.AppException;
import sodresoftwares.barbearia.model.business.Business;
import sodresoftwares.barbearia.model.queue.QueueEntry;
import sodresoftwares.barbearia.model.queue.QueueEntryStatus;
import sodresoftwares.barbearia.model.queue.QueueSession;
import sodresoftwares.barbearia.model.team.TeamMember;
import sodresoftwares.barbearia.model.team.TeamRole;
import sodresoftwares.barbearia.model.user.User;
import sodresoftwares.barbearia.model.user.UserRole;
import sodresoftwares.barbearia.repositories.queue.QueueSessionRepository;
import sodresoftwares.barbearia.repositories.team.TeamMemberRepository;
import sodresoftwares.barbearia.services.queue.QueueCacheService;
import sodresoftwares.barbearia.services.queue.QueueSessionService;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("QueueSessionService Tests")
class QueueSessionServiceTest {
    @Mock
    private QueueSessionRepository queueSessionRepository;

    @Mock
    private TeamMemberRepository teamMemberRepository;

    @Mock
    private QueueCacheService queueCacheService;

    @InjectMocks
    private QueueSessionService queueSessionService;

    private Business business;
    private TeamMember ownerMember;
    private QueueSession existingSession;
    private QueueEntry activeEntry;

    private final String PROF_USER_ID = "prof-user-123";
    private final String CUSTOMER_USER_ID = "customer-user-123";

    @BeforeEach
    void setUp() {
        User professionalUser = User.builder()
                .id(PROF_USER_ID)
                .name("Barbeiro Zé")
                .role(UserRole.PROFESSIONAL)
                .build();

        User customerUser = User.builder()
                .id(CUSTOMER_USER_ID)
                .name("Cliente João")
                .role(UserRole.USER)
                .build();

        business = Business.builder()
                .id("biz-123")
                .name("Barbearia do Zé")
                .isActive(true)
                .build();

        ownerMember = TeamMember.builder()
                .id("member-123")
                .business(business)
                .user(professionalUser)
                .role(TeamRole.OWNER)
                .build();

        existingSession = QueueSession.builder()
                .id("session-123")
                .business(business)
                .prefix("BARB")
                .ticketCode("BARB1234")
                .isActive(false)
                .toleranceMinutes(5)
                .build();

        activeEntry = QueueEntry.builder()
                .id("entry-123")
                .queueSession(existingSession)
                .user(customerUser)
                .serviceName("Corte Navalhado")
                .status(QueueEntryStatus.WAITING)
                .joinedAt(Instant.now())
                .build();
    }

    // ==================== CREATE QUEUE SESSION TESTS ====================

    @Test
    @DisplayName("Should create queue session and generate prefix based on business name")
    void testCreateQueueSession_Success_PrefixGeneration() {
        when(teamMemberRepository.findActiveByUserIdWithBusiness(PROF_USER_ID)).thenReturn(Optional.of(ownerMember));
        when(queueSessionRepository.existsByBusinessId(business.getId())).thenReturn(false);
        when(queueSessionRepository.existsByTicketCode(anyString())).thenReturn(false);
        when(queueSessionRepository.save(any(QueueSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        QueueSessionBusinessResponseDTO result = queueSessionService.createQueueSession(PROF_USER_ID);

        assertThat(result).isNotNull();
        assertThat(result.isActive()).isFalse();
        assertThat(result.ticketCode()).startsWith("BARB");

        verify(queueSessionRepository).save(any(QueueSession.class));
    }

    @Test
    @DisplayName("Should throw conflict exception when business already has a queue")
    void testCreateQueueSession_AlreadyExists() {
        when(teamMemberRepository.findActiveByUserIdWithBusiness(PROF_USER_ID)).thenReturn(Optional.of(ownerMember));
        when(queueSessionRepository.existsByBusinessId(business.getId())).thenReturn(true);

        assertThatThrownBy(() -> queueSessionService.createQueueSession(PROF_USER_ID))
                .isInstanceOf(AppException.class)
                .hasMessage("This business already has a queue session.")
                .extracting(e -> ((AppException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT);

        verify(queueSessionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw exception when user is not associated with any team")
    void testCreateQueueSession_TeamMemberNotFound() {
        when(teamMemberRepository.findActiveByUserIdWithBusiness(PROF_USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> queueSessionService.createQueueSession(PROF_USER_ID))
                .isInstanceOf(AppException.class)
                .hasMessage("User is not associated with any team/business.")
                .extracting(e -> ((AppException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND);

        verify(queueSessionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should generate a new ticket code if a collision is detected during creation")
    void testCreateQueueSession_CollisionLoop() {
        // Arrange
        when(teamMemberRepository.findActiveByUserIdWithBusiness(PROF_USER_ID)).thenReturn(Optional.of(ownerMember));
        when(queueSessionRepository.existsByBusinessId(business.getId())).thenReturn(false);
        when(queueSessionRepository.existsByTicketCode(anyString())).thenReturn(true, false);
        when(queueSessionRepository.save(any(QueueSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        queueSessionService.createQueueSession(PROF_USER_ID);

        // Assert
        verify(queueSessionRepository, times(2)).existsByTicketCode(anyString());
    }

    @Test
    @DisplayName("Should throw Forbidden (403) when STAFF tries to create a queue session")
    void testCreateQueueSession_ForbiddenNotOwner() {
        // Arrange - Setup a staff member
        TeamMember staffMember = TeamMember.builder()
                .business(business)
                .role(TeamRole.STAFF)
                .build();

        when(teamMemberRepository.findActiveByUserIdWithBusiness(PROF_USER_ID)).thenReturn(Optional.of(staffMember));

        // Act & Assert
        assertThatThrownBy(() -> queueSessionService.createQueueSession(PROF_USER_ID))
                .isInstanceOf(AppException.class)
                .hasMessage("Only the business owner can perform this action.")
                .extracting(e -> ((AppException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN);

        verify(queueSessionRepository, never()).save(any());
    }

    // ====================  UPDATE QUEUE STATUS TESTS ====================

    @Test
    @DisplayName("Should update an existing queue session successfully when user is OWNER")
    void testUpdateQueueStatus_Success() {
        when(queueSessionRepository.findByOwnerUserId(PROF_USER_ID)).thenReturn(Optional.of(existingSession));
        when(queueSessionRepository.save(any(QueueSession.class))).thenReturn(existingSession);

        QueueSessionBusinessResponseDTO result = queueSessionService.updateQueueStatus(PROF_USER_ID, true);

        assertThat(result).isNotNull();
        assertThat(result.isActive()).isTrue();

        verify(queueSessionRepository).save(existingSession);
    }

    @Test
    @DisplayName("Should throw exception when updating status and session does not exist or user is not the owner")
    void testUpdateQueueStatus_SessionNotFound() {
        // Arrange
        when(queueSessionRepository.findByOwnerUserId(PROF_USER_ID))
                .thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> queueSessionService.updateQueueStatus(PROF_USER_ID, true))
                .isInstanceOf(AppException.class)
                .hasMessage("Queue not found or you are not the owner.")
                .extracting(e -> ((AppException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND);

        verify(queueSessionRepository, never()).save(any());
    }

    // ==================== UPDATE SESSION SETTINGS TESTS ====================

    @Test
    @DisplayName("Should successfully update both prefix and tolerance")
    void testUpdateSessionSettings_UpdateBoth() {
        UpdateQueueSessionDTO dto = new UpdateQueueSessionDTO("CORTE", 15);
        when(queueSessionRepository.findByOwnerUserId(PROF_USER_ID)).thenReturn(Optional.of(existingSession));
        when(queueSessionRepository.existsByTicketCode(anyString())).thenReturn(false);

        QueueSessionBusinessResponseDTO result = queueSessionService.updateSessionSettings(PROF_USER_ID, dto);

        assertThat(result).isNotNull();
        assertThat(result.ticketCode()).startsWith("CORTE");
        assertThat(existingSession.getToleranceMinutes()).isEqualTo(15);
    }

    @Test
    @DisplayName("Should successfully update only tolerance when prefix is null")
    void testUpdateSessionSettings_UpdateOnlyTolerance() {
        // Arrange
        UpdateQueueSessionDTO dto = new UpdateQueueSessionDTO(null, 20);
        String oldTicketCode = existingSession.getTicketCode();

        when(queueSessionRepository.findByOwnerUserId(PROF_USER_ID)).thenReturn(Optional.of(existingSession));

        // Act
        QueueSessionBusinessResponseDTO result = queueSessionService.updateSessionSettings(PROF_USER_ID, dto);

        // Assert
        assertThat(result).isNotNull();
        assertThat(existingSession.getToleranceMinutes()).isEqualTo(20);
        assertThat(result.ticketCode()).isEqualTo(oldTicketCode);

        verify(queueSessionRepository).save(existingSession);
        verify(queueSessionRepository, never()).existsByTicketCode(anyString());
    }

    @Test
    @DisplayName("Should successfully update only prefix when tolerance is null")
    void testUpdateSessionSettings_UpdateOnlyPrefix() {
        // Arrange
        String newPrefix = "NOVO";
        UpdateQueueSessionDTO dto = new UpdateQueueSessionDTO(newPrefix, null);
        Integer oldTolerance = existingSession.getToleranceMinutes();

        when(queueSessionRepository.findByOwnerUserId(PROF_USER_ID)).thenReturn(Optional.of(existingSession));
        when(queueSessionRepository.existsByTicketCode(anyString())).thenReturn(false);

        // Act
        QueueSessionBusinessResponseDTO result = queueSessionService.updateSessionSettings(PROF_USER_ID, dto);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(existingSession.getId());
        assertThat(result.ticketCode()).startsWith(newPrefix);
        assertThat(existingSession.getToleranceMinutes()).isEqualTo(oldTolerance);

        verify(queueSessionRepository).save(existingSession);
        verify(queueSessionRepository, atLeastOnce()).existsByTicketCode(anyString());
    }

    @Test
    @DisplayName("Should not call save to database when all fields in DTO are null")
    void testUpdateSessionSettings_UpdateNothing() {
        // Arrange
        UpdateQueueSessionDTO dto = new UpdateQueueSessionDTO(null, null);

        when(queueSessionRepository.findByOwnerUserId(PROF_USER_ID)).thenReturn(Optional.of(existingSession));

        // Act
        QueueSessionBusinessResponseDTO result = queueSessionService.updateSessionSettings(PROF_USER_ID, dto);

        // Assert
        assertThat(result).isNotNull();

        verify(queueSessionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw exception when updated prefix results in invalid sanitized string")
    void testUpdateSessionSettings_InvalidSanitizedPrefix() {
        // Arrange
        UpdateQueueSessionDTO dto = new UpdateQueueSessionDTO("!!", null);

        when(queueSessionRepository.findByOwnerUserId(PROF_USER_ID)).thenReturn(Optional.of(existingSession));

        // Act & Assert
        assertThatThrownBy(() -> queueSessionService.updateSessionSettings(PROF_USER_ID, dto))
                .isInstanceOf(AppException.class)
                .hasMessage("Prefix must contain at least 2 valid alphanumeric characters.")
                .extracting(e -> ((AppException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);

        verify(queueSessionRepository, never()).existsByTicketCode(anyString());
        verify(queueSessionRepository, never()).save(any());
    }

    // ==================== REFRESH TICKET CODE TESTS ====================

    @Test
    @DisplayName("Should successfully refresh ticket code and return new DTO")
    void testRefreshTicketCode_Success() {
        when(queueSessionRepository.findByOwnerUserId(PROF_USER_ID)).thenReturn(Optional.of(existingSession));
        when(queueSessionRepository.existsByTicketCode(anyString())).thenReturn(false);
        when(queueSessionRepository.save(any(QueueSession.class))).thenReturn(existingSession);

        QueueSessionBusinessResponseDTO result = queueSessionService.refreshTicketCode(PROF_USER_ID);

        assertThat(result).isNotNull();
        assertThat(result.ticketCode()).startsWith("BARB");
        assertThat(result.ticketCode()).isNotEqualTo("BARB1234");
    }

    @Test
    @DisplayName("Should throw exception when trying to refresh code and session does not exist or user is not the ownertuss")
    void testRefreshTicketCode_SessionNotFound() {
        // Arrange
        when(queueSessionRepository.findByOwnerUserId(PROF_USER_ID))
                .thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> queueSessionService.refreshTicketCode(PROF_USER_ID))
                .isInstanceOf(AppException.class)
                .hasMessage("Queue not found or you are not the owner.")
                .extracting(e -> ((AppException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND);

        verify(queueSessionRepository, never()).existsByTicketCode(anyString());
    }

    // ==================== GET SESSION INFO BY CODE TESTS ====================

    @Test
    @DisplayName("Should return session info preview successfully when ticket code exists")
    void testGetSessionInfoByCode_Success() {
        String ticketCode = "BARB1234";
        QueueEntryResponseDTO mockDto = new QueueEntryResponseDTO(
                "entry-123", 1, "user-1", "Cliente", "Corte",
                QueueEntryStatus.WAITING, null, null, null, Instant.now(), null, 15
        );

        when(queueSessionRepository.findByTicketCodeWithBusiness(ticketCode))
                .thenReturn(Optional.of(existingSession));
        when(queueCacheService.getActiveEntriesDTO(existingSession.getId()))
                .thenReturn(List.of(mockDto, mockDto));

        var result = queueSessionService.getSessionInfoByCode(ticketCode);

        assertThat(result).isNotNull();
        assertThat(result.businessName()).isEqualTo("Barbearia do Zé");
        assertThat(result.peopleInQueue()).isEqualTo(2);
    }

    @Test
    @DisplayName("Should throw exception when trying to get info with an invalid ticket code")
    void testGetSessionInfoByCode_NotFound() {
        // Arrange
        String invalidCode = "INVALID";
        when(queueSessionRepository.findByTicketCodeWithBusiness(invalidCode)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> queueSessionService.getSessionInfoByCode(invalidCode))
                .isInstanceOf(AppException.class)
                .hasMessage("Queue not found for the Ticket code.")
                .extracting(e -> ((AppException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND);

        verifyNoInteractions(queueCacheService);
    }
}