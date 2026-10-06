package sodresoftwares.barbearia.infra.security.oauth2;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import sodresoftwares.barbearia.infra.security.TokenService;
import sodresoftwares.barbearia.model.auth.RefreshToken;
import sodresoftwares.barbearia.model.user.User;
import sodresoftwares.barbearia.model.user.UserRole;
import sodresoftwares.barbearia.model.auth.LgpdConsent;
import sodresoftwares.barbearia.repositories.auth.LgpdConsentRepository;
import sodresoftwares.barbearia.repositories.user.UserRepository;
import sodresoftwares.barbearia.services.auth.RefreshTokenService;

import java.io.IOException;
import java.util.UUID;

@Component
@Slf4j
@RequiredArgsConstructor
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    final private TokenService tokenService;
    final private UserRepository userRepository;
    private final LgpdConsentRepository lgpdConsentRepository;
    private final RefreshTokenService refreshTokenService;

    @Value("${app.security.oauth2-redirect}")
    private String frontendUrl;

    @Value("${app.security.cookie.domain}")
    private String cookieDomain;

    @Value("${app.security.cookie.secure}")
    private boolean cookieSecure;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {

        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        String email = oAuth2User.getAttribute("email");
        String googleId = oAuth2User.getAttribute("sub");
        String name = oAuth2User.getAttribute("name");

        if (name == null || name.isBlank()) {
            name = oAuth2User.getAttribute("given_name");
        }
        if (name == null || name.isBlank()) {
            name = email.split("@")[0];
        }

        User finalUser = userRepository.findByGoogleId(googleId);
        String lgpdLastAcceptedVersion = null;

        // Scenario 1: Existing Google Auth user
        if (finalUser != null) {
            if (!finalUser.getLogin().equals(email)) {
                finalUser.setLogin(email);
                userRepository.save(finalUser);
                log.info("Google account email synchronized");
            }
            lgpdLastAcceptedVersion = fetchLastLgpdVersion(finalUser.getId());
        }
        else {
            User userByEmail = (User) userRepository.findByLogin(email);

            // Scenario 2: Existing standard user logging in with Google for the first time
            if (userByEmail != null) {
                finalUser = userByEmail;
                finalUser.setGoogleId(googleId);
                finalUser.setEmailVerified(true);
                userRepository.save(finalUser);
                log.info("Google account linked successfully");

                lgpdLastAcceptedVersion = fetchLastLgpdVersion(finalUser.getId());
            }
            // Scenario 3: Brand new user
            else {
                finalUser = User.builder()
                        .login(email)
                        .name(name)
                        .googleId(googleId)
                        .password(UUID.randomUUID().toString())
                        .role(UserRole.USER)
                        .emailVerified(true)
                        .build();
                userRepository.save(finalUser);
                log.info("New user registered via Google OAuth2");
            }
        }

        String jwtToken = tokenService.generateToken(finalUser, lgpdLastAcceptedVersion);
        RefreshToken refreshToken = refreshTokenService.generateNewRefreshToken(finalUser);

        Cookie tokenCookie = new Cookie("TEMP_AUTH_TOKEN", jwtToken);
        tokenCookie.setPath("/");
        tokenCookie.setMaxAge(60);
        tokenCookie.setSecure(cookieSecure);
        if (cookieDomain != null && !cookieDomain.isBlank()) {
            tokenCookie.setDomain(cookieDomain);
        }
        response.addCookie(tokenCookie);

        Cookie refreshTokenCookie = new Cookie("TEMP_REFRESH_TOKEN", refreshToken.getToken());
        refreshTokenCookie.setPath("/");
        refreshTokenCookie.setMaxAge(60);
        refreshTokenCookie.setSecure(cookieSecure);
        if (cookieDomain != null && !cookieDomain.isBlank()) {
            refreshTokenCookie.setDomain(cookieDomain);
        }
        response.addCookie(refreshTokenCookie);

        Cookie roleCookie = new Cookie("TEMP_ROLE", finalUser.getRole().name());
        roleCookie.setPath("/");
        roleCookie.setMaxAge(60);
        roleCookie.setSecure(cookieSecure);
        if (cookieDomain != null && !cookieDomain.isBlank()) {
            roleCookie.setDomain(cookieDomain);
        }
        response.addCookie(roleCookie);

        Cookie tutorialCookie = new Cookie("TEMP_TUTORIAL_COMPLETED", String.valueOf(finalUser.getTutorialCompleted()));
        tutorialCookie.setPath("/");
        tutorialCookie.setMaxAge(60);
        tutorialCookie.setSecure(cookieSecure);
        if (cookieDomain != null && !cookieDomain.isBlank()) {
            tutorialCookie.setDomain(cookieDomain);
        }
        response.addCookie(tutorialCookie);

        log.info("OAuth2 authentication completed successfully for user {}", finalUser.getId());
        getRedirectStrategy().sendRedirect(request, response, frontendUrl);
    }

    private String fetchLastLgpdVersion(String userId) {
        return lgpdConsentRepository.findFirstByUserIdOrderByCreatedAtDesc(userId)
                .map(LgpdConsent::getTermVersion)
                .orElse(null);
    }
}