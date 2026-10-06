package sodresoftwares.barbearia.services.auth;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import sodresoftwares.barbearia.dto.auth.ResetPasswordDTO;
import sodresoftwares.barbearia.infra.exception.AppException;
import sodresoftwares.barbearia.model.auth.PasswordResetToken;
import sodresoftwares.barbearia.model.user.User;
import sodresoftwares.barbearia.repositories.auth.PasswordResetTokenRepository;
import sodresoftwares.barbearia.repositories.user.UserRepository;
import sodresoftwares.barbearia.services.notification.EmailService;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PasswordResetService Tests")
class PasswordResetServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordResetTokenRepository tokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private PasswordResetService passwordResetService;

    private final String EMAIL = "test@clickfila.com.br";
    private final String VALID_CODE = "123456";
    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id("user-123")
                .name("Test User")
                .login(EMAIL)
                .password("oldHashedPassword")
                .build();
    }

    // ==================== REQUEST PASSWORD RESET TESTS ====================

    @Test
    @DisplayName("Should generate token and send email when user exists")
    void testRequestPasswordReset_Success() {
        // Arrange
        when(userRepository.findByLogin(EMAIL)).thenReturn(testUser);

        // Act
        passwordResetService.requestPasswordReset(EMAIL);

        // Assert
        verify(tokenRepository).deleteByEmail(EMAIL);
        verify(tokenRepository).save(any(PasswordResetToken.class));

        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendPasswordResetEmail(eq(EMAIL), codeCaptor.capture());

        assertThat(codeCaptor.getValue()).hasSize(6);
    }

    @Test
    @DisplayName("Should do nothing silently when user does not exist on request reset")
    void testRequestPasswordReset_UserNotFound() {
        // Arrange
        when(userRepository.findByLogin(EMAIL)).thenReturn(null);

        // Act
        passwordResetService.requestPasswordReset(EMAIL);

        // Assert
        verify(tokenRepository, never()).deleteByEmail(anyString());
        verify(tokenRepository, never()).save(any(PasswordResetToken.class));
        verify(emailService, never()).sendPasswordResetEmail(anyString(), anyString());
    }

    // ==================== VALIDATE TOKEN TESTS ====================

    @Test
    @DisplayName("Should pass without exceptions when token is valid and not expired")
    void testValidateToken_Success() {
        // Arrange
        PasswordResetToken validToken = PasswordResetToken.builder()
                .email(EMAIL)
                .code(VALID_CODE)
                .expiryDate(Instant.now().plus(10, ChronoUnit.MINUTES))
                .build();

        when(tokenRepository.findByEmailAndCode(EMAIL, VALID_CODE)).thenReturn(Optional.of(validToken));

        // Act & Assert
        Assertions.assertDoesNotThrow(
                () -> passwordResetService.validateToken(EMAIL, VALID_CODE)
        );
    }

    @Test
    @DisplayName("Should throw exception when token is not found (invalid code)")
    void testValidateToken_InvalidCode() {
        // Arrange
        when(tokenRepository.findByEmailAndCode(EMAIL, VALID_CODE)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> passwordResetService.validateToken(EMAIL, VALID_CODE))
                .isInstanceOf(AppException.class)
                .hasMessage("Invalid or incorrect code.")
                .extracting(e -> ((AppException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Should throw exception when token is expired")
    void testValidateToken_ExpiredCode() {
        // Arrange
        PasswordResetToken expiredToken = PasswordResetToken.builder()
                .email(EMAIL)
                .code(VALID_CODE)
                .expiryDate(Instant.now().minus(5, ChronoUnit.MINUTES))
                .build();

        when(tokenRepository.findByEmailAndCode(EMAIL, VALID_CODE)).thenReturn(Optional.of(expiredToken));

        // Act & Assert
        assertThatThrownBy(() -> passwordResetService.validateToken(EMAIL, VALID_CODE))
                .isInstanceOf(AppException.class)
                .hasMessage("This code has expired. Please request a new one.")
                .extracting(e -> ((AppException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ==================== RESET PASSWORD TESTS ====================

    @Test
    @DisplayName("Should update password successfully when all data is correct")
    void testResetPassword_Success() {
        // Arrange
        ResetPasswordDTO dto = new ResetPasswordDTO(EMAIL, VALID_CODE, "NewPassword123", "NewPassword123");

        PasswordResetToken validToken = PasswordResetToken.builder()
                .email(EMAIL)
                .code(VALID_CODE)
                .expiryDate(Instant.now().plus(10, ChronoUnit.MINUTES))
                .build();

        when(tokenRepository.findByEmailAndCode(EMAIL, VALID_CODE)).thenReturn(Optional.of(validToken));
        when(userRepository.findByLogin(EMAIL)).thenReturn(testUser);
        when(passwordEncoder.encode("NewPassword123")).thenReturn("encodedNewPassword");

        // Act
        passwordResetService.resetPassword(dto);

        // Assert
        assertThat(testUser.getPassword()).isEqualTo("encodedNewPassword");
        verify(userRepository).save(testUser);
        verify(tokenRepository).deleteByEmail(EMAIL);
    }

    @Test
    @DisplayName("Should throw exception when password and confirmation do not match")
    void testResetPassword_PasswordsDoNotMatch() {
        // Arrange
        ResetPasswordDTO dto = new ResetPasswordDTO(EMAIL, VALID_CODE, "NewPassword123", "DifferentPassword");

        // Act & Assert
        assertThatThrownBy(() -> passwordResetService.resetPassword(dto))
                .isInstanceOf(AppException.class)
                .hasMessage("New password and confirmation do not match.")
                .extracting(e -> ((AppException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);

        verify(tokenRepository, never()).findByEmailAndCode(anyString(), anyString());
    }

    @Test
    @DisplayName("Should throw exception when user is not found on reset password")
    void testResetPassword_UserNotFound() {
        // Arrange
        ResetPasswordDTO dto = new ResetPasswordDTO(EMAIL, VALID_CODE, "NewPassword123", "NewPassword123");

        PasswordResetToken validToken = PasswordResetToken.builder()
                .email(EMAIL)
                .code(VALID_CODE)
                .expiryDate(Instant.now().plus(10, ChronoUnit.MINUTES))
                .build();

        when(tokenRepository.findByEmailAndCode(EMAIL, VALID_CODE)).thenReturn(Optional.of(validToken));
        when(userRepository.findByLogin(EMAIL)).thenReturn(null);

        // Act & Assert
        assertThatThrownBy(() -> passwordResetService.resetPassword(dto))
                .isInstanceOf(AppException.class)
                .hasMessage("User not found.")
                .extracting(e -> ((AppException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}