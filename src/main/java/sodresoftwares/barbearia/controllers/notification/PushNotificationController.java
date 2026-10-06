package sodresoftwares.barbearia.controllers.notification;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import sodresoftwares.barbearia.dto.push.PushSubscriptionDTO;
import sodresoftwares.barbearia.model.user.User;
import sodresoftwares.barbearia.services.notification.PushSubscriptionService;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class PushNotificationController {

    private final PushSubscriptionService pushSubscriptionService;

    @PostMapping("/subscribe")
    public ResponseEntity<Void> subscribe(
            @AuthenticationPrincipal User loggedUser,
            @RequestBody PushSubscriptionDTO dto) {
        pushSubscriptionService.subscribe(loggedUser, dto);
        return ResponseEntity.ok().build();
    }
}