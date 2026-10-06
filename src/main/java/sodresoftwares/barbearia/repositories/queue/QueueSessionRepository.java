package sodresoftwares.barbearia.repositories.queue;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import sodresoftwares.barbearia.model.queue.QueueSession;

import java.util.Optional;

@Repository
public interface QueueSessionRepository extends JpaRepository<QueueSession, String> {

    @Query("SELECT qs FROM QueueSession qs " +
            "JOIN FETCH qs.business b " +
            "WHERE b.user.id = :ownerId " +
            "AND b.isActive = true")
    Optional<QueueSession> findByOwnerUserId(@Param("ownerId") String ownerId);

    @Query("SELECT q FROM QueueSession q " +
            "JOIN FETCH q.business b " +
            "WHERE b.id = :businessId " +
            "AND b.isActive = true")
    Optional<QueueSession> findByBusinessIdWithBusiness(@Param("businessId") String businessId);

    @Query("SELECT q FROM QueueSession q " +
            "JOIN FETCH q.business b " +
            "WHERE q.ticketCode = :ticketCode " +
            "AND b.isActive = true")
    Optional<QueueSession> findByTicketCodeWithBusiness(@Param("ticketCode") String ticketCode);

    @Query("SELECT s FROM QueueSession s " +
            "JOIN FETCH s.business b " +
            "JOIN FETCH b.user " +
            "WHERE s.id = :id " +
            "AND b.isActive = true")
    Optional<QueueSession> findByIdWithBusinessAndUser(@Param("id") String id);

    boolean existsByBusinessIdAndIsActiveTrue(String businessId);
    boolean existsByTicketCode(String ticketCode);
    boolean existsByBusinessId(String businessId);
}