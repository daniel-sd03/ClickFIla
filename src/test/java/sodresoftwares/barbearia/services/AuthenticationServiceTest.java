package sodresoftwares.barbearia.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import sodresoftwares.barbearia.dto.auth.AuthenticationDTO;
import sodresoftwares.barbearia.dto.auth.TokenResponseDTO;
import sodresoftwares.barbearia.infra.exception.AppException;
import sodresoftwares.barbearia.infra.security.TokenService;
import sodresoftwares.barbearia.model.auth.LgpdConsent;
import sodresoftwares.barbearia.model.auth.RefreshToken;
import sodresoftwares.barbearia.model.user.User;
import sodresoftwares.barbearia.model.user.UserRole;
import sodresoftwares.barbearia.repositories.auth.LgpdConsentRepository;
import sodresoftwares.barbearia.services.auth.AuthenticationService;
import sodresoftwares.barbearia.services.auth.RefreshTokenService;
import sodresoftwares.barbearia.services.user.UserService;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthenticationService Tests")
class AuthenticationServiceTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private TokenService tokenService;

    @Mock
    private LgpdConsentRepository lgpdConsentRepository;

    @Mock
    private UserService userService;

    @Mock
    private RefreshTokenService refreshTokenService;

    @InjectMocks
    private AuthenticationService authService;

    private User testUser;
    private AuthenticationDTO authDTO;
    private RefreshToken mockRefresh;

    @BeforeEach
    void setUp() {
        String TEST_LOGIN = "user@test.com";
        testUser = User.builder()
                .id("user-123")
                .login(TEST_LOGIN)
                .password("$2a$10$encodedPasswordHash...")
                .name("Fulano da Silva")
                .phone("11999999999")
                .role(UserRole.USER)
                .isActive(true)
                .build();

        mockRefresh = RefreshToken.builder().token("valid-refresh-token").build();

        String RAW_PASSWORD = "password123";
        authDTO = new AuthenticationDTO(TEST_LOGIN, RAW_PASSWORD);
    }

    // ==================== LOGIN TESTS ====================

    @Test
    @DisplayName("Should login user successfully and return JWT token")
    void testLogin_Successful() {
        // Arrange
        UsernamePasswordAuthenticationToken authenticatedToken = new UsernamePasswordAuthenticationToken(
                testUser, null, testUser.getAuthorities()
        );

        String fakeLgpdVersion = "1.0";
        LgpdConsent mockConsent = LgpdConsent.builder().termVersion(fakeLgpdVersion).build();

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authenticatedToken);
        when(lgpdConsentRepository.findFirstByUserIdOrderByCreatedAtDesc(testUser.getId()))
                .thenReturn(Optional.of(mockConsent));
        when(tokenService.generateToken(testUser, fakeLgpdVersion))
                .thenReturn("valid-jwt-token");
        when(refreshTokenService.generateNewRefreshToken(testUser)).thenReturn(mockRefresh);

        // Act
        TokenResponseDTO result = authService.login(authDTO);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.token()).isEqualTo("valid-jwt-token");
        assertThat(result.role()).isEqualTo(UserRole.USER.toString());
        assertThat(result.tutorialCompleted()).isFalse();

        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
        verify(lgpdConsentRepository).findFirstByUserIdOrderByCreatedAtDesc(testUser.getId());
        verify(tokenService).generateToken(testUser, fakeLgpdVersion);
    }

    @Test
    @DisplayName("Should throw exception when login credentials are invalid")
    void testLogin_InvalidCredentials() {
        // Arrange
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Invalid credentials"));

        // Act & Assert
        assertThatThrownBy(() -> authService.login(authDTO))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid credentials");

        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
        verify(tokenService, never()).generateToken(any(), any());
    }

    @Test
    @DisplayName("Should throw exception when trying to login with deactivated account")
    void testLogin_AccountDeactivated() {
        testUser.setIsActive(false); // Simulando conta desativada
        UsernamePasswordAuthenticationToken authenticatedToken = new UsernamePasswordAuthenticationToken(
                testUser, null, testUser.getAuthorities()
        );
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authenticatedToken);

        assertThatThrownBy(() -> authService.login(authDTO))
                .isInstanceOf(AppException.class)
                .hasMessage("This account is deactivated. Do you want to reactivate?")
                .extracting(e -> ((AppException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN);

        verify(tokenService, never()).generateToken(any(), any());
    }

    // ==================== REACTIVATE AND LOGIN TESTS ====================

    @Test
    @DisplayName("Should reactivate user and return JWT token")
    void testReactivateAndLogin_Successful() {
        UsernamePasswordAuthenticationToken authenticatedToken = new UsernamePasswordAuthenticationToken(
                testUser, null, testUser.getAuthorities()
        );
        String fakeLgpdVersion = "1.0";
        LgpdConsent mockConsent = LgpdConsent.builder().termVersion(fakeLgpdVersion).build();

        when(authenticationManager.authenticate(any())).thenReturn(authenticatedToken);
        doNothing().when(userService).reactivateAccount(testUser.getId());
        when(lgpdConsentRepository.findFirstByUserIdOrderByCreatedAtDesc(testUser.getId())).thenReturn(Optional.of(mockConsent));
        when(tokenService.generateToken(testUser, fakeLgpdVersion)).thenReturn("valid-jwt-token");
        when(refreshTokenService.generateNewRefreshToken(testUser)).thenReturn(mockRefresh);

        TokenResponseDTO result = authService.reactivateAndLogin(authDTO);

        assertThat(result.token()).isEqualTo("valid-jwt-token");
        assertThat(result.tutorialCompleted()).isFalse();
        verify(userService).reactivateAccount(testUser.getId());
    }
}