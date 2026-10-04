package sodresoftwares.barbearia.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("EmailService Tests")
class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @InjectMocks
    private EmailService emailService;

    private final String SENDER_EMAIL = "no-reply@clickfila.com.br";
    private final String TARGET_EMAIL = "user@test.com";
    private final String RESET_CODE = "123456";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(emailService, "sender", SENDER_EMAIL);
    }

    // ==================== SEND EMAIL TESTS ====================

    @Test
    @DisplayName("Should build and send password reset email successfully")
    void testSendPasswordResetEmail_Success() {
        // Act
        emailService.sendPasswordResetEmail(TARGET_EMAIL, RESET_CODE);

        // Assert
        ArgumentCaptor<SimpleMailMessage> messageCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(messageCaptor.capture());

        SimpleMailMessage capturedMessage = messageCaptor.getValue();

        assertThat(capturedMessage.getFrom()).isEqualTo(SENDER_EMAIL);

        assertThat(capturedMessage.getTo()).containsExactly(TARGET_EMAIL);

        assertThat(capturedMessage.getSubject()).isEqualTo("Recuperação de Senha - Click Fila");

        assertThat(capturedMessage.getText()).contains(RESET_CODE);
    }

    @Test
    @DisplayName("Should catch exception and not throw it upwards when mail sender fails")
    void testSendPasswordResetEmail_Failure_ShouldNotThrowException() {
        // Arrange
        doThrow(new MailSendException("SMTP Server Down"))
                .when(mailSender).send(any(SimpleMailMessage.class));

        // Act & Assert
        assertThatCode(() -> emailService.sendPasswordResetEmail(TARGET_EMAIL, RESET_CODE))
                .doesNotThrowAnyException();

        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    // ==================== SEND VERIFICATION EMAIL TESTS ====================

    @Test
    @DisplayName("Should build and send verification email successfully")
    void testSendVerificationEmail_Success() {
        // Act
        String verificationCode = "654321";
        emailService.sendVerificationEmail(TARGET_EMAIL, verificationCode);

        // Assert
        ArgumentCaptor<SimpleMailMessage> messageCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(messageCaptor.capture());

        SimpleMailMessage capturedMessage = messageCaptor.getValue();

        assertThat(capturedMessage.getFrom()).isEqualTo(SENDER_EMAIL);
        assertThat(capturedMessage.getTo()).containsExactly(TARGET_EMAIL);
        assertThat(capturedMessage.getSubject()).isEqualTo("Verificação de Conta - Click Fila");
        assertThat(capturedMessage.getText()).contains(verificationCode);
        assertThat(capturedMessage.getText()).contains("Bem-vindo ao Click Fila!");
    }

    @Test
    @DisplayName("Should catch exception and not throw it upwards when verification mail sender fails")
    void testSendVerificationEmail_Failure_ShouldNotThrowException() {
        // Arrange
        doThrow(new MailSendException("SMTP Server Down"))
                .when(mailSender).send(any(SimpleMailMessage.class));

        // Act & Assert
        assertThatCode(() -> emailService.sendVerificationEmail(TARGET_EMAIL, "654321"))
                .doesNotThrowAnyException();

        verify(mailSender).send(any(SimpleMailMessage.class));
    }
}