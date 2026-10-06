package sodresoftwares.barbearia.controllers.queue;

import org.junit.jupiter.api.AfterEach;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import sodresoftwares.barbearia.dto.queue.*;
import sodresoftwares.barbearia.infra.security.SecurityFilter;
import sodresoftwares.barbearia.infra.security.SubscriptionCheckInterceptor;
import sodresoftwares.barbearia.infra.security.WebMvcConfig;
import sodresoftwares.barbearia.model.queue.QueueEntryStatus;
import sodresoftwares.barbearia.model.user.User;
import sodresoftwares.barbearia.model.user.UserRole;
import sodresoftwares.barbearia.services.queue.QueueEntryService;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = QueueEntryController.class,
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
@DisplayName("QueueEntryController Tests")
class QueueEntryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JacksonTester<Object> jsonTester;

    @MockitoBean
    private CacheManager cacheManager;

    @MockitoBean
    private QueueEntryService queueEntryService;

    private QueueEntryResponseDTO entryResponseDTO;
    private JoinQueueDTO joinQueueDTO;
    private QueueSessionActionDTO actionDTO;
    @BeforeEach
    void setUp() {
        User loggedInUser = User.builder()
                .id("user-123")
                .name("Cliente Silva")
                .role(UserRole.USER)
                .build();

        joinQueueDTO = new JoinQueueDTO("session-789", "Corte de Cabelo");

        entryResponseDTO = new QueueEntryResponseDTO(
                "entry-123",
                3,
                "user-123",
                "Cliente Silva",
                "Corte de Cabelo",
                QueueEntryStatus.WAITING,
                null,
                null,
                null,
                Instant.now(),
                null,
                10
        );


        actionDTO = new QueueSessionActionDTO("session-789");

        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(loggedInUser, null, loggedInUser.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ==================== GET USER QUEUE STATUS (BFF) TESTS ====================

    @Test
    @DisplayName("GET /queue-entries/me/status -> Should return 200 OK and serialize JSON correctly")
    void testGetMyStatus_JsonSerialization() throws Exception {
        // Arrange:
        UserQueueStatusDTO statusDTO = new UserQueueStatusDTO(entryResponseDTO, null);
        when(queueEntryService.getUserQueueStatus(any())).thenReturn(statusDTO);

        // Act & Assert:
        mockMvc.perform(get("/queue-entries/me/status")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeEntry.id").value("entry-123"));
    }

    // ==================== POST JOIN QUEUE TESTS ====================

    @Test
    @DisplayName("POST /queue-entries -> Should join queue and return 201 Created")
    void testJoinQueue_Success() throws Exception {
        when(queueEntryService.joinQueue(any(), any())).thenReturn(entryResponseDTO);

        mockMvc.perform(post("/queue-entries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonTester.write(joinQueueDTO).getJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("entry-123"))
                .andExpect(jsonPath("$.position").value(3))
                .andExpect(jsonPath("$.clientName").value("Cliente Silva"))
                .andExpect(jsonPath("$.status").value("WAITING"));
    }

    @Test
    @DisplayName("POST /queue-entries -> Should return 400 Bad Request when DTO is invalid")
    void testJoinQueue_ValidationError() throws Exception {
        JoinQueueDTO invalidDTO = new JoinQueueDTO(null, "");

        mockMvc.perform(post("/queue-entries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonTester.write(invalidDTO).getJson()))
                .andExpect(status().isBadRequest());
    }

    // ==================== POST CALL NEXT TESTS ====================

    @Test
    @DisplayName("POST /queue-entries/sessions/{sessionId}/next -> Should call next and return 200 OK")
    void testCallNext_Success() throws Exception {
        Instant calledTime = Instant.now();
        QueueEntryResponseDTO calledEntry = new QueueEntryResponseDTO(
                "entry-123",
                1,
                "user-123",
                "Cliente Silva",
                "Corte de Cabelo",
                QueueEntryStatus.CALLED,
                null,
                null,
                calledTime,
                calledTime,
                calledTime.plus(10, ChronoUnit.MINUTES),
                10
        );

        CallNextDTO callNextDTO = new CallNextDTO("member-123");

        when(queueEntryService.callNext(any(), any(),any())).thenReturn(calledEntry);

        mockMvc.perform(post("/queue-entries/sessions/{sessionId}/next", "session-789")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonTester.write(callNextDTO).getJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("entry-123"))
                .andExpect(jsonPath("$.status").value("CALLED"));
    }

    // ==================== PATCH REQUEUE ENTRY TESTS ====================

    @Test
    @DisplayName("PATCH /queue-entries/{entryId}/requeue -> Should requeue entry and return 200 OK")
    void testRequeueEntry_Success() throws Exception {

        when(queueEntryService.requeueEntry(any(), any(), any())).thenReturn(entryResponseDTO);

        mockMvc.perform(patch("/queue-entries/{entryId}/requeue", "entry-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonTester.write(actionDTO).getJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("entry-123"));
    }

    // ==================== PATCH START SERVICE TESTS ====================

    @Test
    @DisplayName("PATCH /queue-entries/{entryId}/start -> Should start service and return 200 OK")
    void testStartService_Success() throws Exception {
        QueueEntryResponseDTO inServiceEntry = new QueueEntryResponseDTO(
                "entry-123",
                1,
                "user-123",
                "Cliente Silva",
                "Corte de Cabelo",
                QueueEntryStatus.IN_SERVICE,
                "member-123",
                "Barbeiro Zé",
                Instant.now(),
                Instant.now(),
                null,
                10
        );
        when(queueEntryService.startService(any(), any(), any())).thenReturn(inServiceEntry);

        mockMvc.perform(patch("/queue-entries/{entryId}/start", "entry-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonTester.write(actionDTO).getJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("entry-123"))
                .andExpect(jsonPath("$.status").value("IN_SERVICE"));
    }

    // ==================== PATCH FINISH SERVICE TESTS ====================

    @Test
    @DisplayName("PATCH /queue-entries/{entryId}/finish -> Should finish service and return 204 No Content")
    void testFinishService_Success() throws Exception {
        doNothing().when(queueEntryService).finishService(any(), any());

        mockMvc.perform(patch("/queue-entries/{entryId}/finish", "entry-123")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent());
    }

    // ==================== PATCH CANCEL ENTRY TESTS ====================

    @Test
    @DisplayName("PATCH /queue-entries/{entryId}/cancel -> Should cancel entry and return 204 No Content")
    void testCancelEntry_Success() throws Exception {
        doNothing().when(queueEntryService).cancelEntry(any(), any());

        mockMvc.perform(patch("/queue-entries/{entryId}/cancel", "entry-123")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent());
    }
}