package sodresoftwares.barbearia.repositories.business;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import sodresoftwares.barbearia.model.business.Business;

import java.util.Optional;

public interface BusinessRepository extends JpaRepository<Business, String> {
    @Query("SELECT b FROM Business b " +
            "JOIN FETCH b.user " +
            "WHERE b.user.id = :userId")
    Optional<Business> findByUserIdWithUser(@Param("userId") String userId);

    @Query("SELECT b FROM Business b " +
            "JOIN FETCH b.user " +
            "WHERE b.user.id = :userId " +
            "AND b.isActive = true")
    Optional<Business> findActiveByUserIdWithUser(@Param("userId") String userId);

    Optional<Business> findByUserId(String userId);

    boolean existsByUserId(String userId);
}