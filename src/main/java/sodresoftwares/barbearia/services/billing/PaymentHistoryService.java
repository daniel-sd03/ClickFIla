package sodresoftwares.barbearia.services.billing;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import sodresoftwares.barbearia.model.billing.Payment;
import sodresoftwares.barbearia.model.billing.PaymentHistory;
import sodresoftwares.barbearia.model.billing.PaymentStatus;
import sodresoftwares.barbearia.repositories.billing.PaymentHistoryRepository;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentHistoryService {

    private final PaymentHistoryRepository historyRepository;

    @Transactional(propagation = Propagation.MANDATORY)
    public void logStatusChange(Payment payment, PaymentStatus newStatus, String gatewayEvent) {
        PaymentHistory history = PaymentHistory.builder()
                .payment(payment)
                .previousStatus(payment.getStatus())
                .newStatus(newStatus)
                .gatewayEvent(gatewayEvent)
                .build();

        historyRepository.save(history);
    }
}