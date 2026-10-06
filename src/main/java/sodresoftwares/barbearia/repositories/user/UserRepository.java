package sodresoftwares.barbearia.repositories.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.security.core.userdetails.UserDetails;
import sodresoftwares.barbearia.model.user.User;

public interface UserRepository extends JpaRepository<User, String> {
    UserDetails findByLogin(String login);
    boolean existsByLogin(String login);
    User findByGoogleId(String googleId);
}