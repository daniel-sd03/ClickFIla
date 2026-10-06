package sodresoftwares.barbearia.services.notification;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import sodresoftwares.barbearia.dto.notification.PushSubscriptionDTO;
import sodresoftwares.barbearia.model.notification.UserPushSubscription;
import sodresoftwares.barbearia.model.user.User;
import sodresoftwares.barbearia.repositories.notification.UserPushSubscriptionRepository;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PushSubscriptionService Tests")
class PushSubscriptionServiceTest {

    @Mock
    private UserPushSubscriptionRepository repository;

    @InjectMocks
    private PushSubscriptionService pushSubscriptionService;

    private User testUser;
    private PushSubscriptionDTO testDto;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id("user-123")
                .name("Cliente Teste")
                .build();

        PushSubscriptionDTO.Keys keys = new PushSubscriptionDTO.Keys("p256dh-key", "auth-key");
        testDto = new PushSubscriptionDTO("https://fcm.googleapis.com/test-endpoint", keys);
    }

    @Test
    @DisplayName("Should save new subscription when endpoint does not exist")
    void testSubscribe_NewDevice() {
        // Arrange
        when(repository.existsByEndpoint(testDto.endpoint())).thenReturn(false);

        // Act
        pushSubscriptionService.subscribe(testUser, testDto);

        // Assert
        verify(repository).existsByEndpoint(testDto.endpoint());
        verify(repository).save(argThat(sub ->
                sub.getUser().getId().equals("user-123") &&
                        sub.getEndpoint().equals("https://fcm.googleapis.com/test-endpoint") &&
                        sub.getP256dh().equals("p256dh-key") &&
                        sub.getAuth().equals("auth-key")
        ));
    }

    @Test
    @DisplayName("Should ignore and not save when endpoint already exists")
    void testSubscribe_DeviceAlreadyRegistered() {
        // Arrange
        when(repository.existsByEndpoint(testDto.endpoint())).thenReturn(true);

        // Act
        pushSubscriptionService.subscribe(testUser, testDto);

        // Assert
        verify(repository).existsByEndpoint(testDto.endpoint());
        verify(repository, never()).save(any(UserPushSubscription.class));
    }
}