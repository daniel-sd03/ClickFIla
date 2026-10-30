package sodresoftwares.barbearia.dto.billing;

public record CheckoutResponseDTO(
        String gatewaySubscriptionId,
        String pixQrCodeBase64,
        String pixCopiaECola
) {}
