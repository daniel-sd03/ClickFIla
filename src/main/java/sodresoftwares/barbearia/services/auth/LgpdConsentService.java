package sodresoftwares.barbearia.services.auth;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sodresoftwares.barbearia.dto.auth.TokenResponseDTO;
import sodresoftwares.barbearia.infra.security.TokenService;
import sodresoftwares.barbearia.model.auth.LgpdConsent;
import sodresoftwares.barbearia.model.auth.RefreshToken;
import sodresoftwares.barbearia.model.user.User;
import sodresoftwares.barbearia.repositories.auth.LgpdConsentRepository;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LgpdConsentService {

    private final LgpdConsentRepository lgpdConsentRepository;
    private final TokenService tokenService;
    private final RefreshTokenService refreshTokenService;

    @Value("${app.lgpd.current-version}")
    private String currentLgpdVersion;

    @Transactional
    public TokenResponseDTO acceptCurrentTerms(User user, HttpServletRequest request) {
        boolean alreadyAccepted = lgpdConsentRepository.existsByUserIdAndTermVersion(user.getId(), currentLgpdVersion);

        if (!alreadyAccepted) {
            saveConsentToDatabase(user, request);
            log.info(
                    "LGPD consent accepted for version {}",
                    currentLgpdVersion
            );
        }

        String token = tokenService.generateToken(user, currentLgpdVersion);
        RefreshToken refreshToken = refreshTokenService.generateNewRefreshToken(user);
        String role = user.getRole().toString();
        boolean tutorialCompleted = user.getTutorialCompleted();

        return new TokenResponseDTO(
                token,
                refreshToken.getToken(),
                role,tutorialCompleted
        );
    }

    @Transactional
    public void registerConsentForNewUser(User user, HttpServletRequest request) {
        saveConsentToDatabase(user, request);
        log.info(
                "LGPD consent registered for version {}",
                currentLgpdVersion
        );
    }

    private void saveConsentToDatabase(User user, HttpServletRequest request) {
        String ipAddress = extractClientIp(request);
        String userAgent = request.getHeader("User-Agent");

        LgpdConsent consent = LgpdConsent.builder()
                .userId(user.getId())
                .termVersion(currentLgpdVersion)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .build();

        lgpdConsentRepository.save(consent);
    }

    private String extractClientIp(HttpServletRequest request) {
        String cloudflareIp = request.getHeader("CF-Connecting-IP");
        if (cloudflareIp != null && !cloudflareIp.isEmpty()) {
            return cloudflareIp;
        }

        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }

        return request.getRemoteAddr();
    }
}