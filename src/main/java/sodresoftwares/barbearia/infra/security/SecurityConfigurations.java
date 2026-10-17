package sodresoftwares.barbearia.infra.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import sodresoftwares.barbearia.infra.logging.RequestLoggingFilter;
import sodresoftwares.barbearia.infra.security.oauth2.OAuth2AuthenticationSuccessHandler;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfigurations {

    private final SecurityFilter securityFilter;
    private final RequestLoggingFilter requestLoggingFilter;
    private final CustomAuthenticationEntryPoint customAuthenticationEntryPoint;
    private final OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity httpSecurity) throws Exception {
        return httpSecurity
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize

                        // ==========================================
                        // 1. PUBLIC ENDPOINTS (No authentication required)
                        // ==========================================
                        .requestMatchers(HttpMethod.POST, "/auth/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/auth/refresh").permitAll()
                        .requestMatchers(HttpMethod.POST, "/users/client").permitAll()
                        .requestMatchers(HttpMethod.POST, "/users/professional").permitAll()
                        .requestMatchers(HttpMethod.POST, "/auth/password-resets").permitAll()
                        .requestMatchers(HttpMethod.POST, "/auth/password-resets/validate").permitAll()
                        .requestMatchers(HttpMethod.PATCH, "/auth/passwords").permitAll()
                        .requestMatchers(HttpMethod.POST, "/auth/reactivate").permitAll()
                        .requestMatchers(HttpMethod.POST, "/webhooks/asaas").permitAll()
                        .requestMatchers(HttpMethod.GET, "/plans").permitAll() // <-- ROTA DE PLANOS PÚBLICA
                        .requestMatchers("/oauth2/**", "/barbearia/oauth2/**").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/ws/**").permitAll()
                        .requestMatchers("/actuator/**").permitAll()

                        // ==========================================
                        // 2. PROFESSIONAL ONLY ENDPOINTS
                        // ==========================================
                        // Business Management
                        .requestMatchers(HttpMethod.POST, "/businesses").hasRole("PROFESSIONAL")
                        .requestMatchers(HttpMethod.GET, "/businesses/me").hasRole("PROFESSIONAL")
                        .requestMatchers(HttpMethod.PATCH, "/businesses/me").hasRole("PROFESSIONAL")
                        .requestMatchers(HttpMethod.PUT, "/businesses/deactivate").hasRole("PROFESSIONAL") // <-- NOVA ROTA
                        .requestMatchers(HttpMethod.PUT, "/businesses/reactivate").hasRole("PROFESSIONAL") // <-- NOVA ROTA
                        .requestMatchers(HttpMethod.POST, "/subscriptions/checkout").hasRole("PROFESSIONAL")
                        .requestMatchers(HttpMethod.POST, "/subscriptions/cancel/*").hasRole("PROFESSIONAL")
                        .requestMatchers(HttpMethod.GET, "/subscriptions/me").hasRole("PROFESSIONAL")

                        // Dashboard
                        .requestMatchers(HttpMethod.GET, "/dashboard/professional").hasRole("PROFESSIONAL")

                        // Queue Session Management
                        .requestMatchers(HttpMethod.POST, "/queue-sessions").hasRole("PROFESSIONAL")
                        .requestMatchers(HttpMethod.POST, "/queue-sessions/me/ticket-code").hasRole("PROFESSIONAL")
                        .requestMatchers(HttpMethod.PATCH, "/queue-sessions/me").hasRole("PROFESSIONAL")
                        .requestMatchers(HttpMethod.PATCH, "/queue-sessions/me/status").hasRole("PROFESSIONAL")

                        // Queue Entry Management
                        .requestMatchers(HttpMethod.POST, "/queue-entries/sessions/*/next").hasRole("PROFESSIONAL")
                        .requestMatchers(HttpMethod.PATCH, "/queue-entries/*/start").hasRole("PROFESSIONAL")
                        .requestMatchers(HttpMethod.PATCH, "/queue-entries/*/finish").hasRole("PROFESSIONAL")
                        .requestMatchers(HttpMethod.PATCH, "/queue-entries/*/requeue").hasRole("PROFESSIONAL")

                        // User Role Management
                        .requestMatchers(HttpMethod.PATCH, "/users/me/downgrade-to-client").hasRole("PROFESSIONAL")

                        // ==========================================
                        // 3. ANY OTHER REQUEST
                        // ==========================================
                        .anyRequest().authenticated()
                )
                .oauth2Login(oauth2 -> oauth2
                        .successHandler(oAuth2AuthenticationSuccessHandler)
                )
                .exceptionHandling(exception ->
                        exception.authenticationEntryPoint(customAuthenticationEntryPoint)
                )
                .addFilterBefore(securityFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(requestLoggingFilter, SecurityFilter.class)
                .build();
    }

    @Bean
    public RoleHierarchy roleHierarchy() {
        return RoleHierarchyImpl.withDefaultRolePrefix()
                .role("ADMIN").implies("PROFESSIONAL")
                .role("PROFESSIONAL").implies("USER")
                .build();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}