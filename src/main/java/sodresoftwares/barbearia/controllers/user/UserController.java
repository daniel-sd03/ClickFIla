package sodresoftwares.barbearia.controllers.user;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import sodresoftwares.barbearia.dto.auth.ChangePasswordDTO;
import sodresoftwares.barbearia.dto.auth.RegisterDTO;
import sodresoftwares.barbearia.dto.user.UpdateUserDTO;
import sodresoftwares.barbearia.dto.user.UserResponseDTO;
import sodresoftwares.barbearia.dto.user.VerifyEmailDTO;
import sodresoftwares.barbearia.model.user.User;
import sodresoftwares.barbearia.services.user.UserService;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<UserResponseDTO> getMyProfile(
            @AuthenticationPrincipal User loggedInUser) {
        UserResponseDTO profile = userService.getMyProfile(loggedInUser.getId());
        return ResponseEntity.ok(profile);
    }

    @PostMapping("/client")
    public ResponseEntity<Void> register(
            @RequestBody @Valid RegisterDTO data,
            HttpServletRequest request) {
        userService.registerClient(data,request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping("/professional")
    public ResponseEntity<Void> registerProfessional(
            @RequestBody @Valid RegisterDTO data,
            HttpServletRequest request) {
        userService.registerProfessional(data, request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping("/verify-email")
    public ResponseEntity<Void> verifyEmail(
            @AuthenticationPrincipal User loggedUser,
            @Valid @RequestBody VerifyEmailDTO dto) {
        userService.verifyEmail(loggedUser.getId(), dto.code());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/resend-otp")
    public ResponseEntity<Void> resendOtp(
            @AuthenticationPrincipal User loggedUser) {
        userService.resendVerificationEmail(loggedUser.getId());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/me/upgrade-role")
    public ResponseEntity<UserResponseDTO> upgradeToProfessional(
            @AuthenticationPrincipal User loggedInUser) {
        UserResponseDTO updatedUser = userService.upgradeToProfessional(loggedInUser.getId());
        return ResponseEntity.ok(updatedUser);
    }

    @PatchMapping("/me")
    public ResponseEntity<UserResponseDTO> updateMyProfile(
            @AuthenticationPrincipal User loggedInUser,
            @Valid @RequestBody UpdateUserDTO dto) {
        UserResponseDTO updatedProfile = userService.updateUserProfile(loggedInUser.getId(), dto);
        return ResponseEntity.ok(updatedProfile);
    }

    @PatchMapping("/me/password")
    public ResponseEntity<Void> changeMyPassword(
            @AuthenticationPrincipal User loggedInUser,
            @Valid @RequestBody ChangePasswordDTO dto) {
        userService.changePassword(loggedInUser.getId(), dto);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/me/tutorial")
    public ResponseEntity<Void> completeTutorial(@AuthenticationPrincipal User loggedUser) {
        userService.markTutorialAsCompleted(loggedUser.getId());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/me/downgrade-to-client")
    public ResponseEntity<UserResponseDTO> downgradeToClient(@AuthenticationPrincipal User loggedUser) {
        return ResponseEntity.ok(userService.downgradeToClient(loggedUser.getId()));
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteMyAccount(@AuthenticationPrincipal User loggedUser) {
        userService.deleteMyAccount(loggedUser.getId());
        return ResponseEntity.noContent().build();
    }
}