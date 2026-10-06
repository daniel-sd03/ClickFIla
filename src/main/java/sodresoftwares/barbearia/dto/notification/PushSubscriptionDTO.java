package sodresoftwares.barbearia.dto.notification;

public record PushSubscriptionDTO(
        String endpoint,
        Keys keys
) {
    public record Keys(
            String p256dh,
            String auth
    ) {}
}