package sodresoftwares.barbearia.services.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import sodresoftwares.barbearia.model.notification.UserPushSubscription;
import sodresoftwares.barbearia.repositories.notification.UserPushSubscriptionRepository;

import java.util.List;


@Service
@Slf4j
@RequiredArgsConstructor
@Async
public class PushNotificationDispatcher {

    private final UserPushSubscriptionRepository pushRepository;
    private final WebPushService webPushService;

    public void notifyClientTurn(String userId, String attendantOrPlaceName) {
        String title = "É a sua vez! 🎉";
        String message = "Chegou a sua vez na fila! Dirija-se ao local de atendimento ("
                + attendantOrPlaceName + ").";

        notifyUser(userId, title, message);
    }

    public void notifyCancellation(String userId) {
        String title = "Atendimento cancelado ❌";
        String message = "Sua posição na fila foi cancelada. Se precisar, você pode" +
                " entrar na fila novamente pelo app.";

        notifyUser(userId, title, message);
    }

    public void notifyReallocation(String userId) {
        String title = "Posição atualizada 🔄";
        String message = "Sua posição na fila precisou ser realocada. Acompanhe" +
                " pelo aplicativo para ver quando será sua vez!";

        notifyUser(userId, title, message);
    }

    public void notifyUser(String userId, String title, String message) {
        List<UserPushSubscription> subscriptions = pushRepository.findAllByUserId(userId);

        if (subscriptions.isEmpty()) {
            log.info("No active push subscriptions found");
            return;
        }

        String jsonPayload = """
                {
                    "title": "%s",
                    "body": "%s"
                }
                """.formatted(title, message);

        for (UserPushSubscription sub : subscriptions) {
            webPushService.sendPushNotification(
                    sub.getEndpoint(),
                    sub.getP256dh(),
                    sub.getAuth(),
                    jsonPayload
            );
        }
    }
}