package sodresoftwares.barbearia.services.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sodresoftwares.barbearia.dto.auth.ResetPasswordDTO;
import sodresoftwares.barbearia.infra.exception.AppException;
import sodresoftwares.barbearia.model.auth.PasswordResetToken;
import sodresoftwares.barbearia.model.user.User;
import sodresoftwares.barbearia.repositories.auth.PasswordResetTokenRepository;
import sodresoftwares.barbearia.repositories.user.UserRepository;
import sodresoftwares.barbearia.services.notification.EmailService;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PasswordResetService {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    private static final int EXPIRATION_MINUTES = 15;

    @Transactional
    public void requestPasswordReset(String email) {
        var userDetails = userRepository.findByLogin(email);

        if (userDetails != null) {
            tokenRepository.deleteByEmail(email);

            String code = generateSixDigitCode();

            PasswordResetToken token = PasswordResetToken.builder()
                    .email(email)
                    .code(code)
                    .expiryDate(Instant.now().plus(EXPIRATION_MINUTES, ChronoUnit.MINUTES))
                    .build();

            tokenRepository.save(token);

            log.info("Password reset requested");

            emailService.sendPasswordResetEmail(email, code);
        }
    }

    @Transactional(readOnly = true)
    public void validateToken(String email, String code) {
        PasswordResetToken token = tokenRepository.findByEmailAndCode(email, code)
                .orElseThrow(() -> new AppException(
                        HttpStatus.BAD_REQUEST,
                        "INVALID_CODE",
                        "Invalid or incorrect code."
                ));

        if (token.getExpiryDate().isBefore(Instant.now())) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "EXPIRED_CODE",
                    "This code has expired. Please request a new one."
            );
        }
    }

    @Transactional
    public void resetPassword(ResetPasswordDTO dto) {
        if (!dto.newPassword().equals(dto.confirmPassword())) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "PASSWORDS_DO_NOT_MATCH",
                    "New password and confirmation do not match."
            );
        }

        validateToken(dto.email(), dto.code());

        var userDetails = userRepository.findByLogin(dto.email());
        if (userDetails == null) {
            throw new AppException(
                    HttpStatus.NOT_FOUND,
                    "USER_NOT_FOUND",
                    "User not found."
            );
        }

        User user = (User) userDetails;
        user.setPassword(passwordEncoder.encode(dto.newPassword()));
        userRepository.save(user);

        tokenRepository.deleteByEmail(dto.email());
        log.info("Password reset completed successfully");
    }


    private String generateSixDigitCode() {
        SecureRandom random = new SecureRandom();
        int number = 100000 + random.nextInt(900000);
        return String.valueOf(number);
    }
}