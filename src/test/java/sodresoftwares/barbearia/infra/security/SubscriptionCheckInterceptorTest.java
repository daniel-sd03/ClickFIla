package sodresoftwares.barbearia.infra.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import sodresoftwares.barbearia.model.billing.Subscription;
import sodresoftwares.barbearia.model.billing.SubscriptionStatus;
import sodresoftwares.barbearia.model.user.User;
import sodresoftwares.barbearia.repositories.billing.SubscriptionRepository;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SubscriptionCheckInterceptor Tests")
class SubscriptionCheckInterceptorTest {

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private Authentication authentication;

    @Mock
    private SecurityContext securityContext;

    @InjectMocks
    private SubscriptionCheckInterceptor interceptor;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private User testUser;
    private final String USER_ID = "user-123";

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();

        testUser = User.builder()
                .id(USER_ID)
                .login("barbeiro@teste.com")
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ==================== PUBLIC ROUTES AND SECURITY ====================

    @Test
    @DisplayName("Should allow request when authentication is null")
    void shouldAllowWhenAuthenticationIsNull() throws Exception {
        when(securityContext.getAuthentication()).thenReturn(null);
        SecurityContextHolder.setContext(securityContext);

        boolean result = interceptor.preHandle(request, response, new Object());

        assertThat(result).isTrue();
        verify(subscriptionRepository, never()).findByUserIdWithBusinessAndPlan(any());
    }

    @Test
    @DisplayName("Should allow request when user is not authenticated")
    void shouldAllowWhenNotAuthenticated() throws Exception {
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);
        when(authentication.isAuthenticated()).thenReturn(false);

        boolean result = interceptor.preHandle(request, response, new Object());

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("Should allow request when principal is anonymousUser")
    void shouldAllowWhenAnonymousUser() throws Exception {
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn("anonymousUser");

        boolean result = interceptor.preHandle(request, response, new Object());

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("Should log error and allow request when principal is not a User object (Security Anomaly)")
    void shouldAllowAndLogWhenPrincipalIsNotUser() throws Exception {
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn("SomeStringPrincipal");

        boolean result = interceptor.preHandle(request, response, new Object());

        assertThat(result).isTrue();
        verify(subscriptionRepository, never()).findByUserIdWithBusinessAndPlan(any());
    }

    // ==================== SUBSCRIPTION VALIDATIONS ====================

    @Test
    @DisplayName("Should allow request when subscription is ACTIVE")
    void shouldAllowWhenSubscriptionIsActive() throws Exception {
        setupAuthenticatedUser();
        Subscription activeSub = createSubscription(SubscriptionStatus.ACTIVE, LocalDate.now().plusDays(10));
        when(subscriptionRepository.findByUserIdWithBusinessAndPlan(USER_ID)).thenReturn(Optional.of(activeSub));

        boolean result = interceptor.preHandle(request, response, new Object());

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("Should allow request when subscription is TRIAL")
    void shouldAllowWhenSubscriptionIsTrial() throws Exception {
        setupAuthenticatedUser();
        Subscription trialSub = createSubscription(SubscriptionStatus.TRIAL, LocalDate.now().plusDays(5));
        when(subscriptionRepository.findByUserIdWithBusinessAndPlan(USER_ID)).thenReturn(Optional.of(trialSub));

        boolean result = interceptor.preHandle(request, response, new Object());

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("Should allow request when subscription is not found (null status)")
    void shouldAllowWhenSubscriptionNotFound() throws Exception {
        setupAuthenticatedUser();
        when(subscriptionRepository.findByUserIdWithBusinessAndPlan(USER_ID)).thenReturn(Optional.empty());

        boolean result = interceptor.preHandle(request, response, new Object());

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("Should allow request when subscription is CANCELED but still has valid days")
    void shouldAllowWhenSubscriptionIsCanceledButHasValidDays() throws Exception {
        setupAuthenticatedUser();
        Subscription canceledSubValidDays = createSubscription(SubscriptionStatus.CANCELED,
                LocalDate.now(ZoneId.of("America/Sao_Paulo")).plusDays(5));
        when(subscriptionRepository.findByUserIdWithBusinessAndPlan(USER_ID)).thenReturn(Optional.of(canceledSubValidDays));

        boolean result = interceptor.preHandle(request, response, new Object());

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("Should block request with 402 when subscription is CANCELED and valid days expired")
    void shouldBlockWhenSubscriptionIsCanceledAndExpired() throws Exception {
        setupAuthenticatedUser();
        Subscription canceledSubExpired = createSubscription(SubscriptionStatus.CANCELED,
                LocalDate.now(ZoneId.of("America/Sao_Paulo")).minusDays(1));
        when(subscriptionRepository.findByUserIdWithBusinessAndPlan(USER_ID)).thenReturn(Optional.of(canceledSubExpired));

        boolean result = interceptor.preHandle(request, response, new Object());

        assertBlockedResponse(result);
    }

    @Test
    @DisplayName("Should block request with 402 when subscription is SUSPENDED")
    void shouldBlockWhenSubscriptionIsSuspended() throws Exception {
        setupAuthenticatedUser();
        Subscription suspendedSub = createSubscription(SubscriptionStatus.SUSPENDED, LocalDate.now());
        when(subscriptionRepository.findByUserIdWithBusinessAndPlan(USER_ID)).thenReturn(Optional.of(suspendedSub));

        boolean result = interceptor.preHandle(request, response, new Object());

        assertBlockedResponse(result);
    }

    @Test
    @DisplayName("Should block request with 402 when subscription is PENDING")
    void shouldBlockWhenSubscriptionIsPending() throws Exception {
        setupAuthenticatedUser();
        Subscription pendingSub = createSubscription(SubscriptionStatus.PENDING, LocalDate.now().plusDays(30));
        when(subscriptionRepository.findByUserIdWithBusinessAndPlan(USER_ID)).thenReturn(Optional.of(pendingSub));

        boolean result = interceptor.preHandle(request, response, new Object());

        assertBlockedResponse(result);
    }

    // ==================== UTILITY METHODS ====================

    private void setupAuthenticatedUser() {
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn(testUser);
    }

    private Subscription createSubscription(SubscriptionStatus status, LocalDate periodEnd) {
        return Subscription.builder()
                .status(status)
                .currentPeriodEnd(periodEnd)
                .build();
    }

    private void assertBlockedResponse(boolean result) throws Exception {
        assertThat(result).isFalse();
        assertThat(response.getStatus()).isEqualTo(HttpStatus.PAYMENT_REQUIRED.value());
        assertThat(response.getContentType()).isEqualTo("application/json");

        String jsonResponse = response.getContentAsString();
        assertThat(jsonResponse).contains("PAYMENT_REQUIRED");
        assertThat(jsonResponse).contains("Your subscription is inactive or pending payment.");
    }
}