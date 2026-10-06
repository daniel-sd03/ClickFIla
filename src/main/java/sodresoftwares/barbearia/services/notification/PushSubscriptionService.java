package sodresoftwares.barbearia.services.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sodresoftwares.barbearia.dto.push.PushSubscriptionDTO;
import sodresoftwares.barbearia.model.notification.UserPushSubscription;
import sodresoftwares.barbearia.model.user.User;
import sodresoftwares.barbearia.repositories.notification.UserPushSubscriptionRepository;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PushSubscriptionService {

    private final UserPushSubscriptionRepository repository;

    @Transactional
    public void subscribe(User user, PushSubscriptionDTO dto) {
        if (repository.existsByEndpoint(dto.endpoint())) {
            log.info("Device already registered for notifications.");
            return;
        }

        UserPushSubscription subscription = UserPushSubscription.builder()
                .user(user)
                .endpoint(dto.endpoint())
                .p256dh(dto.keys().p256dh())
                .auth(dto.keys().auth())
                .build();

        repository.save(subscription);
        log.info("New Web Push subscription");
    }
}