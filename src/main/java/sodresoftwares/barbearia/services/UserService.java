package sodresoftwares.barbearia.services;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sodresoftwares.barbearia.dto.auth.ChangePasswordDTO;
import sodresoftwares.barbearia.dto.auth.RegisterDTO;
import sodresoftwares.barbearia.dto.user.UpdateUserDTO;
import sodresoftwares.barbearia.dto.user.UserResponseDTO;
import sodresoftwares.barbearia.infra.exception.AppException;
import sodresoftwares.barbearia.model.user.User;
import sodresoftwares.barbearia.model.user.UserRole;
import sodresoftwares.barbearia.repositories.UserRepository;
import sodresoftwares.barbearia.services.auth.OtpService;

import java.time.Instant;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final LgpdConsentService lgpdConsentService;
    private final TeamMemberService teamMemberService;
    private final OtpService otpService;
    private final EmailService emailService;

    @Transactional(readOnly = true)
    public UserResponseDTO getMyProfile(String userId) {
        User user = getUserById(userId);
        return UserResponseDTO.fromEntity(user);
    }

    @Transactional
    public User registerClient(RegisterDTO data, HttpServletRequest request) {
        return createUser(data, UserRole.USER,request);
    }

    @Transactional
    public User registerProfessional(RegisterDTO data, HttpServletRequest request) {
        return createUser(data, UserRole.PROFESSIONAL, request);
    }

    @Transactional
    public User createUser(RegisterDTO data, UserRole role, HttpServletRequest request) {
        if (this.userRepository.existsByLogin(data.login())) {
            throw new AppException(
                    HttpStatus.CONFLICT,
                    "USER_ALREADY_EXISTS",
                    "User already exists");
        }

        String encryptedPassword = passwordEncoder.encode(data.password());

        User newUser = User.builder()
                .login(data.login())
                .password(encryptedPassword)
                .name(data.name())
                .phone(data.phone())
                .role(role)
                .build();

        User savedUser = userRepository.save(newUser);
        lgpdConsentService.registerConsentForNewUser(savedUser, request);

        if (role == UserRole.PROFESSIONAL) {
            generateAndSendOtp(savedUser);
        }

        log.info("User registered with role {}", savedUser.getRole());
        return savedUser;
    }

    @Transactional
    public UserResponseDTO upgradeToProfessional(String loggedUserId) {
        User user = getUserById(loggedUserId);

        if (user.getRole() == UserRole.PROFESSIONAL) {
            throw new AppException(
                    HttpStatus.CONFLICT,
                    "ALREADY_PROFESSIONAL",
                    "This account is already a professional account."
            );
        }

        user.setRole(UserRole.PROFESSIONAL);
        User savedUser = userRepository.save(user);

        if (!savedUser.isEmailVerified()) {
            generateAndSendOtp(savedUser);
            log.info("Verification email sent");
        }

        log.info("User {} upgraded to PROFESSIONAL role", loggedUserId);

        return UserResponseDTO.fromEntity(savedUser);
    }

    @Transactional
    public UserResponseDTO updateUserProfile(String loggedUserId, UpdateUserDTO dto) {
        User user = getUserById(loggedUserId);

        if (dto.name() != null && !dto.name().isBlank()) {
            user.setName(dto.name().trim());
        }

        if (dto.phone() != null) {
            user.setPhone(dto.phone().trim());
        }

        User updatedUser = userRepository.save(user);

        log.info("User updated successfully");
        return UserResponseDTO.fromEntity(updatedUser);
    }

    @Transactional
    public void changePassword(String loggedUserId, ChangePasswordDTO dto) {
        User user = getUserById(loggedUserId);

        if (!passwordEncoder.matches(dto.currentPassword(), user.getPassword())) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_CURRENT_PASSWORD",
                    "Current password does not match."
            );
        }

        if (!dto.newPassword().equals(dto.confirmPassword())) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "PASSWORDS_DO_NOT_MATCH",
                    "New password and confirmation do not match."
            );
        }

        user.setPassword(passwordEncoder.encode(dto.newPassword()));
        userRepository.save(user);

        log.info("Password changed successfully");
    }

    @Transactional
    public void deleteMyAccount(String loggedUserId) {
        User user = getUserById(loggedUserId);

        if (!user.getIsActive()) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "ALREADY_DELETED",
                    "This account is already deactivated."
            );
        }

        if (user.getRole() == UserRole.PROFESSIONAL) {
            teamMemberService.deactivateProfessionalLinksForUser(loggedUserId);
        }

        user.setIsActive(false);
        user.setDeletedAt(Instant.now());

        userRepository.save(user);

        log.info("Account deactivated successfully. Permanent deletion pending.");
    }

    @Transactional
    public void reactivateAccount(String loggedUserId) {
        User user = getUserById(loggedUserId);

        if (user.getIsActive()) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "ALREADY_ACTIVE",
                    "This account is already active."
            );
        }

        user.setIsActive(true);
        user.setDeletedAt(null);

        userRepository.save(user);

        log.info("Account reactivated successfully.");
    }

    @Transactional
    public void markTutorialAsCompleted(String loggedUserId) {
        User user = getUserById(loggedUserId);

        user.setTutorialCompleted(true);
        userRepository.save(user);

        log.info("Tutorial marked as completed");
    }

    @Transactional
    public UserResponseDTO downgradeToClient(String loggedUserId) {
        User user = getUserById(loggedUserId);

        if (user.getRole() == UserRole.USER) {
            throw new AppException(
                    HttpStatus.CONFLICT,
                    "ALREADY_CLIENT",
                    "This account is already a client account."
            );
        }

        teamMemberService.deactivateProfessionalLinksForUser(loggedUserId);

        user.setRole(UserRole.USER);

        User savedUser = userRepository.save(user);
        log.info("User downgraded to USER role");

        return UserResponseDTO.fromEntity(savedUser);
    }

    @Transactional
    public void verifyEmail(String userId, String otpCode) {
        boolean isValid = otpService.isValidOtp(userId, otpCode);

        if (!isValid) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_OTP",
                    "Invalid or expired verification code."
            );
        }

        User user = getUserById(userId);
        user.setEmailVerified(true);
        userRepository.save(user);

        log.info("User {} successfully verified their email", userId);
    }

    public void resendVerificationEmail(String userId) {
        User user = getUserById(userId);

        if (user.isEmailVerified()) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "ALREADY_VERIFIED",
                    "This email is already verified."
            );
        }

        generateAndSendOtp(user);

        log.info("Verification email resent for user {}", userId);
    }


    //--------- HELPER METHODS ------

    private User getUserById(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new AppException(
                        HttpStatus.NOT_FOUND,
                        "USER_NOT_FOUND",
                        "User not found."
                ));
    }

    private void generateAndSendOtp(User user) {
        String otp = otpService.generateAndSaveOtp(user.getId());
        emailService.sendVerificationEmail(user.getLogin(), otp);
    }
}