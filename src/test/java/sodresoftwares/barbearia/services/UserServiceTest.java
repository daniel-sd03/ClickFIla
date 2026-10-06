package sodresoftwares.barbearia.services;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import sodresoftwares.barbearia.dto.auth.ChangePasswordDTO;
import sodresoftwares.barbearia.dto.auth.RegisterDTO;
import sodresoftwares.barbearia.dto.user.UpdateUserDTO;
import sodresoftwares.barbearia.dto.user.UserResponseDTO;
import sodresoftwares.barbearia.infra.exception.AppException;
import sodresoftwares.barbearia.model.user.User;
import sodresoftwares.barbearia.model.user.UserRole;
import sodresoftwares.barbearia.repositories.user.UserRepository;
import sodresoftwares.barbearia.services.auth.LgpdConsentService;
import sodresoftwares.barbearia.services.auth.OtpService;
import sodresoftwares.barbearia.services.notification.EmailService;
import sodresoftwares.barbearia.services.team.TeamMemberService;
import sodresoftwares.barbearia.services.user.UserService;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserService Tests")
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private LgpdConsentService lgpdConsentService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private TeamMemberService teamMemberService;

    @Mock
    private OtpService otpService;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private UserService userService;

    private User testUser;
    private RegisterDTO registerDTO;
    private final String USER_ID = "user-123";
    private final String RAW_PASSWORD = "password123";
    private final String ENCODED_PASSWORD = "$2a$10$encodedPasswordHash...";

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(USER_ID)
                .name("Old Name")
                .login("user@test.com")
                .phone("000000000")
                .password(ENCODED_PASSWORD)
                .role(UserRole.USER)
                .isActive(true)
                .deletedAt(null)
                .build();

        registerDTO = new RegisterDTO(
                testUser.getLogin(),
                RAW_PASSWORD,
                testUser.getName(),
                testUser.getPhone(),
                true
        );
    }
    // ====================  GET MY PROFILE TESTS ====================

    @Test
    @DisplayName("Should return user profile when user exists")
    void testGetMyProfile_Success() {
        // Arrange
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(testUser));

        // Act
        UserResponseDTO result = userService.getMyProfile(USER_ID);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(USER_ID);
        assertThat(result.name()).isEqualTo("Old Name");
        assertThat(result.login()).isEqualTo("user@test.com");

        verify(userRepository).findById(USER_ID);
    }

    @Test
    @DisplayName("Should throw not found exception when user does not exist on get profile")
    void testGetMyProfile_UserNotFound() {
        // Arrange
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> userService.getMyProfile(USER_ID))
                .isInstanceOf(AppException.class)
                .hasMessage("User not found.")
                .extracting(e -> ((AppException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ==================== REGISTER CLIENT TESTS ====================

    @Test
    @DisplayName("Should register new client successfully")
    void testRegisterClient_Successful() {
        // Arrange
        when(userRepository.existsByLogin(testUser.getLogin())).thenReturn(false);
        when(passwordEncoder.encode(RAW_PASSWORD)).thenReturn(ENCODED_PASSWORD);
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        // Act
        userService.registerClient(registerDTO, request);

        // Assert
        verify(userRepository).existsByLogin(testUser.getLogin());
        verify(passwordEncoder).encode(RAW_PASSWORD);
        verify(userRepository).save(argThat(user ->
                user.getLogin().equals(testUser.getLogin()) &&
                        user.getPassword().equals(ENCODED_PASSWORD) &&
                        user.getName().equals(testUser.getName()) &&
                        user.getPhone().equals(testUser.getPhone()) &&
                        user.getRole().equals(UserRole.USER)
        ));
        verify(lgpdConsentService).registerConsentForNewUser(testUser, request);
        verify(otpService, never()).generateAndSaveOtp(anyString());
        verify(emailService, never()).sendVerificationEmail(anyString(), anyString());
    }

    @Test
    @DisplayName("Should throw exception when trying to register an existing login")
    void testRegisterClient_UserAlreadyExists() {
        // Arrange
        when(userRepository.existsByLogin(testUser.getLogin())).thenReturn(true);

        // Act & Assert
        assertThatThrownBy(() -> userService.registerClient(registerDTO, request))
                .isInstanceOf(AppException.class)
                .hasMessage("User already exists")
                .extracting(e -> ((AppException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT);

        verify(userRepository).existsByLogin(testUser.getLogin());
        verify(passwordEncoder, never()).encode(anyString());
        verify(userRepository, never()).save(any(User.class));
        verify(lgpdConsentService, never()).registerConsentForNewUser(any(), any());
    }


    // ==================== REGISTER PROFESSIONAL TESTS ====================

    @Test
    @DisplayName("Should register new professional successfully")
    void testRegisterProfessional_Successful() {
        // Arrange
        when(userRepository.existsByLogin(testUser.getLogin())).thenReturn(false);
        when(passwordEncoder.encode(RAW_PASSWORD)).thenReturn(ENCODED_PASSWORD);
        when(userRepository.save(any(User.class))).thenReturn(testUser);
        when(otpService.generateAndSaveOtp(testUser.getId())).thenReturn("123456");

        // Act
        userService.registerProfessional(registerDTO, request);

        // Assert
        verify(userRepository).save(argThat(user ->
                user.getLogin().equals(testUser.getLogin()) &&
                        user.getRole().equals(UserRole.PROFESSIONAL)
        ));
        verify(lgpdConsentService).registerConsentForNewUser(testUser, request);
        verify(otpService).generateAndSaveOtp(testUser.getId());
        verify(emailService).sendVerificationEmail(testUser.getLogin(), "123456");
    }

    @Test
    @DisplayName("Should throw exception when trying to register an existing professional")
    void testRegisterProfessional_UserAlreadyExists() {
        // Arrange
        when(userRepository.existsByLogin(testUser.getLogin())).thenReturn(true);

        // Act & Assert
        assertThatThrownBy(() -> userService.registerProfessional(registerDTO, request))
                .isInstanceOf(AppException.class)
                .hasMessage("User already exists")
                .extracting(e -> ((AppException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT);

        verify(userRepository, never()).save(any(User.class));
    }

    // ==================== UPGRADE TO PROFESSIONAL TESTS ====================

    @Test
    @DisplayName("Should successfully upgrade a USER to PROFESSIONAL and send OTP if email is not verified")
    void upgradeToProfessional_Success_NotVerified() {
        // Arrange
        User normalUser = User.builder()
                .id("user-123")
                .login("user@test.com")
                .role(UserRole.USER)
                .emailVerified(false)
                .build();

        when(userRepository.findById("user-123")).thenReturn(Optional.of(normalUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(otpService.generateAndSaveOtp("user-123")).thenReturn("123456");

        // Act
        UserResponseDTO result = userService.upgradeToProfessional("user-123");

        // Assert
        assertThat(result.role()).isEqualTo(UserRole.PROFESSIONAL.name());
        verify(otpService).generateAndSaveOtp("user-123");
        verify(emailService).sendVerificationEmail("user@test.com", "123456");
    }

    @Test
    @DisplayName("Should successfully upgrade to PROFESSIONAL but SKIP OTP if email is already verified")
    void upgradeToProfessional_Success_AlreadyVerified() {
        // Arrange
        User normalUser = User.builder()
                .id("user-123")
                .role(UserRole.USER)
                .emailVerified(true)
                .build();

        when(userRepository.findById("user-123")).thenReturn(Optional.of(normalUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        UserResponseDTO result = userService.upgradeToProfessional("user-123");

        // Assert
        assertThat(result.role()).isEqualTo(UserRole.PROFESSIONAL.name());
        verify(otpService, never()).generateAndSaveOtp(anyString());
        verify(emailService, never()).sendVerificationEmail(anyString(), anyString());
    }

    @Test
    @DisplayName("Should throw CONFLICT when user is already a PROFESSIONAL")
    void upgradeToProfessional_AlreadyProfessional() {
        // Arrange
        User professionalUser = User.builder()
                .id("user-123")
                .role(UserRole.PROFESSIONAL)
                .build();

        when(userRepository.findById("user-123")).thenReturn(Optional.of(professionalUser));

        // Act & Assert
        assertThatThrownBy(() -> userService.upgradeToProfessional("user-123"))
                .isInstanceOf(AppException.class)
                .hasMessage("This account is already a professional account.")
                .extracting(e -> ((AppException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT);

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw NOT FOUND when user does not exist on upgrade")
    void upgradeToProfessional_UserNotFound() {
        // Arrange
        when(userRepository.findById("user-123")).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> userService.upgradeToProfessional("user-123"))
                .isInstanceOf(AppException.class)
                .hasMessage("User not found.")
                .extracting(e -> ((AppException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND);

        verify(userRepository, never()).save(any());
    }

    // ==================== UPDATE USER PROFILE TESTS ====================

    @Test
    @DisplayName("Should update both name and phone when valid DTO is provided")
    void testUpdateUserProfile_Success_UpdateAllFields() {
        // Arrange
        UpdateUserDTO updateDTO = new UpdateUserDTO("New Name", "111111111");

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        UserResponseDTO result = userService.updateUserProfile(USER_ID, updateDTO);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.name()).isEqualTo("New Name");
        assertThat(result.phone()).isEqualTo("111111111");

        verify(userRepository).save(testUser);
    }

    @Test
    @DisplayName("Should update ONLY the name when phone is null in DTO")
    void testUpdateUserProfile_Success_UpdateOnlyName() {
        // Arrange
        UpdateUserDTO updateDTO = new UpdateUserDTO("New Name", null);

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        UserResponseDTO result = userService.updateUserProfile(USER_ID, updateDTO);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.name()).isEqualTo("New Name");
        assertThat(result.phone()).isEqualTo("000000000");

        verify(userRepository).save(testUser);
    }

    @Test
    @DisplayName("Should update ONLY the phone when name is null or blank in DTO")
    void testUpdateUserProfile_Success_UpdateOnlyPhone() {
        // Arrange
        UpdateUserDTO updateDTO = new UpdateUserDTO("   ", "222222222");

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        UserResponseDTO result = userService.updateUserProfile(USER_ID, updateDTO);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.name()).isEqualTo("Old Name");
        assertThat(result.phone()).isEqualTo("222222222");

        verify(userRepository).save(testUser);
    }

    @Test
    @DisplayName("Should throw not found exception when user does not exist")
    void testUpdateUserProfile_UserNotFound() {
        // Arrange
        UpdateUserDTO updateDTO = new UpdateUserDTO("New Name", "111111111");
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> userService.updateUserProfile(USER_ID, updateDTO))
                .isInstanceOf(AppException.class)
                .hasMessage("User not found.")
                .extracting(e -> ((AppException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND);

        verify(userRepository, never()).save(any());
    }

    // ==================== CHANGE PASSWORD TESTS ====================

    @Test
    @DisplayName("Should change password successfully when all data is valid")
    void testChangePassword_Success() {
        // Arrange
        ChangePasswordDTO dto = new ChangePasswordDTO("oldPass123", "newPass123", "newPass123");
        testUser.setPassword("hashedOldPass");

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("oldPass123", "hashedOldPass")).thenReturn(true);
        when(passwordEncoder.encode("newPass123")).thenReturn("hashedNewPass");

        // Act
        userService.changePassword(USER_ID, dto);

        // Assert
        verify(userRepository).save(testUser);
        assertThat(testUser.getPassword()).isEqualTo("hashedNewPass");
    }

    @Test
    @DisplayName("Should throw exception when current password does not match")
    void testChangePassword_IncorrectCurrentPassword() {
        // Arrange
        ChangePasswordDTO dto = new ChangePasswordDTO("wrongOldPass", "newPass123", "newPass123");
        testUser.setPassword("hashedOldPass");

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("wrongOldPass", "hashedOldPass")).thenReturn(false);

        // Act & Assert
        assertThatThrownBy(() -> userService.changePassword(USER_ID, dto))
                .isInstanceOf(AppException.class)
                .hasMessage("Current password does not match.")
                .extracting(e -> ((AppException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw exception when new password and confirmation do not match")
    void testChangePassword_PasswordsDoNotMatch() {
        // Arrange
        ChangePasswordDTO dto = new ChangePasswordDTO("oldPass123", "newPass123", "differentPass");
        testUser.setPassword("hashedOldPass");

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("oldPass123", "hashedOldPass")).thenReturn(true);

        // Act & Assert
        assertThatThrownBy(() -> userService.changePassword(USER_ID, dto))
                .isInstanceOf(AppException.class)
                .hasMessage("New password and confirmation do not match.")
                .extracting(e -> ((AppException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw exception when user is not found on change password")
    void testChangePassword_UserNotFound() {
        // Arrange
        ChangePasswordDTO dto = new ChangePasswordDTO("oldPass123", "newPass123", "newPass123");
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> userService.changePassword(USER_ID, dto))
                .isInstanceOf(AppException.class)
                .hasMessage("User not found.")
                .extracting(e -> ((AppException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ==================== DELETE MY ACCOUNT TESTS ====================

    @Test
    @DisplayName("Should deactivate account successfully")
    void testDeleteMyAccount_Success() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(testUser));

        userService.deleteMyAccount(USER_ID);

        assertThat(testUser.getIsActive()).isFalse();
        assertThat(testUser.getDeletedAt()).isNotNull();
        verify(userRepository).save(testUser);
    }

    @Test
    @DisplayName("Should throw exception when account is already deleted")
    void testDeleteMyAccount_AlreadyDeleted() {
        testUser.setIsActive(false);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(testUser));

        assertThatThrownBy(() -> userService.deleteMyAccount(USER_ID))
                .isInstanceOf(AppException.class)
                .hasMessage("This account is already deactivated.")
                .extracting(e -> ((AppException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should deactivate professional links when a PROFESSIONAL deletes their account")
    void testDeleteMyAccount_Professional_DeactivatesLinks() {
        testUser.setRole(UserRole.PROFESSIONAL);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(testUser));

        userService.deleteMyAccount(USER_ID);

        assertThat(testUser.getIsActive()).isFalse();
        verify(teamMemberService).deactivateProfessionalLinksForUser(USER_ID);
        verify(userRepository).save(testUser);
    }

    // ==================== REACTIVATE ACCOUNT TESTS ====================

    @Test
    @DisplayName("Should reactivate account successfully")
    void testReactivateAccount_Success() {
        testUser.setIsActive(false);
        testUser.setDeletedAt(java.time.Instant.now());
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(testUser));

        userService.reactivateAccount(USER_ID);

        assertThat(testUser.getIsActive()).isTrue();
        assertThat(testUser.getDeletedAt()).isNull();
        verify(userRepository).save(testUser);
    }

    @Test
    @DisplayName("Should throw exception when trying to reactivate an active account")
    void testReactivateAccount_AlreadyActive() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(testUser));

        assertThatThrownBy(() -> userService.reactivateAccount(USER_ID))
                .isInstanceOf(AppException.class)
                .hasMessage("This account is already active.")
                .extracting(e -> ((AppException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);

        verify(userRepository, never()).save(any());
    }

    // ==================== TUTORIAL TESTS ====================

    @Test
    @DisplayName("Should mark tutorial as completed successfully")
    void markTutorialAsCompleted_Success() {
        // Arrange
        String loggedUserId = testUser.getId();
        testUser.setTutorialCompleted(false);

        when(userRepository.findById(loggedUserId)).thenReturn(Optional.of(testUser));

        // Act
        userService.markTutorialAsCompleted(loggedUserId);

        // Assert
        assertThat(testUser.getTutorialCompleted()).isTrue();
        verify(userRepository).findById(loggedUserId);
        verify(userRepository).save(testUser);
    }

    @Test
    @DisplayName("Should throw exception when user is not found while completing tutorial")
    void markTutorialAsCompleted_UserNotFound() {
        // Arrange
        String invalidUserId = "invalid-id";
        when(userRepository.findById(invalidUserId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> userService.markTutorialAsCompleted(invalidUserId))
                .isInstanceOf(AppException.class)
                .hasMessage("User not found.")
                .extracting(e -> ((AppException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND);

        verify(userRepository).findById(invalidUserId);
    }

    // ==================== DOWNGRADE TO CLIENT TESTS ====================

    @Test
    @DisplayName("Should successfully downgrade a PROFESSIONAL to USER and deactivate professional links")
    void downgradeToClient_Success() {
        // Arrange
        User professionalUser = User.builder()
                .id(USER_ID)
                .role(UserRole.PROFESSIONAL)
                .build();

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(professionalUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        UserResponseDTO result = userService.downgradeToClient(USER_ID);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.role()).isEqualTo(UserRole.USER.name());
        assertThat(professionalUser.getRole()).isEqualTo(UserRole.USER);

        verify(teamMemberService).deactivateProfessionalLinksForUser(USER_ID);
        verify(userRepository).save(professionalUser);
    }

    @Test
    @DisplayName("Should throw CONFLICT when user is already a USER (client)")
    void downgradeToClient_AlreadyClient() {
        // Arrange
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(testUser)); // testUser already has UserRole.USER

        // Act & Assert
        assertThatThrownBy(() -> userService.downgradeToClient(USER_ID))
                .isInstanceOf(AppException.class)
                .hasMessage("This account is already a client account.")
                .extracting(e -> ((AppException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT);

        verify(teamMemberService, never()).deactivateProfessionalLinksForUser(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw NOT FOUND when user does not exist on downgrade")
    void downgradeToClient_UserNotFound() {
        // Arrange
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> userService.downgradeToClient(USER_ID))
                .isInstanceOf(AppException.class)
                .hasMessage("User not found.")
                .extracting(e -> ((AppException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND);

        verify(teamMemberService, never()).deactivateProfessionalLinksForUser(any());
        verify(userRepository, never()).save(any());
    }

    // ==================== EMAIL VERIFICATION TESTS ====================

    @Test
    @DisplayName("Should verify email successfully when OTP is valid")
    void verifyEmail_Success() {
        when(otpService.isValidOtp(USER_ID, "123456")).thenReturn(true);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(testUser));

        userService.verifyEmail(USER_ID, "123456");

        assertThat(testUser.isEmailVerified()).isTrue();
        verify(userRepository).save(testUser);
    }

    @Test
    @DisplayName("Should throw exception when OTP is invalid or expired")
    void verifyEmail_InvalidOtp() {
        when(otpService.isValidOtp(USER_ID, "000000")).thenReturn(false);

        assertThatThrownBy(() -> userService.verifyEmail(USER_ID, "000000"))
                .isInstanceOf(AppException.class)
                .hasMessage("Invalid or expired verification code.")
                .extracting(e -> ((AppException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);

        verify(userRepository, never()).save(any());
    }

    // ==================== RESEND VERIFICATION TESTS ====================

    @Test
    @DisplayName("Should resend verification email successfully")
    void resendVerificationEmail_Success() {
        testUser.setEmailVerified(false);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(testUser));
        when(otpService.generateAndSaveOtp(USER_ID)).thenReturn("654321");

        userService.resendVerificationEmail(USER_ID);

        verify(otpService).generateAndSaveOtp(USER_ID);
        verify(emailService).sendVerificationEmail(testUser.getLogin(), "654321");
    }

    @Test
    @DisplayName("Should throw exception when trying to resend to an already verified user")
    void resendVerificationEmail_AlreadyVerified() {
        testUser.setEmailVerified(true);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(testUser));

        assertThatThrownBy(() -> userService.resendVerificationEmail(USER_ID))
                .isInstanceOf(AppException.class)
                .hasMessage("This email is already verified.")
                .extracting(e -> ((AppException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);

        verify(otpService, never()).generateAndSaveOtp(anyString());
        verify(emailService, never()).sendVerificationEmail(anyString(), anyString());
    }
}