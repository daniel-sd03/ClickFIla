package sodresoftwares.barbearia.infra.gateway;

import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import sodresoftwares.barbearia.dto.billing.CheckoutResponseDTO;
import sodresoftwares.barbearia.infra.exception.AppException;
import sodresoftwares.barbearia.model.business.Business;
import sodresoftwares.barbearia.model.billing.Plan;
import sodresoftwares.barbearia.ports.PaymentGatewayPort;
import sodresoftwares.barbearia.dto.billing.CreditCardDTO;
import sodresoftwares.barbearia.dto.billing.CreditCardHolderDTO;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
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
            throw new AppException(HttpStatus.BAD_REQUEST, "MISSING_DOCUMENT", "CPF or CNPJ is required to process the payment.");
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
            throw new AppException(HttpStatus.INTERNAL_SERVER_ERROR, "GATEWAY_ERROR", "Failed to communicate with the payment gateway.");
        }

        return (String) response.get("id");
    }

    @Override
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

        var requestBody = Map.of(
                "customer", gatewayCustomerId,
                "billingType", "CREDIT_CARD",
                "value", plan.getPrice(),
                "nextDueDate", LocalDate.now().toString(),
                "cycle", cycle,
                "description", plan.getCode(),
                "externalReference", business.getId(),
                "creditCard", buildCreditCardPayload(creditCard),
                "creditCardHolderInfo", buildHolderPayload(holderInfo),
                "remoteIp", getClientIpAddress()
        );

        return sendSubscriptionRequest(
                HttpMethod.POST,
                "/subscriptions",
                null, requestBody,
                "The card was declined or the provided data is invalid. Please check the information and try again."
        );
    }

    @Override
    public GatewayCreditCardResponse updateSubscriptionCreditCard(
            String gatewaySubscriptionId,
            CreditCardDTO creditCard,
            CreditCardHolderDTO holderInfo) {

        var requestBody = Map.of(
                "billingType", "CREDIT_CARD",
                "creditCard", buildCreditCardPayload(creditCard),
                "creditCardHolderInfo", buildHolderPayload(holderInfo),
                "remoteIp", getClientIpAddress()
        );

        return sendSubscriptionRequest(
                HttpMethod.PUT,
                "/subscriptions/{id}",
                gatewaySubscriptionId,
                requestBody,
                "The new card was declined or the provided data is invalid."
        );
    }

    @Override
    public GatewayCreditCardResponse createScheduledSubscription(
            Business business,
            Plan plan,
            String gatewayCustomerId,
            String creditCardToken,
            CreditCardDTO creditCard,
            CreditCardHolderDTO holderInfo,
            LocalDate nextDueDate) {

        String cycle = plan.getBillingCycle().name();

        Map<String, Object> requestBody = new java.util.HashMap<>(Map.of(
                "customer", gatewayCustomerId,
                "billingType", "CREDIT_CARD",
                "value", plan.getPrice(),
                "nextDueDate", nextDueDate.toString(),
                "cycle", cycle,
                "description", plan.getCode(),
                "externalReference", business.getId(),
                "remoteIp", getClientIpAddress()
        ));

        if (creditCard != null && holderInfo != null) {
            requestBody.put("creditCard", buildCreditCardPayload(creditCard));
            requestBody.put("creditCardHolderInfo", buildHolderPayload(holderInfo));
        } else {
            requestBody.put("creditCardToken", creditCardToken);
        }

        return sendSubscriptionRequest(
                HttpMethod.POST,
                "/subscriptions",
                null,
                requestBody,
                "Failed to schedule the new subscription. Check the payment method."
        );
    }

    @Override
    @SuppressWarnings("unchecked")
    public CheckoutResponseDTO processPixCheckout(Business business, Plan plan, String gatewayCustomerId) {
        String cycle = plan.getBillingCycle().name();

        var requestBody = Map.of(
                "customer", gatewayCustomerId,
                "billingType", "PIX",
                "value", plan.getPrice(),
                "nextDueDate", LocalDate.now().toString(),
                "cycle", cycle,
                "description", plan.getCode(),
                "externalReference", business.getId()
        );

        var subscriptionResponse = restClient.post()
                .uri("/subscriptions")
                .body(requestBody)
                .retrieve()
                .body(Map.class);

        if (subscriptionResponse == null || !subscriptionResponse.containsKey("id")) {
            throw new AppException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "GATEWAY_ERROR",
                    "Failed to create PIX subscription."
            );
        }

        String subscriptionId = (String) subscriptionResponse.get("id");

        var paymentsResponse = restClient.get()
                .uri("/payments?subscription=" + subscriptionId)
                .retrieve()
                .body(Map.class);

        List<Map<String, Object>> payments = (List<Map<String, Object>>) paymentsResponse.get("data");
        if (payments == null || payments.isEmpty()) {
            throw new AppException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "GATEWAY_ERROR",
                    "No invoice generated for this PIX subscription."
            );
        }

        String firstPaymentId = (String) payments.get(0).get("id");

        var pixResponse = restClient.get()
                .uri("/payments/{id}/pixQrCode", firstPaymentId)
                .retrieve()
                .body(Map.class);

        if (pixResponse == null || !pixResponse.containsKey("payload")) {
            throw new AppException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "GATEWAY_ERROR",
                    "Failed to retrieve PIX QR Code."
            );
        }

        return new CheckoutResponseDTO(
                subscriptionId,
                (String) pixResponse.get("encodedImage"),
                (String) pixResponse.get("payload")
        );
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

    /**
     * Reusable method to send the request (POST/PUT) and parse the GatewayCreditCardResponse.
     */
    @SuppressWarnings("unchecked")
    private GatewayCreditCardResponse sendSubscriptionRequest(
            HttpMethod method,
            String uri,
            String uriVariable,
            Map<String, Object> requestBody,
            String errorMessage) {

        var requestBuilder = method == HttpMethod.POST ? restClient.post() : restClient.put();

        var uriBuilder = uriVariable != null ? requestBuilder.uri(uri, uriVariable) : requestBuilder.uri(uri);

        var response = uriBuilder
                .body(requestBody)
                .retrieve()
                .onStatus(status -> status.is4xxClientError(), (req, res) -> {
                    throw new AppException(
                            HttpStatus.BAD_REQUEST,
                            "PAYMENT_REJECTED",
                            errorMessage);
                })
                .body(Map.class);

        if (response == null || !response.containsKey("id")) {
            throw new AppException(HttpStatus.INTERNAL_SERVER_ERROR, "GATEWAY_ERROR", "Failed to process transparent checkout with the payment gateway.");
        }

        Map<String, Object> asaasCreditCardData = (Map<String, Object>) response.get("creditCard");
        String token = asaasCreditCardData != null ? (String) asaasCreditCardData.get("creditCardToken") : "NO_TOKEN";
        String brand = asaasCreditCardData != null ? (String) asaasCreditCardData.get("creditCardBrand") : "UNKNOWN";
        String lastFour = asaasCreditCardData != null ? (String) asaasCreditCardData.get("creditCardNumber") : "0000";

        return new GatewayCreditCardResponse((String) response.get("id"), token, brand, lastFour);
    }

    private Map<String, String> buildCreditCardPayload(CreditCardDTO creditCard) {
        return Map.of(
                "holderName", creditCard.holderName(),
                "number", creditCard.number().replaceAll("\\D", ""),
                "expiryMonth", creditCard.expiryMonth(),
                "expiryYear", creditCard.expiryYear(),
                "ccv", creditCard.ccv()
        );
    }

    private Map<String, String> buildHolderPayload(CreditCardHolderDTO holderInfo) {
        var holderPayload = new HashMap<String, String>();
        holderPayload.put("name", holderInfo.name());
        holderPayload.put("email", holderInfo.email());
        holderPayload.put("cpfCnpj", holderInfo.cpfCnpj().replaceAll("\\D", ""));
        holderPayload.put("postalCode", holderInfo.postalCode().replaceAll("\\D", ""));
        holderPayload.put("addressNumber", holderInfo.addressNumber());
        holderPayload.put("phone", holderInfo.phone().replaceAll("\\D", ""));
        if (holderInfo.addressComplement() != null) holderPayload.put("addressComplement", holderInfo.addressComplement());
        if (holderInfo.mobilePhone() != null) holderPayload.put("mobilePhone", holderInfo.mobilePhone().replaceAll("\\D", ""));
        return holderPayload;
    }

    private String getClientIpAddress() {
        var requestAttributes = RequestContextHolder.getRequestAttributes();
        if (requestAttributes instanceof ServletRequestAttributes servletRequestAttributes) {
            String xForwardedFor = servletRequestAttributes.getRequest().getHeader("X-Forwarded-For");
            if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
                return xForwardedFor.split(",")[0];
            }
            return servletRequestAttributes.getRequest().getRemoteAddr();
        }
        return "127.0.0.1";
    }
}