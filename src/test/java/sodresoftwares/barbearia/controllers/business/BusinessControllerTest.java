package sodresoftwares.barbearia.controllers.business;

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
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import sodresoftwares.barbearia.dto.business.BusinessResponseDTO;
import sodresoftwares.barbearia.dto.business.CreateBusinessDTO;
import sodresoftwares.barbearia.dto.business.UpdateBusinessDTO;
import sodresoftwares.barbearia.dto.user.UserResponseDTO;
import sodresoftwares.barbearia.infra.security.SecurityFilter;
import sodresoftwares.barbearia.infra.security.SubscriptionCheckInterceptor;
import sodresoftwares.barbearia.infra.security.WebMvcConfig;
import sodresoftwares.barbearia.model.user.User;
import sodresoftwares.barbearia.model.user.UserRole;
import sodresoftwares.barbearia.services.business.BusinessService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = BusinessController.class,
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
@DisplayName("BusinessController Tests")
class BusinessControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JacksonTester<Object> jsonTester;

    @MockitoBean
    private BusinessService businessService;

    @MockitoBean
    private CacheManager cacheManager;

    private User loggedInUser;
    private CreateBusinessDTO createBusinessDTO;
    private UpdateBusinessDTO updateDTO;
    private BusinessResponseDTO responseDTO;

    @BeforeEach
    void setUp() {
        loggedInUser = User.builder()
                .id("barber-user-123")
                .name("Barbeiro Zé")
                .role(UserRole.PROFESSIONAL)
                .build();

        updateDTO = new UpdateBusinessDTO("New Business Name");

        UserResponseDTO userDTO = UserResponseDTO.fromEntity(loggedInUser);

        responseDTO = new BusinessResponseDTO(
                "biz-123",
                "New Business Name",
                true,
                userDTO
        );

        createBusinessDTO = new CreateBusinessDTO(
                "Barbearia do Zé",
                "12345678909"
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ==================== GET MY BUSINESS PROFILE TESTS ====================

    @Test
    @DisplayName("GET /businesses/me -> Should return 200 OK and profile data including user")
    void testGetMyBusinessProfile_Success() throws Exception {
        // Arrange
        UserResponseDTO mockUserDTO = new UserResponseDTO("user-123", "Barbeiro Zé", "ze@test.com", "3199999999", "PROFESSIONAL");
        BusinessResponseDTO getResponseDTO = new BusinessResponseDTO("biz-123", "Barbearia do Zé", true, mockUserDTO);

        when(businessService.getMyBusinessProfile(any())).thenReturn(getResponseDTO);

        // Act & Assert
        mockMvc.perform(get("/businesses/me")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("biz-123"))
                .andExpect(jsonPath("$.name").value("Barbearia do Zé"))
                .andExpect(jsonPath("$.user.id").value("user-123"))
                .andExpect(jsonPath("$.user.name").value("Barbeiro Zé"))
                .andExpect(jsonPath("$.user.login").value("ze@test.com"));
    }

    // ==================== POST CREATE BUSINESS TESTS ====================

    @Test
    @DisplayName("POST /businesses -> Should create new business successfully (HTTP 201)")
    void testCreateBusiness_Success() throws Exception {
        // Arrange
        doNothing().when(businessService).createBusiness(any(), any(CreateBusinessDTO.class));

        // Act & Assert
        mockMvc.perform(post("/businesses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonTester.write(createBusinessDTO).getJson()))
                .andExpect(status().isCreated());

        verify(businessService).createBusiness(any(), any(CreateBusinessDTO.class));
    }

    @Test
    @DisplayName("POST /businesses -> Should return 400 when business register fields are blank")
    void testCreateBusiness_ValidationErrors() throws Exception {
        // Arrange
        CreateBusinessDTO invalidDTO = new CreateBusinessDTO("", "12345678909");

        // Act & Assert
        mockMvc.perform(post("/businesses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonTester.write(invalidDTO).getJson()))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(businessService);
    }

    // ==================== PATCH UPDATE BUSINESS PROFILE TESTS ====================

    @Test
    @DisplayName("PATCH /businesses/me -> Should update business profile and return 200 OK")
    void testUpdateMyBusinessProfile_Success() throws Exception {
        when(businessService.updateBusinessProfile(any(), any()))
                .thenReturn(responseDTO);

        mockMvc.perform(patch("/businesses/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonTester.write(updateDTO).getJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("biz-123"))
                .andExpect(jsonPath("$.name").value("New Business Name"))
                .andExpect(jsonPath("$.isActive").value(true));
    }

    @Test
    @DisplayName("PATCH /businesses/me -> Should return 400 Bad Request when DTO has validation errors")
    void testUpdateMyBusinessProfile_ValidationError() throws Exception {
        UpdateBusinessDTO invalidDTO = new UpdateBusinessDTO("");

        mockMvc.perform(patch("/businesses/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonTester.write(invalidDTO).getJson()))
                .andExpect(status().isBadRequest());
    }

    // ==================== PUT REACTIVATE BUSINESS TESTS ====================

    @Test
    @DisplayName("PUT /businesses/reactivate -> Should reactivate business and return 200 OK")
    void testReactivateBusiness_Success() throws Exception {
        doNothing().when(businessService).reactivateBusiness(any());

        mockMvc.perform(put("/businesses/reactivate")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        verify(businessService).reactivateBusiness(any());
    }
}