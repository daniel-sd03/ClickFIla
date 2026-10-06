package sodresoftwares.barbearia.services.auth;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import sodresoftwares.barbearia.dto.auth.TokenResponseDTO;
import sodresoftwares.barbearia.infra.security.TokenService;
import sodresoftwares.barbearia.model.auth.LgpdConsent;
import sodresoftwares.barbearia.model.auth.RefreshToken;
import sodresoftwares.barbearia.model.user.User;
import sodresoftwares.barbearia.model.user.UserRole;
import sodresoftwares.barbearia.repositories.auth.LgpdConsentRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("LgpdConsentService Tests")
class LgpdConsentServiceTest {

    @Mock
    private LgpdConsentRepository lgpdConsentRepository;

    @Mock
    private TokenService tokenService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private RefreshTokenService refreshTokenService;

    @InjectMocks
    private LgpdConsentService lgpdConsentService;

    private User testUser;
    private final String CURRENT_VERSION = "2.0";
    private RefreshToken mockRefresh;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(lgpdConsentService, "currentLgpdVersion", CURRENT_VERSION);
        mockRefresh = RefreshToken.builder().token("mock-refresh-token").build();
        testUser = User.builder()
                .id("user-123")
                .login("test@test.com")
                .role(UserRole.USER)
                .build();
    }

    // ==================== ACCEPT CURRENT TERMS TESTS ====================

    @Test
    @DisplayName("Should generate token WITHOUT saving when user already accepted the current terms")
    void acceptCurrentTerms_AlreadyAccepted() {
        // Arrange
        when(lgpdConsentRepository.existsByUserIdAndTermVersion(testUser.getId(), CURRENT_VERSION)).thenReturn(true);
        when(tokenService.generateToken(testUser, CURRENT_VERSION)).thenReturn("mock-token");
        when(refreshTokenService.generateNewRefreshToken(testUser)).thenReturn(mockRefresh);

        // Act
        TokenResponseDTO response = lgpdConsentService.acceptCurrentTerms(testUser, request);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.token()).isEqualTo("mock-token");
        assertThat(response.refreshToken()).isEqualTo("mock-refresh-token");
        assertThat(response.role()).isEqualTo(testUser.getRole().toString());
        assertThat(response.tutorialCompleted()).isFalse();

        verify(lgpdConsentRepository, never()).save(any(LgpdConsent.class));
        verify(tokenService).generateToken(testUser, CURRENT_VERSION);
    }

    @Test
    @DisplayName("Should save consent and generate token when user has NOT accepted current terms (using Cloudflare IP)")
    void acceptCurrentTerms_NotAccepted_CloudflareIp() {
        // Arrange
        when(lgpdConsentRepository.existsByUserIdAndTermVersion(testUser.getId(), CURRENT_VERSION)).thenReturn(false);
        when(request.getHeader("CF-Connecting-IP")).thenReturn("192.168.1.100");
        when(request.getHeader("User-Agent")).thenReturn("Mozilla/5.0");
        when(tokenService.generateToken(testUser, CURRENT_VERSION)).thenReturn("mock-token");
        when(refreshTokenService.generateNewRefreshToken(testUser)).thenReturn(mockRefresh);

        // Act
        TokenResponseDTO response = lgpdConsentService.acceptCurrentTerms(testUser, request);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.token()).isEqualTo("mock-token");
        assertThat(response.refreshToken()).isEqualTo("mock-refresh-token");
        assertThat(response.role()).isEqualTo(testUser.getRole().toString());
        assertThat(response.tutorialCompleted()).isFalse();

        ArgumentCaptor<LgpdConsent> consentCaptor = ArgumentCaptor.forClass(LgpdConsent.class);
        verify(lgpdConsentRepository).save(consentCaptor.capture());

        LgpdConsent savedConsent = consentCaptor.getValue();
        assertThat(savedConsent.getUserId()).isEqualTo(testUser.getId());
        assertThat(savedConsent.getTermVersion()).isEqualTo(CURRENT_VERSION);
        assertThat(savedConsent.getIpAddress()).isEqualTo("192.168.1.100");
        assertThat(savedConsent.getUserAgent()).isEqualTo("Mozilla/5.0");
    }

    // ==================== REGISTER CONSENT FOR NEW USER TESTS ====================

    @Test
    @DisplayName("Should save consent for new user silently (using standard RemoteAddr)")
    void registerConsentForNewUser_StandardIp() {
        // Arrange
        when(request.getHeader("CF-Connecting-IP")).thenReturn(null);
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("10.0.0.5");
        when(request.getHeader("User-Agent")).thenReturn("Postman");

        // Act
        lgpdConsentService.registerConsentForNewUser(testUser, request);

        // Assert
        ArgumentCaptor<LgpdConsent> consentCaptor = ArgumentCaptor.forClass(LgpdConsent.class);
        verify(lgpdConsentRepository).save(consentCaptor.capture());

        LgpdConsent savedConsent = consentCaptor.getValue();
        assertThat(savedConsent.getUserId()).isEqualTo(testUser.getId());
        assertThat(savedConsent.getTermVersion()).isEqualTo(CURRENT_VERSION);
        assertThat(savedConsent.getIpAddress()).isEqualTo("10.0.0.5");
        assertThat(savedConsent.getUserAgent()).isEqualTo("Postman");

        verifyNoInteractions(tokenService);
    }
}