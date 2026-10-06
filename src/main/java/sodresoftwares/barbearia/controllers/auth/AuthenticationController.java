package sodresoftwares.barbearia.controllers.auth;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import sodresoftwares.barbearia.dto.auth.*;
import sodresoftwares.barbearia.services.auth.AuthenticationService;
import sodresoftwares.barbearia.services.auth.PasswordResetService;
import sodresoftwares.barbearia.services.auth.RefreshTokenService;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthenticationController {

	private final AuthenticationService authenticationService;
	private final PasswordResetService passwordResetService;
	private final RefreshTokenService refreshTokenService;

	@PostMapping("/login")
	public ResponseEntity<TokenResponseDTO> login(
			@RequestBody @Valid AuthenticationDTO data) {
		TokenResponseDTO response = authenticationService.login(data);
		return ResponseEntity.ok(response);
	}

	@PostMapping("/password-resets")
	public ResponseEntity<Void> requestPasswordReset(@Valid @RequestBody ForgotPasswordDTO dto) {
		passwordResetService.requestPasswordReset(dto.email());
		return ResponseEntity.ok().build();
	}

	@PostMapping("/password-resets/validate")
	public ResponseEntity<Void> validateResetToken(@Valid @RequestBody ValidateTokenDTO dto) {
		passwordResetService.validateToken(dto.email(), dto.code());
		return ResponseEntity.ok().build();
	}

	@PatchMapping("/passwords")
	public ResponseEntity<Void> updatePassword(@Valid @RequestBody ResetPasswordDTO dto) {
		passwordResetService.resetPassword(dto);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/reactivate")
	public ResponseEntity<TokenResponseDTO> reactivate(@RequestBody @Valid AuthenticationDTO data) {
		return ResponseEntity.ok(authenticationService.reactivateAndLogin(data));
	}

	@PostMapping("/refresh")
	public ResponseEntity<TokenRefreshResponseDTO> refreshAccessToken(@Valid @RequestBody TokenRefreshRequestDTO request) {
		TokenRefreshResponseDTO response = refreshTokenService.processRefreshToken(request.refreshToken());
		return ResponseEntity.ok(response);
	}
}
