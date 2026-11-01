package sodresoftwares.barbearia.dto.billing;

public record CreditCardSummaryDTO(
        String id,
        String brand,
        String lastFourDigits
) {}