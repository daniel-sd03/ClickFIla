package sodresoftwares.barbearia.repositories.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import sodresoftwares.barbearia.model.auth.RefreshToken;
import sodresoftwares.barbearia.model.user.User;

import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, String> {

    Optional<RefreshToken> findByToken(String token);

    Optional<RefreshToken> findByUserId(String userId);

    boolean existsByToken(String token);

    int deleteByUser(User user);
}