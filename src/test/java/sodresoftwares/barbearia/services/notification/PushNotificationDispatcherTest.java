package sodresoftwares.barbearia.services.notification;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import sodresoftwares.barbearia.model.notification.UserPushSubscription;
import sodresoftwares.barbearia.repositories.notification.UserPushSubscriptionRepository;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PushNotificationDispatcher Tests")
class PushNotificationDispatcherTest {

    @Mock
    private UserPushSubscriptionRepository pushRepository;

    @Mock
    private WebPushService webPushService;

    @InjectMocks
    private PushNotificationDispatcher dispatcher;

    private UserPushSubscription subscription1;
    private UserPushSubscription subscription2;
    private final String USER_ID = "user-123";

    @BeforeEach
    void setUp() {
        subscription1 = UserPushSubscription.builder()
                .endpoint("endpoint-1")
                .p256dh("p256dh-1")
                .auth("auth-1")
                .build();

        subscription2 = UserPushSubscription.builder()
                .endpoint("endpoint-2")
                .p256dh("p256dh-2")
                .auth("auth-2")
                .build();
    }

    // ==================== NOTIFY CLIENT TURN ====================

    @Test
    @DisplayName("Should format correct client turn message and delegate to notifyUser")
    void testNotifyClientTurn() {
        // Arrange
        when(pushRepository.findAllByUserId(USER_ID)).thenReturn(List.of(subscription1));

        String expectedTitle = "É a sua vez! 🎉";
        String expectedMessage = "Chegou a sua vez na fila! Dirija-se ao local de atendimento (Zé).";
        String expectedJson = """
                {
                    "title": "%s",
                    "body": "%s"
                }
                """.formatted(expectedTitle, expectedMessage);

        // Act
        dispatcher.notifyClientTurn(USER_ID, "Zé");

        // Assert
        verify(webPushService).sendPushNotification("endpoint-1", "p256dh-1", "auth-1", expectedJson);
    }

    // ==================== NOTIFY USER ====================

    @Test
    @DisplayName("Should not call WebPushService when user has no subscriptions")
    void testNotifyUser_NoSubscriptions() {
        // Arrange
        when(pushRepository.findAllByUserId(USER_ID)).thenReturn(List.of());

        // Act
        dispatcher.notifyUser(USER_ID, "Title", "Message");

        // Assert
        verify(pushRepository).findAllByUserId(USER_ID);
        verify(webPushService, never()).sendPushNotification(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Should format JSON payload and call WebPushService for all subscriptions")
    void testNotifyUser_WithSubscriptions() {
        // Arrange
        when(pushRepository.findAllByUserId(USER_ID)).thenReturn(List.of(subscription1, subscription2));

        String expectedJson = """
                {
                    "title": "My Title",
                    "body": "My Message"
                }
                """;

        // Act
        dispatcher.notifyUser(USER_ID, "My Title", "My Message");

        // Assert
        verify(webPushService).sendPushNotification("endpoint-1", "p256dh-1", "auth-1", expectedJson);
        verify(webPushService).sendPushNotification("endpoint-2", "p256dh-2", "auth-2", expectedJson);
    }

    // ==================== NOTIFY CANCELLATION ====================

    @Test
    @DisplayName("Should format cancellation message and delegate to notifyUser")
    void testNotifyCancellation() {
        // Arrange
        when(pushRepository.findAllByUserId(USER_ID)).thenReturn(List.of(subscription1));

        String expectedTitle = "Atendimento cancelado ❌";
        String expectedMessage = "Sua posição na fila foi cancelada. Se precisar, você pode entrar na fila novamente pelo app.";
        String expectedJson = """
                {
                    "title": "%s",
                    "body": "%s"
                }
                """.formatted(expectedTitle, expectedMessage);

        // Act
        dispatcher.notifyCancellation(USER_ID);

        // Assert
        verify(webPushService).sendPushNotification("endpoint-1", "p256dh-1", "auth-1", expectedJson);
    }

    // ==================== NOTIFY REALLOCATION ====================

    @Test
    @DisplayName("Should format reallocation message and delegate to notifyUser")
    void testNotifyReallocation() {
        // Arrange
        when(pushRepository.findAllByUserId(USER_ID)).thenReturn(List.of(subscription1));

        String expectedTitle = "Posição atualizada 🔄";
        String expectedMessage = "Sua posição na fila precisou ser realocada. Acompanhe pelo aplicativo para ver quando será sua vez!";
        String expectedJson = """
                {
                    "title": "%s",
                    "body": "%s"
                }
                """.formatted(expectedTitle, expectedMessage);

        // Act
        dispatcher.notifyReallocation(USER_ID);

        // Assert
        verify(webPushService).sendPushNotification("endpoint-1", "p256dh-1", "auth-1", expectedJson);
    }
}