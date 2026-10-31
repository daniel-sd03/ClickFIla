package sodresoftwares.barbearia.infra.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import sodresoftwares.barbearia.model.billing.Subscription;
import sodresoftwares.barbearia.model.billing.SubscriptionStatus;
import sodresoftwares.barbearia.model.user.User;
import sodresoftwares.barbearia.repositories.billing.SubscriptionRepository;

import java.time.LocalDate;
import java.time.ZoneId;

@Slf4j
@Component
@RequiredArgsConstructor
public class SubscriptionCheckInterceptor implements HandlerInterceptor {

    private final SubscriptionRepository subscriptionRepository;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            return true;
        }

        if (!(authentication.getPrincipal() instanceof User authenticatedUser)) {
            log.error("Security Anomaly: Expected Principal of type User, but got: {}",
                    authentication.getPrincipal() != null ? authentication.getPrincipal().getClass().getName() : "null");
            return true;
        }

        String userId = authenticatedUser.getId();

        Subscription subscription = subscriptionRepository.findByUserIdWithBusinessAndPlan(userId).orElse(null);

        if (subscription == null) {
            return true;
        }

        SubscriptionStatus status = subscription.getStatus();
        LocalDate periodEnd = subscription.getCurrentPeriodEnd();
        LocalDate today = LocalDate.now(ZoneId.of("America/Sao_Paulo"));

        boolean hasValidDays = periodEnd != null && !periodEnd.isBefore(today);

        if (status == SubscriptionStatus.SUSPENDED
                || status == SubscriptionStatus.PENDING
                || (status == SubscriptionStatus.CANCELED && !hasValidDays)) {

            log.warn("Access denied (402) for User {}. Subscription is {}. Valid days remaining: {}", userId, status, hasValidDays);

            response.setStatus(HttpStatus.PAYMENT_REQUIRED.value());
            response.setContentType("application/json");
            response.getWriter().write("""
                {
                    "error": "PAYMENT_REQUIRED", 
                    "message": "Your subscription is inactive or pending payment. Please settle your payment to access the business features."
                }
            """);
            return false;
        }

        return true;
    }
}