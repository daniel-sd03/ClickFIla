package sodresoftwares.barbearia.services.notification;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import nl.martijndwars.webpush.Encoding;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.apache.http.HttpResponse;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.Security;

@Slf4j
@Service
public class WebPushService {

    @Value("${webpush.vapid.public.key}")
    private String publicKey;

    @Value("${webpush.vapid.private.key}")
    private String privateKey;

    @Value("${webpush.vapid.subject}")
    private String subject;

    private PushService pushService;

    @PostConstruct
    private void init() throws Exception {
        Security.addProvider(new BouncyCastleProvider());

        this.pushService = new PushService(publicKey, privateKey, subject);
    }

    public void sendPushNotification(String endpoint, String p256dh, String auth, String jsonPayload) {
        try {
            Notification notification = new Notification(endpoint, p256dh, auth, jsonPayload);

            HttpResponse response = pushService.send(notification, Encoding.AES128GCM);

            int statusCode = response.getStatusLine().getStatusCode();

            if (statusCode == 201) {
                log.info("Push notification sent successfully to endpoint: {}", endpoint);
            } else {
                log.warn("Failed to send push. Google FCM returned status: {}", statusCode);
            }

        } catch (Exception e) {
            log.error("Failed to send push notification", e);
        }
    }
}