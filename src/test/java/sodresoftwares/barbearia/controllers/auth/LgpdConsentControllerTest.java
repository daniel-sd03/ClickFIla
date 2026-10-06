package sodresoftwares.barbearia.controllers.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientAutoConfiguration;
import org.springframework.boot.security.oauth2.client.autoconfigure.servlet.OAuth2ClientWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import sodresoftwares.barbearia.dto.auth.TokenResponseDTO;
import sodresoftwares.barbearia.infra.exception.GlobalExceptionHandler;
import sodresoftwares.barbearia.infra.security.SecurityFilter;
import sodresoftwares.barbearia.infra.security.SubscriptionCheckInterceptor;
import sodresoftwares.barbearia.infra.security.WebMvcConfig;
import sodresoftwares.barbearia.model.user.User;
import sodresoftwares.barbearia.services.auth.LgpdConsentService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = LgpdConsentController.class,
        excludeFilters = {
                @ComponentScan.Filter(
                        type = FilterType.ASSIGNABLE_TYPE,
                        classes = {
                                SecurityFilter.class,
                                SubscriptionCheckInterceptor.class,
                                WebMvcConfig.class,
                                GlobalExceptionHandler.class
                        }
                )
        },
        excludeAutoConfiguration = {
                OAuth2ClientAutoConfiguration.class,
                OAuth2ClientWebSecurityAutoConfiguration.class
        }
)
@ActiveProfiles("test")
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("LgpdConsentController Tests")
class LgpdConsentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LgpdConsentService lgpdConsentService;

    @MockitoBean
    private CacheManager cacheManager;

    private final String newToken = "new-fresh-jwt-token";
    private final String newRefreshToken = "new-refresh-token";
    private TokenResponseDTO mockResponse;

    @BeforeEach
    void setUp() {
        mockResponse = new TokenResponseDTO(
                newToken,
                newRefreshToken,
                "USER",
                false
        );
    }

    @Test
    @DisplayName("POST /api/v1/lgpd-consents -> Should accept terms and return new token (HTTP 200)")
    void testAcceptTerms_Success() throws Exception {
        // Arrange
        when(lgpdConsentService.acceptCurrentTerms(any(User.class), any())).thenReturn(mockResponse);

        // Act & Assert
        mockMvc.perform(post("/v1/lgpd-consents")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value(newToken))
                .andExpect(jsonPath("$.refreshToken").value(newRefreshToken))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.tutorialCompleted").value(false));;

        verify(lgpdConsentService).acceptCurrentTerms(any(User.class), any());
    }
}