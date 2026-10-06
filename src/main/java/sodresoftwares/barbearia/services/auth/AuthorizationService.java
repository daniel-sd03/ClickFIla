package sodresoftwares.barbearia.services.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sodresoftwares.barbearia.repositories.user.UserRepository;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthorizationService implements UserDetailsService{

	private final UserRepository userRepository;

	@Override
	public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
		 UserDetails user = userRepository.findByLogin(username);
	        if (user == null) {
	            throw new UsernameNotFoundException("User not found");
	        }
	        return user;
	}
}
