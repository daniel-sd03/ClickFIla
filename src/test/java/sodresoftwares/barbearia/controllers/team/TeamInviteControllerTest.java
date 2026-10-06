package sodresoftwares.barbearia.controllers.team;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientAutoConfiguration;
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
import sodresoftwares.barbearia.dto.team.CreateTeamInviteDTO;
import sodresoftwares.barbearia.dto.team.TeamInviteResponseDTO;
import sodresoftwares.barbearia.infra.security.SecurityFilter;
import sodresoftwares.barbearia.infra.security.SubscriptionCheckInterceptor;
import sodresoftwares.barbearia.infra.security.WebMvcConfig;
import sodresoftwares.barbearia.model.team.TeamRole;
import sodresoftwares.barbearia.model.user.User;
import sodresoftwares.barbearia.model.user.UserRole;
import sodresoftwares.barbearia.services.team.TeamInviteService;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = TeamInviteController.class,
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
                OAuth2ClientAutoConfiguration.class
        }
)
@ActiveProfiles("test")
@AutoConfigureMockMvc(addFilters = false)
@AutoConfigureJsonTesters
@DisplayName("TeamInviteController Tests")
class TeamInviteControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JacksonTester<Object> jsonTester;

    @MockitoBean
    private CacheManager cacheManager;

    @MockitoBean
    private TeamInviteService teamInviteService;

    private User mockLoggedUser;

    @BeforeEach
    void setUp() {
        mockLoggedUser = User.builder()
                .id("logged-user-id")
                .login("user@test.com")
                .role(UserRole.PROFESSIONAL)
                .build();

        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(mockLoggedUser, null, mockLoggedUser.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Should return 201 Created when sending an invite")
    void sendInvite_Success() throws Exception {
        CreateTeamInviteDTO dto = new CreateTeamInviteDTO("new@test.com");

        mockMvc.perform(post("/team-invites")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonTester.write(dto).getJson()))
                .andExpect(status().isCreated());

        verify(teamInviteService).sendInvite(any(), any(CreateTeamInviteDTO.class));
    }

    @Test
    @DisplayName("Should return 200 OK and a list of pending invites")
    void getMyPendingInvites_Success() throws Exception {
        TeamInviteResponseDTO inviteDTO = new TeamInviteResponseDTO(
                "invite-123", "biz-123", "Barbearia Teste", "user@test.com", TeamRole.STAFF, Instant.now()
        );

        when(teamInviteService.getMyPendingInvites(any())).thenReturn(List.of(inviteDTO));

        mockMvc.perform(get("/team-invites/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("invite-123"))
                .andExpect(jsonPath("$[0].businessName").value("Barbearia Teste"));

        verify(teamInviteService).getMyPendingInvites(any());
    }

    @Test
    @DisplayName("Should return 200 OK when accepting an invite")
    void acceptInvite_Success() throws Exception {
        String inviteId = "invite-123";

        mockMvc.perform(post("/team-invites/{id}/accept", inviteId))
                .andExpect(status().isOk());

        verify(teamInviteService).acceptInvite(eq(inviteId), any(), any());
    }

    @Test
    @DisplayName("Should return 200 OK when declining an invite")
    void declineInvite_Success() throws Exception {
        String inviteId = "invite-123";

        mockMvc.perform(post("/team-invites/{id}/decline", inviteId))
                .andExpect(status().isOk());

        verify(teamInviteService).declineInvite(eq(inviteId), any());
    }
}