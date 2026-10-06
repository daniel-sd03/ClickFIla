package sodresoftwares.barbearia.mappers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import sodresoftwares.barbearia.dto.queue.QueueEntryResponseDTO;
import sodresoftwares.barbearia.infra.exception.AppException;
import sodresoftwares.barbearia.model.queue.QueueEntry;
import sodresoftwares.barbearia.model.queue.QueueEntryStatus;
import sodresoftwares.barbearia.model.queue.QueueSession;
import sodresoftwares.barbearia.model.user.User;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("QueueMapper Tests")
class QueueMapperTest {

    private QueueMapper queueMapper;

    private QueueEntry entry1;
    private QueueEntry entry2;
    private List<QueueEntry> activeEntries;
    private Instant timeOfCall;
    private final Integer MOCK_TOLERANCE = 15;

    @BeforeEach
    void setUp() {
        queueMapper = new QueueMapper();
        timeOfCall = Instant.now();

        QueueSession session = QueueSession.builder()
                .id("session-1")
                .toleranceMinutes(MOCK_TOLERANCE)
                .build();

        User user1 = User.builder()
                .id("user-1")
                .name("João")
                .build();

        entry1 = QueueEntry.builder()
                .id("entry-1")
                .user(user1)
                .queueSession(session)
                .serviceName("Corte Simples")
                .status(QueueEntryStatus.WAITING)
                .build();

        User user2 = User.builder()
                .id("user-2")
                .name("Maria")
                .build();

        entry2 = QueueEntry.builder()
                .id("entry-2")
                .user(user2)
                .queueSession(session)
                .serviceName("Corte e Barba")
                .status(QueueEntryStatus.CALLED)
                .calledAt(timeOfCall)
                .build();

        activeEntries = List.of(entry1, entry2);
    }

    // ==================== SINGLE DTO TESTS ====================

    @Test
    @DisplayName("Should successfully map a single entry and calculate its correct position and tolerance")
    void testToSingleDto_Success() {
        // Act
        QueueEntryResponseDTO result = queueMapper.toSingleDto(entry2, activeEntries);

        // Assert
        assertThat(result.id()).isEqualTo("entry-2");
        assertThat(result.position()).isEqualTo(2);
        assertThat(result.userId()).isEqualTo("user-2");
        assertThat(result.clientName()).isEqualTo("Maria");
        assertThat(result.serviceName()).isEqualTo("Corte e Barba");
        assertThat(result.status()).isEqualTo(QueueEntryStatus.CALLED);
        assertThat(result.calledAt()).isEqualTo(timeOfCall);
        assertThat(result.toleranceMinute()).isEqualTo(MOCK_TOLERANCE);
        assertThat(result.serverTimeNow()).isNotNull();
        assertThat(result.toleranceExpiresAt()).isEqualTo(timeOfCall.plus(MOCK_TOLERANCE, ChronoUnit.MINUTES));
    }

    @Test
    @DisplayName("Should throw internal server error if entry is missing from active entries list")
    void testToSingleDto_EntryNotInActiveQueue() {
        // Arrange
        QueueSession session = QueueSession.builder().toleranceMinutes(10).build();
        QueueEntry missingEntry = QueueEntry.builder()
                .id("entry-999")
                .queueSession(session)
                .build();

        // Act & Assert
        assertThatThrownBy(() -> queueMapper.toSingleDto(missingEntry, activeEntries))
                .isInstanceOf(AppException.class)
                .hasMessage("Queue entry was not found in the active queue.")
                .extracting(e -> ((AppException) e).getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    // ==================== LIST DTO TESTS ====================

    @Test
    @DisplayName("Should successfully map a list of entries to a list of DTOs with correct positions and tolerance rules")
    void testToDtoList_Success() {
        // Act
        List<QueueEntryResponseDTO> results = queueMapper.toDtoList(activeEntries);

        // Assert
        assertThat(results).hasSize(2);

        // Asserts do entry 1
        assertThat(results.get(0).id()).isEqualTo("entry-1");
        assertThat(results.get(0).position()).isEqualTo(1);
        assertThat(results.get(0).clientName()).isEqualTo("João");
        assertThat(results.get(0).calledAt()).isNull();
        assertThat(results.get(0).toleranceMinute()).isEqualTo(MOCK_TOLERANCE);
        assertThat(results.get(0).serverTimeNow()).isNotNull();
        assertThat(results.get(0).toleranceExpiresAt()).isNull();

        // Asserts do entry 2
        assertThat(results.get(1).id()).isEqualTo("entry-2");
        assertThat(results.get(1).position()).isEqualTo(2);
        assertThat(results.get(1).clientName()).isEqualTo("Maria");
        assertThat(results.get(1).calledAt()).isEqualTo(timeOfCall);
        assertThat(results.get(1).toleranceMinute()).isEqualTo(MOCK_TOLERANCE);
        assertThat(results.get(1).serverTimeNow()).isNotNull();
        assertThat(results.get(1).toleranceExpiresAt()).isEqualTo(timeOfCall.plus(MOCK_TOLERANCE, ChronoUnit.MINUTES));
    }
}