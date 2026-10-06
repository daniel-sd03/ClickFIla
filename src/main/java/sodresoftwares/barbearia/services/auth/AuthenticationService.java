package sodresoftwares.barbearia.services.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sodresoftwares.barbearia.dto.auth.AuthenticationDTO;
import sodresoftwares.barbearia.dto.auth.TokenResponseDTO;
import sodresoftwares.barbearia.infra.exception.AppException;
import sodresoftwares.barbearia.infra.security.TokenService;
import sodresoftwares.barbearia.model.auth.RefreshToken;
import sodresoftwares.barbearia.model.user.User;
import sodresoftwares.barbearia.model.auth.LgpdConsent;
import sodresoftwares.barbearia.repositories.auth.LgpdConsentRepository;
import sodresoftwares.barbearia.services.user.UserService;

import java.util.Objects;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthenticationService {

    private final AuthenticationManager authenticationManager;
    private final TokenService tokenService;
    private final LgpdConsentRepository lgpdConsentRepository;
    private final UserService userService;
    private final RefreshTokenService refreshTokenService;

    @Transactional
    public TokenResponseDTO login(AuthenticationDTO data) {
        User loggedUser = authenticateUser(data);

        if (!loggedUser.getIsActive()) {
            throw new AppException(
                    HttpStatus.FORBIDDEN,
                    "ACCOUNT_DEACTIVATED",
                    "This account is deactivated. Do you want to reactivate?"
            );
        }

        return buildTokenResponse(loggedUser);
    }

    @Transactional
    public TokenResponseDTO reactivateAndLogin(AuthenticationDTO data) {
        User loggedUser = authenticateUser(data);

        userService.reactivateAccount(loggedUser.getId());

        return buildTokenResponse(loggedUser);
    }

    private User authenticateUser(AuthenticationDTO data) {
        var usernamePassword = new UsernamePasswordAuthenticationToken(data.login(), data.password());
        var auth = this.authenticationManager.authenticate(usernamePassword);

        return (User) Objects.requireNonNull(auth.getPrincipal());
    }

    private TokenResponseDTO buildTokenResponse(User loggedUser) {
        try {
            MDC.put("userId", loggedUser.getId());
            log.info("User authenticated successfully");

            String lgpdLastAcceptedVersion = lgpdConsentRepository.findFirstByUserIdOrderByCreatedAtDesc(loggedUser.getId())
                    .map(LgpdConsent::getTermVersion)
                    .orElse(null);

            String token = tokenService.generateToken(loggedUser, lgpdLastAcceptedVersion);
            RefreshToken refreshToken = refreshTokenService.generateNewRefreshToken(loggedUser);
            String role = loggedUser.getRole().toString();
            boolean tutorialCompleted = loggedUser.getTutorialCompleted();

            return new TokenResponseDTO(
                    token,
                    refreshToken.getToken(),
                    role,
                    tutorialCompleted
            );
        } finally {
            MDC.remove("userId");
        }
    }
}