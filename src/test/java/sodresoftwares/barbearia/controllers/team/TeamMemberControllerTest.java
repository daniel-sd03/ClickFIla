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
import sodresoftwares.barbearia.dto.team.QuickCreateMemberDTO;
import sodresoftwares.barbearia.infra.security.SecurityFilter;
import sodresoftwares.barbearia.infra.security.SubscriptionCheckInterceptor;
import sodresoftwares.barbearia.infra.security.WebMvcConfig;
import sodresoftwares.barbearia.model.user.User;
import sodresoftwares.barbearia.model.user.UserRole;
import sodresoftwares.barbearia.services.team.TeamMemberService;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = TeamMemberController.class,
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
@DisplayName("TeamMemberController Tests")
class TeamMemberControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JacksonTester<Object> jsonTester;

    @MockitoBean
    private CacheManager cacheManager;

    @MockitoBean
    private TeamMemberService teamMemberService;

    private User mockLoggedUser;

    @BeforeEach
    void setUp() {
        mockLoggedUser = User.builder()
                .id("logged-user-id")
                .login("admin@test.com")
                .role(UserRole.PROFESSIONAL)
                .build();

        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(mockLoggedUser, null, mockLoggedUser.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    // ==================== POST TEAM MEMBERS ====================

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Should return 201 Created when quick creating a member")
    void quickCreateMember_Success() throws Exception {
        QuickCreateMemberDTO dto = new QuickCreateMemberDTO("John Doe");

        mockMvc.perform(post("/team-members/quick-create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonTester.write(dto).getJson()))
                .andExpect(status().isCreated());

        verify(teamMemberService).quickCreateMember(any(), any(QuickCreateMemberDTO.class));
    }

    // ==================== DELETE TEAM MEMBERS ====================

    @Test
    @DisplayName("Should return 204 No Content when removing a member")
    void removeMember_Success() throws Exception {
        String memberIdToRemove = "member-123";

        mockMvc.perform(delete("/team-members/{memberId}", memberIdToRemove))
                .andExpect(status().isNoContent());

        verify(teamMemberService).removeMember(any(), any());
    }

    // ==================== POST LEAVE TEAM  ====================

    @Test
    @DisplayName("POST /api/team/members/leave -> Should return 204 No Content")
    void testLeaveTeam_Success() throws Exception {

        // Act & Assert
        mockMvc.perform(post("/team-members/leave")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent());

        verify(teamMemberService).leaveTeam(any());
    }
}