package sodresoftwares.barbearia.infra.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import sodresoftwares.barbearia.infra.security.SubscriptionCheckInterceptor;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final SubscriptionCheckInterceptor subscriptionCheckInterceptor;
    private final EmailVerificationInterceptor emailVerificationInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(emailVerificationInterceptor)
                .addPathPatterns(
                        "/businesses/**",
                        "/queue-sessions/**",
                        "/queue-entries/**",
                        "/team-members/**"
                );

        registry.addInterceptor(subscriptionCheckInterceptor)
                .addPathPatterns(
                        "/queue-sessions/**",
                        "/queue-entries/**",
                        "/team-members/**"
                );
    }
}