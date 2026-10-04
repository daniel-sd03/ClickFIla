package sodresoftwares.barbearia.infra.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("EmailVerificationInterceptor Tests")
class EmailVerificationInterceptorTest {

    @InjectMocks
    private EmailVerificationInterceptor interceptor;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    private StringWriter responseWriter;

    @BeforeEach
    void setUp() throws Exception {
        responseWriter = new StringWriter();
    }

    private String generateTestToken(String role, Boolean emailVerified) {
        return JWT.create()
                .withClaim("role", role)
                .withClaim("email_verified", emailVerified)
                .sign(Algorithm.none());
    }

    @Test
    @DisplayName("Should let USER (client) pass through without checking email")
    void shouldLetUserPassThrough() throws Exception {
        String token = generateTestToken("USER", false);
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);

        boolean result = interceptor.preHandle(request, response, new Object());

        assertThat(result).isTrue();
        verifyNoInteractions(response);
    }

    @Test
    @DisplayName("Should let VERIFIED PROFESSIONAL pass through")
    void shouldLetVerifiedProfessionalPassThrough() throws Exception {
        String token = generateTestToken("PROFESSIONAL", true);
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);

        boolean result = interceptor.preHandle(request, response, new Object());

        assertThat(result).isTrue();
        verifyNoInteractions(response);
    }

    @Test
    @DisplayName("Should BLOCK UNVERIFIED PROFESSIONAL and return 403 Forbidden")
    void shouldBlockUnverifiedProfessional() throws Exception {
        String token = generateTestToken("PROFESSIONAL", false);
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(response.getWriter()).thenReturn(new PrintWriter(responseWriter));

        boolean result = interceptor.preHandle(request, response, new Object());

        assertThat(result).isFalse();
        verify(response).setStatus(403);
        verify(response).setContentType("application/json");
        assertThat(responseWriter.toString()).contains("EMAIL_NOT_VERIFIED");
    }

    @Test
    @DisplayName("Should let pass through if no Authorization header is present (let SecurityFilter handle it)")
    void shouldPassIfNoHeader() throws Exception {
        when(request.getHeader("Authorization")).thenReturn(null);

        boolean result = interceptor.preHandle(request, response, new Object());

        assertThat(result).isTrue();
        verifyNoInteractions(response);
    }
}