package sodresoftwares.barbearia.infra.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;
import sodresoftwares.barbearia.model.user.User;
import sodresoftwares.barbearia.model.user.UserRole;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("TokenService Tests")
class TokenServiceTest {

    @InjectMocks
    private TokenService tokenService;

    private User testUser;
    private final String TEST_SECRET = "test-secret-key-for-testing-purposes-only";
    private final String TEST_LGPD_VERSION = "1.0";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(tokenService, "secret", TEST_SECRET);

        testUser = User.builder()
                .id("user-123")
                .login("test@example.com")
                .role(UserRole.USER)
                .build();
    }

    @Test
    @DisplayName("Should generate token successfully")
    void shouldGenerateTokenSuccessfully() {
        String token = tokenService.generateToken(testUser, TEST_LGPD_VERSION);

        assertThat(token).isNotNull();
        assertThat(token).isNotEmpty();
        assertThat(token).contains(".");
    }

    @Test
    @DisplayName("Should validate and decode correct token successfully")
    void shouldValidateCorrectTokenSuccessfully() {
        testUser.setEmailVerified(true);

        String token = tokenService.generateToken(testUser, TEST_LGPD_VERSION);

        DecodedJWT decodedJWT = tokenService.validateAndDecodeToken(token);

        assertThat(decodedJWT).isNotNull();
        assertThat(decodedJWT.getSubject()).isEqualTo("test@example.com");
        assertThat(decodedJWT.getClaim("user_id").asString()).isEqualTo("user-123");
        assertThat(decodedJWT.getClaim("role").asString()).isEqualTo("USER");
        assertThat(decodedJWT.getClaim("lgpd_version").asString()).isEqualTo(TEST_LGPD_VERSION);
        assertThat(decodedJWT.getClaim("email_verified").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("Should return null for invalid token")
    void shouldReturnNullForInvalidToken() {
        DecodedJWT result = tokenService.validateAndDecodeToken("invalid.token.here");
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("Should return null for tampered token")
    void shouldReturnNullForTamperedToken() {
        String token = tokenService.generateToken(testUser, TEST_LGPD_VERSION);
        String tamperedToken = token.substring(0, token.length() - 10) + "0123456789";

        DecodedJWT result = tokenService.validateAndDecodeToken(tamperedToken);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("Should return null for empty token")
    void shouldReturnNullForEmptyToken() {
        DecodedJWT result = tokenService.validateAndDecodeToken("");
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("Should return null for null token")
    void shouldReturnNullForNullToken() {
        DecodedJWT result = tokenService.validateAndDecodeToken(null);
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("Should have expiration date in the future")
    void shouldHaveExpirationDateInTheFuture() {
        String token = tokenService.generateToken(testUser, TEST_LGPD_VERSION);
        Algorithm algorithm = Algorithm.HMAC256(TEST_SECRET);

        Instant expiresAt = JWT.require(algorithm)
                .withIssuer("auth-api")
                .build()
                .verify(token)
                .getExpiresAtAsInstant();

        assertThat(expiresAt).isAfter(Instant.now());
    }

    @Test
    @DisplayName("Should return UsernamePasswordAuthenticationToken for valid DecodedJWT")
    void shouldReturnAuthenticationFromDecodedJWT() {
        // Arrange
        DecodedJWT decodedJWT = mock(DecodedJWT.class);
        com.auth0.jwt.interfaces.Claim userIdClaim = mock(com.auth0.jwt.interfaces.Claim.class);
        com.auth0.jwt.interfaces.Claim roleClaim = mock(com.auth0.jwt.interfaces.Claim.class);

        when(decodedJWT.getSubject()).thenReturn("test@example.com");

        when(decodedJWT.getClaim("user_id")).thenReturn(userIdClaim);
        when(userIdClaim.asString()).thenReturn("user-123");

        when(decodedJWT.getClaim("role")).thenReturn(roleClaim);
        when(roleClaim.asString()).thenReturn("USER");

        // Act
        UsernamePasswordAuthenticationToken auth = tokenService.getAuthentication(decodedJWT);

        // Assert
        assertThat(auth).isNotNull();
        User authUser = (User) auth.getPrincipal();
        assertThat(authUser.getId()).isEqualTo("user-123");
        assertThat(authUser.getLogin()).isEqualTo("test@example.com");
        assertThat(authUser.getRole().name()).isEqualTo("USER");
    }
}