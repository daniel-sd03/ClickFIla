package sodresoftwares.barbearia.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import sodresoftwares.barbearia.dto.queue.JoinQueueDTO;
import sodresoftwares.barbearia.dto.queue.QueueEntryResponseDTO;
import sodresoftwares.barbearia.dto.queue.UserQueueStatusDTO;
import sodresoftwares.barbearia.infra.exception.AppException;
import sodresoftwares.barbearia.mappers.QueueMapper;
import sodresoftwares.barbearia.model.business.Business;
import sodresoftwares.barbearia.model.queue.QueueEntry;
import sodresoftwares.barbearia.model.queue.QueueEntryStatus;
import sodresoftwares.barbearia.model.queue.QueueSession;
import sodresoftwares.barbearia.model.team.TeamMember;
import sodresoftwares.barbearia.model.team.TeamRole;
import sodresoftwares.barbearia.model.user.User;
import sodresoftwares.barbearia.model.user.UserRole;
import sodresoftwares.barbearia.repositories.queue.QueueEntryRepository;
import sodresoftwares.barbearia.repositories.queue.QueueSessionRepository;
import sodresoftwares.barbearia.repositories.team.TeamMemberRepository;
import sodresoftwares.barbearia.repositories.user.UserRepository;
import sodresoftwares.barbearia.services.notification.PushNotificationDispatcher;
import sodresoftwares.barbearia.services.notification.QueueNotificationService;
import sodresoftwares.barbearia.services.queue.QueueCacheService;
import sodresoftwares.barbearia.services.queue.QueueEntryService;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("QueueEntryService Tests")
class QueueEntryServiceTest {

    @Mock
    private QueueEntryRepository queueEntryRepository;

    @Mock
    private QueueSessionRepository queueSessionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TeamMemberRepository teamMemberRepository;

    @Mock
    private QueueCacheService queueCacheService;

    @Mock
    private QueueNotificationService queueNotificationService;

    @Mock
    private PushNotificationDispatcher pushDispatcher;

    @Spy
    private QueueMapper queueMapper = new QueueMapper();

    @InjectMocks
    private QueueEntryService queueEntryService;

    private User clientUser;
    private Business business;
    private TeamMember teamMember;
    private QueueSession activeSession;
    private QueueEntry waitingEntry;
    private JoinQueueDTO joinQueueDTO;
    private QueueEntryResponseDTO cachedDto;

    private final String BARBER_USER_ID = "barber-user-123";
    private final String CLIENT_USER_ID = "client-user-123";
    private final String SESSION_ID = "session-123";
    private final String ENTRY_ID = "entry-123";

    @BeforeEach
    void setUp() {
        User barberUser = User.builder()
                .id(BARBER_USER_ID)
                .name("Barbeiro Zé")
                .role(UserRole.PROFESSIONAL)
                .build();

        clientUser = User.builder()
                .id(CLIENT_USER_ID)
                .name("Cliente João")
                .role(UserRole.USER)
                .build();

        business = Business.builder()
                .id("biz-123")
                .name("Barbearia do Zé")
                .isActive(true)
                .build();

        teamMember = TeamMember.builder()
                .id("member-123")
                .business(business)
                .user(barberUser)
                .role(TeamRole.STAFF)
                .isActive(true)
                .build();

        activeSession = QueueSession.builder()
                .id(SESSION_ID)
                .business(business)
                .isActive(true)
                .toleranceMinutes(15)
                .build();

        waitingEntry = QueueEntry.builder()
                .id(ENTRY_ID)
                .queueSession(activeSession)
                .user(clientUser)
                .serviceName("Corte")
                .status(QueueEntryStatus.WAITING)
                .joinedAt(Instant.now())
                .build();

        cachedDto = new QueueEntryResponseDTO(
                ENTRY_ID,
                1,
                CLIENT_USER_ID,
                "Cliente João",
                "Corte",
                QueueEntryStatus.WAITING,
                null,
                null,
                null,
                Instant.now(),
                null,
                15
        );


        joinQueueDTO = new JoinQueueDTO(SESSION_ID, "Corte");
    }

    // ==================== GET USER QUEUE STATUS (BFF) TESTS ====================

