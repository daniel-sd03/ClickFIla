package sodresoftwares.barbearia.controllers.user;

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
import sodresoftwares.barbearia.dto.auth.ChangePasswordDTO;
import sodresoftwares.barbearia.dto.auth.RegisterDTO;
import sodresoftwares.barbearia.dto.user.UpdateUserDTO;
import sodresoftwares.barbearia.dto.user.UserResponseDTO;
import sodresoftwares.barbearia.dto.user.VerifyEmailDTO;
import sodresoftwares.barbearia.infra.security.SecurityFilter;
import sodresoftwares.barbearia.infra.security.SubscriptionCheckInterceptor;
import sodresoftwares.barbearia.infra.security.WebMvcConfig;
import sodresoftwares.barbearia.model.user.User;
import sodresoftwares.barbearia.model.user.UserRole;
import sodresoftwares.barbearia.services.user.UserService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = UserController.class,
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
@DisplayName("UserController Tests")
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JacksonTester<Object> jsonTester;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private CacheManager cacheManager;

    private User loggedInUser;
    private RegisterDTO registerDTO;
    private UpdateUserDTO updateDTO;
    private UserResponseDTO responseDTO;


    @BeforeEach
    void setUp() {
        loggedInUser = User.builder()
                .id("user-123")
                .name("Old Name")
                .role(UserRole.USER)
                .build();

        registerDTO = new RegisterDTO(
                "user@test.com",
                "password123",
                "Cliente Teste",
                "11999999999",
                true);

        updateDTO = new UpdateUserDTO(
                "New Name",
                "111111111");

        responseDTO = new UserResponseDTO(
                "user-123",
                "New Name",
                "user@test.com",
                "111111111",
                "USER"
        );
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(loggedInUser, null, loggedInUser.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ==================== GET MY PROFILE TESTS ====================

    @Test
    @DisplayName("GET /users/me -> Should return 200 OK and profile data")
    void testGetMyProfile_Success() throws Exception {
        // Arrange
        when(userService.getMyProfile(any())).thenReturn(responseDTO);

        // Act & Assert
        mockMvc.perform(get("/users/me")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("user-123"))
                .andExpect(jsonPath("$.name").value("New Name"))
                .andExpect(jsonPath("$.login").value("user@test.com"));
    }

    // ==================== POST REGISTER CLIENT TESTS ====================

    @Test
    @DisplayName("POST /users -> Should register new user successfully (HTTP 201)")
    void testRegister_Success() throws Exception {
        // Arrange
        User userMock = new User();
        when(userService.registerClient(any(RegisterDTO.class), any())).thenReturn(userMock);

        // Act & Assert
        mockMvc.perform(post("/users/client")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonTester.write(registerDTO).getJson()))
                .andExpect(status().isCreated());

        verify(userService).registerClient(any(RegisterDTO.class), any());
    }

    @Test
    @DisplayName("POST /users -> Should return 400 when register fields are blank")
    void testRegister_ValidationErrors() throws Exception {
        RegisterDTO invalidDTO = new RegisterDTO("", "", "", "123",true);

        // Act & Assert
        mockMvc.perform(post("/users/client")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonTester.write(invalidDTO).getJson()))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(userService);
    }

    // ==================== POST REGISTER PROFESSIONAL TESTS ====================

    @Test
    @DisplayName("POST /users/professional -> Should register new professional successfully (HTTP 201)")
    void testRegisterProfessional_Success() throws Exception {
        // Arrange
        User userMock = new User();
        when(userService.registerProfessional(any(RegisterDTO.class), any())).thenReturn(userMock);

        // Act & Assert
        mockMvc.perform(post("/users/professional")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonTester.write(registerDTO).getJson()))
                .andExpect(status().isCreated());

        verify(userService).registerProfessional(any(RegisterDTO.class), any());
    }

    @Test
    @DisplayName("POST /users/professional -> Should return 400 when register fields are blank")
    void testRegisterProfessional_ValidationErrors() throws Exception {
        RegisterDTO invalidDTO = new RegisterDTO("", "", "", "123", true);

        // Act & Assert
        mockMvc.perform(post("/users/professional")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonTester.write(invalidDTO).getJson()))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(userService);
    }

    // ==================== EMAIL VERIFICATION TESTS ====================

    @Test
    @DisplayName("POST /users/verify-email -> Should return 204 No Content when OTP is valid")
    void verifyEmail_Success() throws Exception {
        VerifyEmailDTO dto = new VerifyEmailDTO("123456");
        doNothing().when(userService).verifyEmail(anyString(), anyString());

        mockMvc.perform(post("/users/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonTester.write(dto).getJson()))
                .andExpect(status().isNoContent());

        verify(userService).verifyEmail(any(), eq("123456"));
    }

    @Test
    @DisplayName("POST /users/verify-email -> Should return 400 Bad Request when OTP is blank")
    void verifyEmail_ValidationError() throws Exception {
        VerifyEmailDTO invalidDto = new VerifyEmailDTO("");

        mockMvc.perform(post("/users/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonTester.write(invalidDto).getJson()))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(userService);
    }

    // ==================== RESEND VERIFICATION TESTS ====================

    @Test
    @DisplayName("POST /users/resend-otp -> Should return 204 No Content when resent successfully")
    void resendOtp_Success() throws Exception {
        doNothing().when(userService).resendVerificationEmail(anyString());

        mockMvc.perform(post("/users/resend-otp")
                        .with(user(loggedInUser))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent());

        verify(userService).resendVerificationEmail(any());
    }

    // ==================== UPGRADE ROLE TESTS ====================

    @Test
    @DisplayName("Should return 200 OK when user successfully upgrades to Professional")
    void upgradeToProfessional_Success() throws Exception {
        // Arrange
        User loggedInUser = User.builder()
                .id("user-123")
                .role(UserRole.USER)
                .build();

        UserResponseDTO mockResponse = new UserResponseDTO(
                "user-123",
                "João Barbeiro",
                "joao@test.com",
                "11999999999",
                UserRole.PROFESSIONAL.name()
        );

        when(userService.upgradeToProfessional(any())).thenReturn(mockResponse);

        // Act & Assert
        mockMvc.perform(patch("/users/me/upgrade-role")
                        .with(user(loggedInUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("user-123"))
                .andExpect(jsonPath("$.role").value("PROFESSIONAL"));

        verify(userService).upgradeToProfessional(any());
    }

    // ==================== PATCH UPDATE USER PROFILE TESTS ====================

    @Test
    @DisplayName("PATCH /users/me -> Should update profile and return 200 OK")
    void testUpdateMyProfile_Success() throws Exception {
        when(userService.updateUserProfile(any(), any())).thenReturn(responseDTO);

        mockMvc.perform(patch("/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonTester.write(updateDTO).getJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("user-123"))
                .andExpect(jsonPath("$.name").value("New Name"))
                .andExpect(jsonPath("$.phone").value("111111111"));
    }

    @Test
    @DisplayName("PATCH /users/me -> Should return 400 Bad Request when DTO has validation errors")
    void testUpdateMyProfile_ValidationError() throws Exception {
        UpdateUserDTO invalidDTO = new UpdateUserDTO("A", "111111111");

        mockMvc.perform(patch("/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonTester.write(invalidDTO).getJson()))
                .andExpect(status().isBadRequest());
    }

    // ==================== PATCH CHANGE PASSWORD TESTS ====================

    @Test
    @DisplayName("PATCH /users/me/password -> Should change password and return 204 No Content")
    void testChangeMyPassword_Success() throws Exception {
        // Arrange
        ChangePasswordDTO dto = new ChangePasswordDTO("oldPass123", "newPass123", "newPass123");
        doNothing().when(userService).changePassword(eq("user-123"), any(ChangePasswordDTO.class));

        // Act & Assert
        mockMvc.perform(patch("/users/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonTester.write(dto).getJson()))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("PATCH /users/me/password -> Should return 400 Bad Request when DTO has validation errors")
    void testChangeMyPassword_ValidationError() throws Exception {
        // Arrange:
        ChangePasswordDTO invalidDto = new ChangePasswordDTO("oldPass123", "123", "123");

        // Act & Assert
        mockMvc.perform(patch("/users/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonTester.write(invalidDto).getJson()))
                .andExpect(status().isBadRequest());
    }

    // ==================== DELETE MY ACCOUNT TESTS ====================

    @Test
    @DisplayName("DELETE /users/me -> Should soft delete account and return 204 No Content")
    void testDeleteMyAccount_Success() throws Exception {
        doNothing().when(userService).deleteMyAccount(anyString());

        mockMvc.perform(delete("/users/me")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent());

        verify(userService).deleteMyAccount(any());
    }

    // ==================== PATCH TUTORIAL TESTS ====================

    @Test
    @DisplayName("PATCH /users/me/tutorial -> Should complete tutorial and return 204 No Content")
    void completeTutorial_Success() throws Exception {
        // Arrange
        doNothing().when(userService).markTutorialAsCompleted(loggedInUser.getId());

        // Act & Assert
        mockMvc.perform(patch("/users/me/tutorial")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent());

        verify(userService).markTutorialAsCompleted(any());
    }

    // ==================== DOWNGRADE ROLE TESTS ====================

    @Test
    @DisplayName("PATCH /users/me/downgrade-to-client -> Should return 200 OK when user successfully downgrades to Client")
    void downgradeToClient_Success() throws Exception {
        // Arrange
        User professionalUser = User.builder()
                .id("user-123")
                .role(UserRole.PROFESSIONAL)
                .build();

        UserResponseDTO mockResponse = new UserResponseDTO(
                "user-123",
                "João Cliente",
                "joao@test.com",
                "11999999999",
                UserRole.USER.name()
        );

        when(userService.downgradeToClient(any())).thenReturn(mockResponse);

        // Act & Assert
        mockMvc.perform(patch("/users/me/downgrade-to-client")
                        .with(user(professionalUser))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("user-123"))
                .andExpect(jsonPath("$.role").value("USER"));

        verify(userService).downgradeToClient(any());
    }
}