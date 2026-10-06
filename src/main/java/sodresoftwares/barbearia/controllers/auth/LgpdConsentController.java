package sodresoftwares.barbearia.controllers.auth;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import sodresoftwares.barbearia.dto.auth.TokenResponseDTO;
import sodresoftwares.barbearia.model.user.User;
import sodresoftwares.barbearia.services.auth.LgpdConsentService;

@RestController
@RequestMapping("/v1/lgpd-consents")
@RequiredArgsConstructor
public class LgpdConsentController {

    private final LgpdConsentService lgpdConsentService;

    @PostMapping
    public ResponseEntity<TokenResponseDTO> acceptTerms(
            @AuthenticationPrincipal User user,
            HttpServletRequest request) {
        TokenResponseDTO response = lgpdConsentService.acceptCurrentTerms(user, request);
        return ResponseEntity.ok(response);
    }
}