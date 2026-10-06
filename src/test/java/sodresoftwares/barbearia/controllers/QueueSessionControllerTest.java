package sodresoftwares.barbearia.controllers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientAutoConfiguration;
import org.springframework.boot.security.oauth2.client.autoconfigure.servlet.OAuth2ClientWebSecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.json.AutoConfigureJsonTesters;
import org.springframework.boot.test.json.JacksonTester;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import sodresoftwares.barbearia.controllers.queue.QueueSessionController;
import sodresoftwares.barbearia.dto.queue.*;
import sodresoftwares.barbearia.infra.security.SecurityFilter;
import sodresoftwares.barbearia.infra.security.SubscriptionCheckInterceptor;
import sodresoftwares.barbearia.infra.security.WebMvcConfig;
import sodresoftwares.barbearia.model.queue.QueueEntryStatus;
import sodresoftwares.barbearia.model.user.User;
import sodresoftwares.barbearia.model.user.UserRole;
import sodresoftwares.barbearia.services.queue.QueueSessionService;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        controllers = QueueSessionController.class,
        excludeFilters = {
                @ComponentScan.Filter(
                        type = FilterType.ASSIGNABLE_TYPE,
                        classes = {
                                SecurityFilter.class,
                                SubscriptionCheckInterceptor.class,
                                WebMvcConfig.class
                        }
                )
        },
        excludeAutoConfiguration = {
                OAuth2ClientAutoConfiguration.class,
                OAuth2ClientWebSecurityAutoConfiguration.class
        }
)
@ActiveProfiles("test")
@AutoConfigureMockMvc(addFilters = false)
@AutoConfigureJsonTesters
@DisplayName("QueueSessionController Tests")
class QueueSessionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JacksonTester<Object> jsonTester;

    @MockitoBean
    private CacheManager cacheManager;

    @MockitoBean
    private QueueSessionService queueSessionService;

    private User loggedInUser;
    private QueueSessionBusinessResponseDTO sessionResponseDTO;

    @BeforeEach
    void setUp() {
        String USER_ID = "prof-user-123";
        loggedInUser = User.builder()
                .id(USER_ID)
                .name("Barbeiro Zé")
                .role(UserRole.PROFESSIONAL)
                .build();

        sessionResponseDTO = new QueueSessionBusinessResponseDTO(
                "session-123",
                "BARB1234",
                false
        );

        QueueEntryResponseDTO entryDTO = new QueueEntryResponseDTO(
                "entry-123",
                1,
                "client-123",
                "João Silva",
                "Corte Navalhado",
                QueueEntryStatus.WAITING,
                null,
                null,
                null,
                Instant.now(),
                null,
                10
        );

    }

    // ==================== GET SESSION INFO BY CODE TESTS ====================

    @Test
    @DisplayName("GET /queue-sessions/tickets/{ticketCode} - Should return 200 OK and session preview DTO")
    void testGetSessionByCode_Success() throws Exception {
        // Arrange
        String ticketCode = "BARB1";
        QueueSessionUserResponseDTO mockResponse = new QueueSessionUserResponseDTO(
                "session-123",
                "Barbearia do Zé",
                3,
                true,
                5
        );

        when(queueSessionService.getSessionInfoByCode(ticketCode)).thenReturn(mockResponse);

        // Act & Assert
        mockMvc.perform(get("/queue-sessions/tickets/{ticketCode}", ticketCode)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value("session-123"))
                .andExpect(jsonPath("$.businessName").value("Barbearia do Zé"))
                .andExpect(jsonPath("$.peopleInQueue").value(3))
                .andExpect(jsonPath("$.isActive").value(true))
                .andExpect(jsonPath("$.toleranceMinutes").value(5));
    }

    // ==================== POST CREATE SESSION TESTS ====================

    @Test
    @DisplayName("POST /queue-sessions -> Should create session and return 201 Created")
    void testCreateSession_Success() throws Exception {
        // Arrange
        when(queueSessionService.createQueueSession(any())).thenReturn(sessionResponseDTO);

        // Act & Assert
        mockMvc.perform(post("/queue-sessions")
                        .with(user(loggedInUser))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("session-123"))
                .andExpect(jsonPath("$.ticketCode").value("BARB1234"))
                .andExpect(jsonPath("$.isActive").value(false));
    }

    // ==================== POST REFRESH TICKET CODE TESTS ====================

    @Test
    @DisplayName("POST /queue-sessions/me/ticket-code -> Should refresh ticket code and return 200 OK")
    void refreshTicketCode_Success() throws Exception {
        // Arrange
        QueueSessionBusinessResponseDTO refreshedSessionDTO = new QueueSessionBusinessResponseDTO(
                sessionResponseDTO.id(),
                "BARB9999",
                sessionResponseDTO.isActive()
        );

        when(queueSessionService.refreshTicketCode(any())).thenReturn(refreshedSessionDTO);

        // Act & Assert
        mockMvc.perform(post("/queue-sessions/me/ticket-code")
                        .requestAttr("userId", loggedInUser.getId())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(sessionResponseDTO.id()))
                .andExpect(jsonPath("$.ticketCode").value("BARB9999"))
                .andExpect(jsonPath("$.isActive").value(sessionResponseDTO.isActive()));
    }

    // ==================== PATCH UPDATE ME TESTS (PREFIX & TOLERANCE) ====================

    @Test
    @DisplayName("PATCH /queue-sessions/me -> Should update settings and return 200 OK")
    void testUpdateSettings_Success() throws Exception {
        // Arrange
        UpdateQueueSessionDTO requestDTO = new UpdateQueueSessionDTO("CORTE", 15);
        QueueSessionBusinessResponseDTO updatedSessionDTO = new QueueSessionBusinessResponseDTO(
                "session-123",
                "CORTE1234",
                true
        );

        when(queueSessionService.updateSessionSettings(any(), any())).thenReturn(updatedSessionDTO);

        // Act & Assert
        mockMvc.perform(patch("/queue-sessions/me")
                        .with(user(loggedInUser))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonTester.write(requestDTO).getJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("session-123"))
                .andExpect(jsonPath("$.ticketCode").value("CORTE1234"))
                .andExpect(jsonPath("$.isActive").value(true));
    }

    @Test
    @DisplayName("PATCH /queue-sessions/me -> Should return 400 Bad Request when prefix is too short")
    void testUpdateSettings_ValidationError() throws Exception {
        // Arrange
        UpdateQueueSessionDTO requestDTO = new UpdateQueueSessionDTO("A", 15);

        // Act & Assert
        mockMvc.perform(patch("/queue-sessions/me")
                        .with(user(loggedInUser))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonTester.write(requestDTO).getJson()))
                .andExpect(status().isBadRequest());
    }

    // ==================== PATCH UPDATE STATUS TESTS ====================

    @Test
    @DisplayName("PATCH /queue-sessions/me/status -> Should update status and return 200 OK")
    void testUpdateStatus_Success() throws Exception {
        // Arrange
        UpdateQueueStatusDTO requestDTO = new UpdateQueueStatusDTO(true);
        QueueSessionBusinessResponseDTO activeSessionDTO = new QueueSessionBusinessResponseDTO("session-123", "BARB1234", true);

        when(queueSessionService.updateQueueStatus(any(), anyBoolean())).thenReturn(activeSessionDTO);

        // Act & Assert
        mockMvc.perform(patch("/queue-sessions/me/status")
                        .with(user(loggedInUser))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonTester.write(requestDTO).getJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("session-123"))
                .andExpect(jsonPath("$.ticketCode").value("BARB1234"))
                .andExpect(jsonPath("$.isActive").value(true));
    }

    @Test
    @DisplayName("PATCH /queue-sessions/me/status -> Should return 400 Bad Request when body is invalid")
    void testUpdateStatus_ValidationError() throws Exception {
        // Arrange
        UpdateQueueStatusDTO requestDTO = new UpdateQueueStatusDTO(null);

        // Act & Assert
        mockMvc.perform(patch("/queue-sessions/me/status")
                        .with(user(loggedInUser))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonTester.write(requestDTO).getJson()))
                .andExpect(status().isBadRequest());
    }
}