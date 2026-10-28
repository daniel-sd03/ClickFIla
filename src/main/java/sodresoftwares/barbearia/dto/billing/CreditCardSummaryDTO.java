package sodresoftwares.barbearia.dto.billing;

public record CreditCardSummaryDTO(
        String brand,
        String lastFourDigits
) {}