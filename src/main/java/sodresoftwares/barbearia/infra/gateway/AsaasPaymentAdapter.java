package sodresoftwares.barbearia.infra.gateway;

import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import sodresoftwares.barbearia.infra.exception.AppException;
import sodresoftwares.barbearia.model.business.Business;
import sodresoftwares.barbearia.model.billing.Plan;
import sodresoftwares.barbearia.ports.PaymentGatewayPort;

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
        var requestBody = Map.of(
                "name", business.getName(),
                "email", business.getUser().getLogin(),
                "cpfCnpj", business.getCpfCnpj() != null ? business.getCpfCnpj() : "",
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
    public GatewayCheckoutResponse createSubscriptionCheckout(Business business, @NonNull Plan plan, String gatewayCustomerId) {
        if (plan.getBillingCycle() == null) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_PLAN",
                    "The selected plan has an invalid billing cycle."
            );
        }

        return switch (plan.getBillingCycle()) {
            case MONTHLY -> createMonthlyCheckout(business, plan, gatewayCustomerId);
            case YEARLY -> createYearlyCheckout(business, plan, gatewayCustomerId);
            default -> throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "UNSUPPORTED_CYCLE",
                    "Billing cycle not supported by the gateway."
            );
        };
    }

    @Override
    public void cancelSubscription(String gatewaySubscriptionId) {
        restClient.delete()
                .uri("/paymentLinks/{id}", gatewaySubscriptionId)
                .retrieve()
                .toBodilessEntity();
    }

    // ======================================================================================
    // Auxiliary Methods
    // ======================================================================================

    private GatewayCheckoutResponse createMonthlyCheckout(Business business, Plan plan, String customerId) {
        var requestBody = Map.of(
                "billingType", "UNDEFINED",
                "chargeType", "RECURRENT",
                "name", "Assinatura " + plan.getName() + " - " + business.getName(),
                "description", "Acesso completo ao sistema de gestão de filas.",
                "value", plan.getPrice(),
                "customer", customerId
        );

        var response = restClient.post()
                .uri("/paymentLinks")
                .body(requestBody)
                .retrieve()
                .body(Map.class);

        if (response == null || !response.containsKey("id") || !response.containsKey("url")) {
            throw new AppException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "GATEWAY_ERROR",
                    "Failed to generate monthly checkout link."
            );
        }

        return new GatewayCheckoutResponse((String) response.get("id"), (String) response.get("url"));
    }

    private GatewayCheckoutResponse createYearlyCheckout(Business business, Plan plan, String customerId) {
        var requestBody = Map.of(
                "billingType", "UNDEFINED",
                "chargeType", "INSTALLMENT",
                "name", "Assinatura " + plan.getName() + " (Anual) - " + business.getName(),
                "description", "Plano anual parcelado em até 12x no cartão.",
                "value", plan.getPrice(),
                "maxInstallmentCount", 12,
                "dueDateLimitDays", 5,
                "customer", customerId
        );

        var response = restClient.post()
                .uri("/paymentLinks")
                .body(requestBody)
                .retrieve()
                .body(Map.class);

        if (response == null || !response.containsKey("id") || !response.containsKey("url")) {
            throw new AppException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "GATEWAY_ERROR",
                    "Failed to generate yearly checkout link."
            );
        }

        return new GatewayCheckoutResponse((String) response.get("id"), (String) response.get("url"));
    }
}