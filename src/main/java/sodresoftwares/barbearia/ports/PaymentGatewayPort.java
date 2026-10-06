package sodresoftwares.barbearia.ports;

import sodresoftwares.barbearia.model.business.Business;
import sodresoftwares.barbearia.model.billing.Plan;

public interface PaymentGatewayPort {

    /**
     * Cria (ou recupera) o cliente no Gateway de Pagamento.
     * Retorna o ID do cliente gerado pela API externa (ex: cus_000005030805).
     */
    String createOrUpdateCustomer(Business business);

    /**
     * Gera o Checkout da Assinatura no Gateway.
     * Deve retornar a URL de pagamento para o barbeiro e o ID da assinatura gerada lá.
     */
    GatewayCheckoutResponse createSubscriptionCheckout(Business business, Plan plan, String gatewayCustomerId);

    /**
     * Cancela a assinatura ativa no Gateway (para parar de cobrar o barbeiro).
     */
    void cancelSubscription(String gatewaySubscriptionId);

    // DTO interno (Record) para retornar os dois dados que precisamos quando geramos o checkout
    record GatewayCheckoutResponse(String gatewaySubscriptionId, String checkoutUrl) {}
}