package sodresoftwares.barbearia.repositories.billing;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import sodresoftwares.barbearia.model.billing.Subscription;
import sodresoftwares.barbearia.model.billing.SubscriptionStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface SubscriptionRepository extends JpaRepository<Subscription, String> {

    @Query("SELECT s FROM Subscription s " +
            "JOIN FETCH s.business " +
            "WHERE s.business.id = :businessId")
    Optional<Subscription> findByBusinessIdWithBusiness(@Param("businessId") String businessId);

    @Query("""
        SELECT s FROM Subscription s 
        JOIN FETCH s.business b 
        LEFT JOIN FETCH s.plan p 
        JOIN TeamMember tm ON tm.business.id = b.id 
        WHERE tm.user.id = :userId AND tm.isActive = true
    """)
    Optional<Subscription> findByUserIdWithBusinessAndPlan(@Param("userId") String userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Subscription s " +
            "WHERE s.id = :id")
    Optional<Subscription> findByIdWithLock(String id);

    @Query("""
        SELECT s.status 
        FROM Subscription s 
        JOIN s.business b 
        JOIN TeamMember tm ON tm.business.id = b.id 
        WHERE tm.user.id = :userId AND tm.isActive = true
    """)
    Optional<SubscriptionStatus> findSubscriptionStatusByUserId(@Param("userId") String userId);

    List<Subscription> findByStatusAndCurrentPeriodStartBefore(SubscriptionStatus status, LocalDate date);
    boolean existsByBusinessId(String businessId);
    Optional<Subscription> findByBusinessId(String businessId);
    List<Subscription> findByStatusAndCurrentPeriodEndBefore(SubscriptionStatus status, LocalDate date);
    List<Subscription> findByCancelAtPeriodEndTrueAndCurrentPeriodEndBefore(LocalDate date);
}