package sodresoftwares.barbearia.repositories.billing;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import sodresoftwares.barbearia.dto.billing.CheckoutResponseDTO;
import sodresoftwares.barbearia.model.billing.Payment;
import sodresoftwares.barbearia.model.billing.PaymentStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, String> {

    @Query("SELECT p FROM Payment p " +
            "WHERE p.subscription.id = :subscriptionId " +
            "AND p.status IN ('OVERDUE', 'PENDING') " +
            "ORDER BY p.dueDate " +
            "DESC LIMIT 1")
    Optional<Payment> findLatestPendingOrOverduePayment(String subscriptionId);

    Optional<Payment> findFirstBySubscriptionIdAndStatusOrderByIdDesc(String subscriptionId, PaymentStatus status);
    boolean existsByGatewayInvoiceId(String gatewayInvoiceId);
    Optional<Payment> findByGatewayInvoiceId(String gatewayInvoiceId);
    List<Payment> findByStatusAndDueDateBefore(PaymentStatus status, LocalDate date);
}