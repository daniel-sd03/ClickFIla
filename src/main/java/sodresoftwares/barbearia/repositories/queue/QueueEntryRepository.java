package sodresoftwares.barbearia.repositories.queue;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import sodresoftwares.barbearia.model.queue.QueueEntry;
import sodresoftwares.barbearia.model.queue.QueueEntryStatus;

import java.util.List;
import java.util.Optional;

@Repository
public interface QueueEntryRepository extends JpaRepository<QueueEntry, String> {

    @Query("""
        SELECT q FROM QueueEntry q
        JOIN FETCH q.user
        JOIN FETCH q.queueSession
        LEFT JOIN FETCH q.servedByMember sbm
        LEFT JOIN FETCH sbm.user
        WHERE q.queueSession.id = :sessionId
        AND q.status IN ('WAITING', 'CALLED', 'IN_SERVICE')
        ORDER BY q.joinedAt ASC
    """)
    List<QueueEntry> findActiveEntriesBySessionId(@Param("sessionId") String sessionId);

    @Query("""
        SELECT q FROM QueueEntry q
        JOIN FETCH q.user
        WHERE q.id = :id
    """)
    Optional<QueueEntry> findByIdWithUser(@Param("id") String id);

    @Query("SELECT e FROM QueueEntry e " +
            "JOIN FETCH e.user u " +
            "JOIN FETCH e.queueSession s " +
            "JOIN FETCH s.business b " +
            "JOIN FETCH b.user " +
            "LEFT JOIN FETCH e.servedByMember sbm " +
            "LEFT JOIN FETCH sbm.user " +
            "WHERE e.id = :id")
    Optional<QueueEntry> findByIdWithFullGraph(@Param("id") String id);

    @Query("SELECT COUNT(qe) > 0 FROM QueueEntry qe " +
            "WHERE qe.servedByMember.id = :memberId " +
            "AND qe.status IN ('CALLED', 'IN_SERVICE')")
    boolean hasActiveServiceByMemberId(@Param("memberId") String memberId);

    @Query("SELECT COUNT(qe) > 0 FROM QueueEntry qe " +
            "WHERE qe.queueSession.business.id = :businessId " +
            "AND qe.status IN ('WAITING', 'CALLED', 'IN_SERVICE')")
    boolean hasActiveEntriesByBusinessId(@Param("businessId") String businessId);

    boolean existsByUserIdAndStatusIn(String userId, List<QueueEntryStatus> statuses);
    Optional<QueueEntry> findByUserIdAndStatusIn(String userId, List<QueueEntryStatus> statuses);
    Optional<QueueEntry> findFirstByUserIdOrderByJoinedAtDesc(String userId);
    boolean existsByServedByMemberIdAndStatusIn(String memberId, List<QueueEntryStatus> statuses);
}