package sodresoftwares.barbearia.repositories.billing;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import sodresoftwares.barbearia.model.billing.PaymentHistory;

import java.util.List;

@Repository
public interface PaymentHistoryRepository extends JpaRepository<PaymentHistory, String> {

    List<PaymentHistory> findByPaymentIdOrderByCreatedAtDesc(String paymentId);
}