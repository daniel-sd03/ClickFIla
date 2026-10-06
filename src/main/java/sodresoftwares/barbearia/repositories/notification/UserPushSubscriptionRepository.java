package sodresoftwares.barbearia.repositories.notification;

import org.springframework.data.jpa.repository.JpaRepository;
import sodresoftwares.barbearia.model.notification.UserPushSubscription;
import java.util.List;

public interface UserPushSubscriptionRepository extends JpaRepository<UserPushSubscription, String> {

    boolean existsByEndpoint(String endpoint);

    List<UserPushSubscription> findAllByUserId(String userId);
}