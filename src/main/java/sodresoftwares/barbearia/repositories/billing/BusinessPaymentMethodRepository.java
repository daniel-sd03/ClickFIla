package sodresoftwares.barbearia.repositories.billing;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import sodresoftwares.barbearia.model.billing.BusinessPaymentMethod;

import java.util.Optional;

@Repository
public interface BusinessPaymentMethodRepository extends JpaRepository<BusinessPaymentMethod, String> {
    Optional<BusinessPaymentMethod> findByBusinessId(String businessId);
}