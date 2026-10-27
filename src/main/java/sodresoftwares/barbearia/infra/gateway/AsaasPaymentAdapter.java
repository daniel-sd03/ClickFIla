package sodresoftwares.barbearia.infra.gateway;

import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import sodresoftwares.barbearia.infra.exception.AppException;
import sodresoftwares.barbearia.model.business.Business;
import sodresoftwares.barbearia.model.billing.Plan;
import sodresoftwares.barbearia.ports.PaymentGatewayPort;
import sodresoftwares.barbearia.dto.billing.CreditCardDTO;
import sodresoftwares.barbearia.dto.billing.CreditCardHolderDTO;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

@Component
public class AsaasPaymentAdapter implements PaymentGatewayPort {

    private final RestClient restClient;

    public AsaasPaymentAdapter(
            @Value("${asaas.api.url}") String apiUrl,
            @Value("${asaas.api.key}") String apiKey) {

        this.restClient = RestClient.builder()
                .baseUrl(apiUrl)
                .defaultHeader("access_token", apiKey)
                .build();
    }

    @Override
    public String createOrUpdateCustomer(@NonNull Business business) {
        if (business.getCpfCnpj() == null || business.getCpfCnpj().trim().isEmpty()) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "MISSING_DOCUMENT",
                    "CPF or CNPJ is required to process the payment."
            );
        }

        var requestBody = Map.of(
                "name", business.getName(),
                "email", business.getUser().getLogin(),
                "cpfCnpj", business.getCpfCnpj().replaceAll("\\D", ""),
                "externalReference", business.getId()
        );

        var response = restClient.post()
                .uri("/customers")
                .body(requestBody)
                .retrieve()
                .body(Map.class);

        if (response == null || !response.containsKey("id")) {
            throw new AppException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "GATEWAY_ERROR",
                    "Failed to communicate with the payment gateway."
            );
        }

        return (String) response.get("id");
    }

    @Override
    @SuppressWarnings("unchecked")
    public GatewayCreditCardResponse processCreditCardCheckout(
            Business business,
            @NonNull Plan plan,
            String gatewayCustomerId,
            CreditCardDTO creditCard,
            CreditCardHolderDTO holderInfo) {

        String cycle = switch (plan.getBillingCycle()) {
            case MONTHLY -> "MONTHLY";
            case YEARLY -> "YEARLY";
            default -> throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "UNSUPPORTED_CYCLE",
                    "Billing cycle not supported."
            );
        };

        var creditCardPayload = Map.of(
                "holderName", creditCard.holderName(),
                "number", creditCard.number().replaceAll("\\D", ""),
                "expiryMonth", creditCard.expiryMonth(),
                "expiryYear", creditCard.expiryYear(),
                "ccv", creditCard.ccv()
        );

        var holderPayload = new HashMap<String, String>();
        holderPayload.put("name", holderInfo.name());
        holderPayload.put("email", holderInfo.email());
        holderPayload.put("cpfCnpj", holderInfo.cpfCnpj().replaceAll("\\D", ""));
        holderPayload.put("postalCode", holderInfo.postalCode().replaceAll("\\D", ""));
        holderPayload.put("addressNumber", holderInfo.addressNumber());
        holderPayload.put("phone", holderInfo.phone().replaceAll("\\D", ""));
        if (holderInfo.addressComplement() != null) holderPayload.put("addressComplement", holderInfo.addressComplement());
        if (holderInfo.mobilePhone() != null) holderPayload.put("mobilePhone", holderInfo.mobilePhone().replaceAll("\\D", ""));

        var requestBody = Map.of(
                "customer", gatewayCustomerId,
                "billingType", "CREDIT_CARD",
                "value", plan.getPrice(),
                "nextDueDate", LocalDate.now().toString(),
                "cycle", cycle,
                "description", plan.getCode(),
                "externalReference", business.getId(),
                "creditCard", creditCardPayload,
                "creditCardHolderInfo", holderPayload,
                "remoteIp", getClientIpAddress()
        );

        var response = restClient.post()
                .uri("/subscriptions")
                .body(requestBody)
                .retrieve()
                .onStatus(status -> status.is4xxClientError(), (req, res) -> {
                    throw new AppException(HttpStatus.BAD_REQUEST, "PAYMENT_REJECTED", "O cartão foi recusado ou os dados são inválidos. Verifique as informações e tente novamente.");
                })
                .body(Map.class);

        if (response == null || !response.containsKey("id")) {
            throw new AppException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "GATEWAY_ERROR",
                    "Failed to generate transparent checkout."
            );
        }

        Map<String, Object> asaasCreditCardData = (Map<String, Object>) response.get("creditCard");
        String token = asaasCreditCardData != null ? (String) asaasCreditCardData.get("creditCardToken") : "NO_TOKEN";
        String brand = asaasCreditCardData != null ? (String) asaasCreditCardData.get("creditCardBrand") : "UNKNOWN";
        String lastFour = asaasCreditCardData != null ? (String) asaasCreditCardData.get("creditCardNumber") : "0000";

        return new GatewayCreditCardResponse((String) response.get("id"), token, brand, lastFour);
    }

    @Override
    public void cancelSubscription(String gatewaySubscriptionId) {
        restClient.delete()
                .uri("/subscriptions/{id}", gatewaySubscriptionId)
                .retrieve()
                .toBodilessEntity();
    }

    // ======================================================================================
    // Auxiliary Methods
    // ======================================================================================

    private String getClientIpAddress() {
        var requestAttributes = RequestContextHolder.getRequestAttributes();
        if (requestAttributes instanceof ServletRequestAttributes servletRequestAttributes) {
            String xForwardedFor = servletRequestAttributes.getRequest().getHeader("X-Forwarded-For");
            if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
                return xForwardedFor.split(",")[0]; // Pega o IP original atrás do Cloudflare/Load Balancer
            }
            return servletRequestAttributes.getRequest().getRemoteAddr();
        }
        return "127.0.0.1";
    }
}