    @Test
    @DisplayName("Should return active entry and ignore history when user has an active queue")
    void testGetUserQueueStatus_WithActiveEntry() {
        // Arrange
        String userId = CLIENT_USER_ID;
        when(queueEntryRepository.findByUserIdAndStatusIn(eq(userId), anyList()))
                .thenReturn(Optional.of(waitingEntry));
        when(queueCacheService.getActiveEntriesDTO(SESSION_ID))
                .thenReturn(List.of(cachedDto));

        // Act
        UserQueueStatusDTO result = queueEntryService.getUserQueueStatus(userId);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.activeEntry()).isNotNull();
        assertThat(result.activeEntry().id()).isEqualTo(ENTRY_ID);
        assertThat(result.activeEntry().status()).isEqualTo(QueueEntryStatus.WAITING);
        assertThat(result.latestHistoricalEntry()).isNull();

        verify(queueEntryRepository, never()).findFirstByUserIdOrderByJoinedAtDesc(anyString());
    }

    @Test
    @DisplayName("Should return historical entry when user does NOT have an active queue")
    void testGetUserQueueStatus_WithoutActiveEntry_WithHistory() {
        // Arrange
        String userId = CLIENT_USER_ID;
        QueueEntry historicalEntry = QueueEntry.builder()
                .id("hist-999")
                .queueSession(activeSession)
                .user(clientUser)
                .serviceName("Corte e Barba")
                .status(QueueEntryStatus.FINISHED)
                .joinedAt(Instant.now().minus(2, ChronoUnit.HOURS))
                .build();

        when(queueEntryRepository.findByUserIdAndStatusIn(eq(userId), anyList()))
                .thenReturn(Optional.empty());
        when(queueEntryRepository.findFirstByUserIdOrderByJoinedAtDesc(userId))
                .thenReturn(Optional.of(historicalEntry));

        // Act
        UserQueueStatusDTO result = queueEntryService.getUserQueueStatus(userId);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.activeEntry()).isNull();
        assertThat(result.latestHistoricalEntry()).isNotNull();
        assertThat(result.latestHistoricalEntry().id()).isEqualTo("hist-999");
        assertThat(result.latestHistoricalEntry().serviceName()).isEqualTo("Corte e Barba");

        verify(queueEntryRepository).findFirstByUserIdOrderByJoinedAtDesc(userId);
    }

    @Test
    @DisplayName("Should return both null when user has NO active queue and NO history")
    void testGetUserQueueStatus_WithoutActiveEntry_WithoutHistory() {
        // Arrange
        String userId = CLIENT_USER_ID;
        when(queueEntryRepository.findByUserIdAndStatusIn(eq(userId), anyList()))
                .thenReturn(Optional.empty());
        when(queueEntryRepository.findFirstByUserIdOrderByJoinedAtDesc(userId))
                .thenReturn(Optional.empty());

        // Act
        UserQueueStatusDTO result = queueEntryService.getUserQueueStatus(userId);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.activeEntry()).isNull();
        assertThat(result.latestHistoricalEntry()).isNull();
    }

    // ==================== FIND ACTIVE ENTRY TESTS ====================

    @Test
    @DisplayName("Should return active entry DTO with correct position when user is in queue")
    void testFindActiveEntryByUserId_Success() {
        // Arrange
        String userId = CLIENT_USER_ID;

        when(queueEntryRepository.findByUserIdAndStatusIn(eq(userId), anyList()))
                .thenReturn(Optional.of(waitingEntry));
        when(queueCacheService.getActiveEntriesDTO(SESSION_ID))
                .thenReturn(List.of(cachedDto));

        // Act
        Optional<QueueEntryResponseDTO> result = queueEntryService.findActiveEntryByUserId(userId);

        // Assert
        assertThat(result).isPresent();
        QueueEntryResponseDTO dto = result.get();

        assertThat(dto.id()).isEqualTo(ENTRY_ID);
        assertThat(dto.position()).isEqualTo(1);
        assertThat(dto.userId()).isEqualTo(userId);
        assertThat(dto.clientName()).isEqualTo("Cliente João");
        assertThat(dto.serviceName()).isEqualTo("Corte");
        assertThat(dto.status()).isEqualTo(QueueEntryStatus.WAITING);
        assertThat(dto.toleranceMinute()).isEqualTo(15);

        verify(queueEntryRepository).findByUserIdAndStatusIn(eq(userId), anyList());
        verify(queueCacheService).getActiveEntriesDTO(SESSION_ID);
    }

    @Test
    @DisplayName("Should return empty optional when user has no active entries in any queue")
    void testFindActiveEntryByUserId_NotFound() {
        // Arrange
        String userId = CLIENT_USER_ID;

        when(queueEntryRepository.findByUserIdAndStatusIn(eq(userId), anyList()))
                .thenReturn(Optional.empty());

        // Act
        Optional<QueueEntryResponseDTO> result = queueEntryService.findActiveEntryByUserId(userId);

        // Assert
        assertThat(result).isEmpty();

        verify(queueEntryRepository).findByUserIdAndStatusIn(eq(userId), anyList());
        verifyNoInteractions(queueCacheService);
    }


    // ==================== LATEST ENTRY TESTS ====================

    @Test
    @DisplayName("Should return latest entry when it is recent (within 24 hours)")
    void testFindLatestEntryByUserId_RecentEntry() {
        // Arrange
        when(queueEntryRepository.findFirstByUserIdOrderByJoinedAtDesc(CLIENT_USER_ID))
                .thenReturn(Optional.of(waitingEntry));

        // Act
        Optional<QueueEntryResponseDTO> result = queueEntryService.findLatestEntryByUserId(CLIENT_USER_ID);

        // Assert
        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo(ENTRY_ID);
    }

    @Test
    @DisplayName("Should return empty when latest entry is older than 24 hours")
    void testFindLatestEntryByUserId_OldEntry() {
        // Arrange
        waitingEntry.setJoinedAt(Instant.now().minus(25, ChronoUnit.HOURS));

        when(queueEntryRepository.findFirstByUserIdOrderByJoinedAtDesc(CLIENT_USER_ID))
                .thenReturn(Optional.of(waitingEntry));

        // Act
        Optional<QueueEntryResponseDTO> result = queueEntryService.findLatestEntryByUserId(CLIENT_USER_ID);

        // Assert
        assertThat(result).isEmpty();
        verifyNoInteractions(queueMapper);
    }

    @Test
    @DisplayName("Should return empty when user has no entries")
    void testFindLatestEntryByUserId_NoEntryFound() {
        // Arrange
        when(queueEntryRepository.findFirstByUserIdOrderByJoinedAtDesc(CLIENT_USER_ID))
                .thenReturn(Optional.empty());

        // Act
        Optional<QueueEntryResponseDTO> result = queueEntryService.findLatestEntryByUserId(CLIENT_USER_ID);

        // Assert
        assertThat(result).isEmpty();
        verifyNoInteractions(queueMapper);
    }

    // ==================== JOIN QUEUE TESTS ====================

    @Test
    @DisplayName("Should allow user to join queue and map all DTO fields correctly")
    void testJoinQueue_Success() {
        // Arrange
        when(queueSessionRepository.findByIdWithBusinessAndUser(SESSION_ID)).thenReturn(Optional.of(activeSession));
        when(userRepository.findById(CLIENT_USER_ID)).thenReturn(Optional.of(clientUser));
        when(queueEntryRepository.existsByUserIdAndStatusIn(eq(CLIENT_USER_ID), anyList())).thenReturn(false);
        when(queueEntryRepository.save(any(QueueEntry.class))).thenReturn(waitingEntry);
        when(queueEntryRepository.findActiveEntriesBySessionId(SESSION_ID)).thenReturn(List.of(waitingEntry));

        // Act
        QueueEntryResponseDTO response = queueEntryService.joinQueue(joinQueueDTO, CLIENT_USER_ID);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(ENTRY_ID);
        assertThat(response.position()).isEqualTo(1);
        assertThat(response.status()).isEqualTo(QueueEntryStatus.WAITING);

        verify(queueCacheService).evictSessionList(SESSION_ID);
    }

    @Test
    @DisplayName("Should throw exception when queue session is not found")
    void testJoinQueue_SessionNotFound() {
        // Arrange
        when(queueSessionRepository.findByIdWithBusinessAndUser(SESSION_ID)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> queueEntryService.joinQueue(joinQueueDTO, CLIENT_USER_ID))
                .isInstanceOf(AppException.class)
                .hasMessage("Queue session not found")
                .extracting(e -> ((AppException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND);

        verifyNoInteractions(userRepository, queueEntryRepository);
    }

    @Test
    @DisplayName("Should block user from joining if queue is closed")
    void testJoinQueue_QueueClosed() {
        // Arrange
        activeSession.setIsActive(false);
        when(queueSessionRepository.findByIdWithBusinessAndUser(SESSION_ID)).thenReturn(Optional.of(activeSession));

        // Act & Assert
        assertThatThrownBy(() -> queueEntryService.joinQueue(joinQueueDTO, CLIENT_USER_ID))
                .isInstanceOf(AppException.class)
                .hasMessage("This queue is currently closed.")
                .extracting(e -> ((AppException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);

        verifyNoInteractions(userRepository, queueEntryRepository);
    }

    @Test
    @DisplayName("Should block user from joining if already waiting in an active queue")
    void testJoinQueue_AlreadyInQueue() {
        // Arrange
        when(queueSessionRepository.findByIdWithBusinessAndUser(SESSION_ID)).thenReturn(Optional.of(activeSession));
        when(userRepository.findById(CLIENT_USER_ID)).thenReturn(Optional.of(clientUser));
        when(queueEntryRepository.existsByUserIdAndStatusIn(eq(CLIENT_USER_ID), anyList())).thenReturn(true);

        // Act & Assert
        assertThatThrownBy(() -> queueEntryService.joinQueue(joinQueueDTO, CLIENT_USER_ID))
                .isInstanceOf(AppException.class)
                .hasMessage("You are already waiting in an active queue.")
                .extracting(e -> ((AppException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT);

        verify(queueEntryRepository, never()).save(any());
    }

    // ==================== CALL NEXT TESTS ====================

    @Test
    @DisplayName("Should successfully call next waiting client and link the member serving")
    void testCallNext_Success() {
        // Arrange
        when(queueSessionRepository.findByIdWithBusinessAndUser(SESSION_ID)).thenReturn(Optional.of(activeSession));
        when(teamMemberRepository.findById(teamMember.getId())).thenReturn(Optional.of(teamMember));
        when(queueEntryRepository.existsByServedByMemberIdAndStatusIn(eq(teamMember.getId()), anyList())).thenReturn(false);
        when(queueEntryRepository.save(any(QueueEntry.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(queueEntryRepository.findActiveEntriesBySessionId(SESSION_ID)).thenReturn(List.of(waitingEntry));

        // Act
        QueueEntryResponseDTO response = queueEntryService.callNext(SESSION_ID, BARBER_USER_ID, teamMember.getId());

        // Assert
        assertThat(response).isNotNull();
        assertThat(waitingEntry.getStatus()).isEqualTo(QueueEntryStatus.CALLED);
        assertThat(waitingEntry.getCalledAt()).isNotNull();
        assertThat(waitingEntry.getServedByMember()).isEqualTo(teamMember);

        verify(queueEntryRepository).save(waitingEntry);
        verify(queueCacheService).evictSessionList(SESSION_ID);
        verify(pushDispatcher).notifyClientTurn(waitingEntry.getUser().getId(), teamMember.getName());
    }

    @Test
    @DisplayName("Should throw exception if logged user tries to act as a member without permission")
    void testCallNext_Forbidden() {
        // Arrange
        String intruderUserId = "intruder-user-999";
        when(queueSessionRepository.findByIdWithBusinessAndUser(SESSION_ID)).thenReturn(Optional.of(activeSession));
        when(teamMemberRepository.findById(teamMember.getId())).thenReturn(Optional.of(teamMember));
        when(teamMemberRepository.existsByUserIdAndBusinessIdAndRoleAndIsActiveTrue(intruderUserId, business.getId(), TeamRole.OWNER))
                .thenReturn(false);

        // Act & Assert
        assertThatThrownBy(() -> queueEntryService.callNext(SESSION_ID, intruderUserId, teamMember.getId()))
                .isInstanceOf(AppException.class)
                .hasMessage("You do not have permission to perform actions for this member.")
                .extracting(e -> ((AppException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("Should throw exception if there is no one waiting in the queue")
    void testCallNext_QueueEmpty() {
        // Arrange
        when(queueSessionRepository.findByIdWithBusinessAndUser(SESSION_ID)).thenReturn(Optional.of(activeSession));
        when(teamMemberRepository.findById(teamMember.getId())).thenReturn(Optional.of(teamMember));
        when(queueEntryRepository.existsByServedByMemberIdAndStatusIn(eq(teamMember.getId()), anyList())).thenReturn(false);
        when(queueEntryRepository.findActiveEntriesBySessionId(SESSION_ID)).thenReturn(List.of());

        // Act & Assert
        assertThatThrownBy(() -> queueEntryService.callNext(SESSION_ID, BARBER_USER_ID, teamMember.getId()))
                .isInstanceOf(AppException.class)
                .hasMessage("There are no clients waiting in the queue.")
                .extracting(e -> ((AppException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Should block callNext if the specific team member is already attending someone")
    void testCallNext_MemberBusy() {
        // Arrange
        when(queueSessionRepository.findByIdWithBusinessAndUser(SESSION_ID)).thenReturn(Optional.of(activeSession));
        when(teamMemberRepository.findById(teamMember.getId())).thenReturn(Optional.of(teamMember));
        when(queueEntryRepository.existsByServedByMemberIdAndStatusIn(eq(teamMember.getId()), anyList())).thenReturn(true);

        // Act & Assert
        assertThatThrownBy(() -> queueEntryService.callNext(SESSION_ID, BARBER_USER_ID, teamMember.getId()))
                .isInstanceOf(AppException.class)
                .hasMessage("You are already attending a client. Finish the current service or cancel before calling the next one.")
                .extracting(e -> ((AppException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ==================== REQUEUE ENTRY TESTS ====================

    @Test
    @DisplayName("Should successfully requeue entry, reset status and CLEAR servedByMember")
    void testRequeueEntry_Success() {
        // Arrange
        waitingEntry.setStatus(QueueEntryStatus.CALLED);
        waitingEntry.setMissedCalls(0);
        waitingEntry.setServedByMember(teamMember);

        QueueEntry nextClientWaiting = QueueEntry.builder()
                .id("entry-456")
                .status(QueueEntryStatus.WAITING)
                .joinedAt(Instant.now().plusSeconds(10))
                .build();

        when(queueEntryRepository.findActiveEntriesBySessionId(SESSION_ID))
                .thenReturn(new ArrayList<>(List.of(waitingEntry, nextClientWaiting)));
        when(teamMemberRepository.findByUserIdAndBusinessIdAndIsActiveTrue(BARBER_USER_ID, business.getId()))
                .thenReturn(Optional.of(teamMember));
        when(queueEntryRepository.save(any(QueueEntry.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        QueueEntryResponseDTO result = queueEntryService.requeueEntry(SESSION_ID, ENTRY_ID, BARBER_USER_ID);

        // Assert
        assertThat(waitingEntry.getStatus()).isEqualTo(QueueEntryStatus.WAITING);
        assertThat(waitingEntry.getServedByMember()).isNull();
        assertThat(waitingEntry.getMissedCalls()).isEqualTo(1);
        assertThat(waitingEntry.getJoinedAt()).isEqualTo(nextClientWaiting.getJoinedAt().plusSeconds(1));

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(ENTRY_ID);
        assertThat(result.status()).isEqualTo(QueueEntryStatus.WAITING);
        verify(pushDispatcher).notifyReallocation(waitingEntry.getUser().getId());
    }

    @Test
    @DisplayName("Should throw bad request exception when trying to requeue an entry that is not CALLED")
    void testRequeueEntry_InvalidStatus() {
        // Arrange
        when(queueEntryRepository.findActiveEntriesBySessionId(SESSION_ID)).thenReturn(List.of(waitingEntry));
        when(teamMemberRepository.findByUserIdAndBusinessIdAndIsActiveTrue(BARBER_USER_ID, business.getId())).thenReturn(Optional.of(teamMember));

        // Act & Assert
        assertThatThrownBy(() -> queueEntryService.requeueEntry(SESSION_ID, ENTRY_ID, BARBER_USER_ID))
                .isInstanceOf(AppException.class)
                .hasMessage("Only clients with CALLED status can be returned to the queue.");
    }

    // ==================== START SERVICE TESTS ====================

    @Test
    @DisplayName("Should start service successfully when client status is CALLED")
    void testStartService_Success() {
        // Arrange
        waitingEntry.setStatus(QueueEntryStatus.CALLED);

        when(queueEntryRepository.findActiveEntriesBySessionId(SESSION_ID)).thenReturn(List.of(waitingEntry));
        when(teamMemberRepository.findByUserIdAndBusinessIdAndIsActiveTrue(BARBER_USER_ID, business.getId())).thenReturn(Optional.of(teamMember));
        when(queueEntryRepository.save(any(QueueEntry.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        QueueEntryResponseDTO result = queueEntryService.startService(SESSION_ID, ENTRY_ID, BARBER_USER_ID);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(QueueEntryStatus.IN_SERVICE);
    }

    @Test
    @DisplayName("Should throw bad request exception when entry status is neither WAITING nor CALLED")
    void testStartService_InvalidStatus() {
        // Arrange
        waitingEntry.setStatus(QueueEntryStatus.FINISHED);
        when(queueEntryRepository.findActiveEntriesBySessionId(SESSION_ID)).thenReturn(List.of(waitingEntry));
        when(teamMemberRepository.findByUserIdAndBusinessIdAndIsActiveTrue(BARBER_USER_ID, business.getId())).thenReturn(Optional.of(teamMember));

        // Act & Assert
        assertThatThrownBy(() -> queueEntryService.startService(SESSION_ID, ENTRY_ID, BARBER_USER_ID))
                .isInstanceOf(AppException.class)
                .hasMessage("The client must be called to start the service.");
    }

    // ==================== FINISH SERVICE TESTS ====================

    @Test
    @DisplayName("Should finish service successfully and evictSessionList cache")
    void testFinishService_Success() {
        // Arrange
        waitingEntry.setStatus(QueueEntryStatus.IN_SERVICE);
        when(queueEntryRepository.findByIdWithFullGraph(ENTRY_ID)).thenReturn(Optional.of(waitingEntry));
        when(teamMemberRepository.findByUserIdAndBusinessIdAndIsActiveTrue(BARBER_USER_ID, business.getId())).thenReturn(Optional.of(teamMember));
        when(queueEntryRepository.save(any(QueueEntry.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        queueEntryService.finishService(ENTRY_ID, BARBER_USER_ID);

        // Assert
        assertThat(waitingEntry.getStatus()).isEqualTo(QueueEntryStatus.FINISHED);

        verify(queueEntryRepository).save(waitingEntry);
        verify(queueCacheService).evictSessionList(SESSION_ID);
    }

    @Test
    @DisplayName("Should throw bad request exception when attempting to finish a service not IN_SERVICE")
    void testFinishService_InvalidStatus() {
        // Arrange:
        when(queueEntryRepository.findByIdWithFullGraph(ENTRY_ID)).thenReturn(Optional.of(waitingEntry));
        when(teamMemberRepository.findByUserIdAndBusinessIdAndIsActiveTrue(BARBER_USER_ID, business.getId())).thenReturn(Optional.of(teamMember));

        // Act & Assert
        assertThatThrownBy(() -> queueEntryService.finishService(ENTRY_ID, BARBER_USER_ID))
                .isInstanceOf(AppException.class)
                .hasMessage("You can only finish a service that is currently in progress (IN_SERVICE).")
                .extracting(e -> ((AppException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);

        verify(queueEntryRepository, never()).save(any());
        verifyNoInteractions(queueCacheService);
    }

    // ==================== CANCEL ENTRY TESTS ====================

    @Test
    @DisplayName("Should allow the client to cancel their own entry")
    void testCancelEntry_ByClient_Success() {
        // Arrange
        when(queueEntryRepository.findByIdWithFullGraph(ENTRY_ID)).thenReturn(Optional.of(waitingEntry));
        when(teamMemberRepository.existsByUserIdAndBusinessIdAndIsActiveTrue(CLIENT_USER_ID, business.getId())).thenReturn(false);
        when(queueEntryRepository.save(any(QueueEntry.class))).thenReturn(waitingEntry);

        // Act
        queueEntryService.cancelEntry(ENTRY_ID, CLIENT_USER_ID);

        // Assert
        assertThat(waitingEntry.getStatus()).isEqualTo(QueueEntryStatus.CANCELLED);
        verify(queueCacheService).evictSessionList(SESSION_ID);
        verify(queueEntryRepository).save(waitingEntry);
        verify(pushDispatcher, never()).notifyCancellation(anyString());
    }

    @Test
    @DisplayName("Should allow the barber to cancel a client's entry")
    void testCancelEntry_ByBarber_Success() {
        // Arrange
        when(queueEntryRepository.findByIdWithFullGraph(ENTRY_ID)).thenReturn(Optional.of(waitingEntry));
        when(teamMemberRepository.existsByUserIdAndBusinessIdAndIsActiveTrue(BARBER_USER_ID, business.getId())).thenReturn(true);
        when(queueEntryRepository.save(any(QueueEntry.class))).thenReturn(waitingEntry);

        // Act
        queueEntryService.cancelEntry(ENTRY_ID, BARBER_USER_ID);

        // Assert
        assertThat(waitingEntry.getStatus()).isEqualTo(QueueEntryStatus.CANCELLED);
        verify(queueCacheService).evictSessionList(SESSION_ID);
        verify(queueEntryRepository).save(waitingEntry);
        verify(pushDispatcher).notifyCancellation(waitingEntry.getUser().getId());
    }

    @Test
    @DisplayName("Should throw forbidden exception when user attempting to cancel is neither the barber nor the client")
    void testCancelEntry_Forbidden() {
        // Arrange
        when(queueEntryRepository.findByIdWithFullGraph(ENTRY_ID)).thenReturn(Optional.of(waitingEntry));
        String intruderUserId = "intruder-user-999";
        when(teamMemberRepository.existsByUserIdAndBusinessIdAndIsActiveTrue(intruderUserId, business.getId())).thenReturn(false);

        // Act & Assert
        assertThatThrownBy(() -> queueEntryService.cancelEntry(ENTRY_ID, intruderUserId))
                .isInstanceOf(AppException.class)
                .hasMessage("You do not have permission to cancel this entry.")
                .extracting(e -> ((AppException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN);

        verify(queueEntryRepository, never()).save(any());
        verifyNoInteractions(queueCacheService);
    }

    @ParameterizedTest
    @EnumSource(value = QueueEntryStatus.class, names = {"CANCELLED", "FINISHED"})
    @DisplayName("Should block cancellation if entry is already CANCELLED or FINISHED")
    void testCancelEntry_AlreadyFinishedOrCancelled(QueueEntryStatus invalidStatus) {
        // Arrange
        waitingEntry.setStatus(invalidStatus);
        when(queueEntryRepository.findByIdWithFullGraph(ENTRY_ID)).thenReturn(Optional.of(waitingEntry));
        when(teamMemberRepository.existsByUserIdAndBusinessIdAndIsActiveTrue(CLIENT_USER_ID, business.getId())).thenReturn(false);

        // Act & Assert
        assertThatThrownBy(() -> queueEntryService.cancelEntry(ENTRY_ID, CLIENT_USER_ID))
                .isInstanceOf(AppException.class)
                .hasMessage("This entry is already cancelled or finished.")
                .extracting(e -> ((AppException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);

        verify(queueEntryRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should block client from cancelling when service is IN_SERVICE")
    void testCancelEntry_ByClient_ServiceInProgress() {
        // Arrange
        waitingEntry.setStatus(QueueEntryStatus.IN_SERVICE);
        when(queueEntryRepository.findByIdWithFullGraph(ENTRY_ID)).thenReturn(Optional.of(waitingEntry));
        when(teamMemberRepository.existsByUserIdAndBusinessIdAndIsActiveTrue(CLIENT_USER_ID, business.getId())).thenReturn(false);

        // Act & Assert
        assertThatThrownBy(() -> queueEntryService.cancelEntry(ENTRY_ID, CLIENT_USER_ID))
                .isInstanceOf(AppException.class)
                .hasMessage("You cannot cancel your spot while the service is in progress.")
                .extracting(e -> ((AppException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);

        verify(queueEntryRepository, never()).save(any());
    }
}