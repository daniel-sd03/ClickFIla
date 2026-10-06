package sodresoftwares.barbearia.controllers.notification;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientAutoConfiguration;
import org.springframework.boot.security.oauth2.client.autoconfigure.servlet.OAuth2ClientWebSecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.json.AutoConfigureJsonTesters;
import org.springframework.boot.test.json.JacksonTester;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import sodresoftwares.barbearia.dto.notification.PushSubscriptionDTO;
import sodresoftwares.barbearia.infra.exception.GlobalExceptionHandler;
import sodresoftwares.barbearia.infra.security.SecurityFilter;
import sodresoftwares.barbearia.infra.security.SubscriptionCheckInterceptor;
import sodresoftwares.barbearia.infra.security.WebMvcConfig;
import sodresoftwares.barbearia.services.notification.PushSubscriptionService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = PushNotificationController.class,
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
@AutoConfigureJsonTesters
@DisplayName("PushNotificationController Tests")
class PushNotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JacksonTester<Object> jsonTester;

    @MockitoBean
    private PushSubscriptionService pushSubscriptionService;

    @MockitoBean
    private CacheManager cacheManager;

    private PushSubscriptionDTO testDto;

    @BeforeEach
    void setUp() {
        PushSubscriptionDTO.Keys keys = new PushSubscriptionDTO.Keys("test-p256dh", "test-auth");
        testDto = new PushSubscriptionDTO("https://fcm.googleapis.com/test-endpoint", keys);
    }

    @Test
    @DisplayName("POST /notifications/subscribe -> Should return 200 OK when subscription is processed")
    void testSubscribe_Success() throws Exception {
        // Arrange
        doNothing().when(pushSubscriptionService).subscribe(any(), any(PushSubscriptionDTO.class));

        // Act & Assert
        mockMvc.perform(post("/notifications/subscribe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonTester.write(testDto).getJson()))
                .andExpect(status().isOk());

        verify(pushSubscriptionService).subscribe(any(), any(PushSubscriptionDTO.class));
    }
}