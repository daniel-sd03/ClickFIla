package sodresoftwares.barbearia.services.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from}")
    private String sender;

    @Async
    public void sendPasswordResetEmail(String to, String code) {
        try {

            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(sender);
            message.setTo(to);
            message.setSubject("Recuperação de Senha - Click Fila");
            message.setText("Olá!\n\nVocê solicitou a recuperação de senha.\n" +
                    "Seu código de segurança é: " + code + "\n\n" +
                    "Este código é válido por 15 minutos.\n" +
                    "Se você não solicitou esta alteração, por favor ignore este e-mail.");

            long startTime  = System.currentTimeMillis();
            mailSender.send(message);
            log.info("Password reset email sent successfully in {} ms",
                    System.currentTimeMillis() - startTime );

        } catch (Exception e) {
            log.error("Failed to send password reset email", e);
        }
    }

    @Async
    public void sendVerificationEmail(String to, String code) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(sender);
            message.setTo(to);
            message.setSubject("Verificação de Conta - Click Fila");
            message.setText("Olá!\n\n" +
                    "Bem-vindo ao Click Fila! Para liberar a criação de filas e times na sua conta, " +
                    "use o código de verificação abaixo:\n\n" +
                    code + "\n\n" +
                    "Este código é válido por 15 minutos.\n" +
                    "Equipe Click Fila");

            long startTime = System.currentTimeMillis();
            mailSender.send(message);
            log.info("Verification email sent successfully in {} ms", System.currentTimeMillis() - startTime);

        } catch (Exception e) {
            log.error("Failed to send verification email", e);
        }
    }
}