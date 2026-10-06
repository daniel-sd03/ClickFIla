package sodresoftwares.barbearia.services.auth;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import sodresoftwares.barbearia.model.user.User;
import sodresoftwares.barbearia.repositories.user.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthorizationService Tests")
class AuthorizationServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AuthorizationService authorizationService;

    @Test
    @DisplayName("Should load user by username successfully")
    void shouldLoadUserByUsernameSuccessfully() {
        // Arrange
        String login = "admin@test.com";
        User mockUser = User.builder().login(login).build();
        when(userRepository.findByLogin(login)).thenReturn(mockUser);

        // Act
        UserDetails result = authorizationService.loadUserByUsername(login);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getUsername()).isEqualTo(login);
        verify(userRepository).findByLogin(login);
    }

    @Test
    @DisplayName("Should throw UsernameNotFoundException when user does not exist")
    void shouldThrowExceptionWhenUserNotFound() {
        // Arrange
        String login = "nonexistent@test.com";
        when(userRepository.findByLogin(login)).thenReturn(null);

        // Act & Assert
        assertThatThrownBy(() -> authorizationService.loadUserByUsername(login))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessage("User not found");

        verify(userRepository).findByLogin(login);
    }
}