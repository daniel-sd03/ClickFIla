package sodresoftwares.barbearia.services.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OtpService Tests")
class OtpServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private OtpService otpService;

    private final String USER_ID = "user-123";
    private final String REDIS_PREFIX = "otp:verification:";

    @Test
    @DisplayName("Should generate OTP and save it in Redis")
    void shouldGenerateAndSaveOtp() {
        // Arrange
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        // Act
        String otp = otpService.generateAndSaveOtp(USER_ID);

        // Assert
        assertThat(otp).isNotNull();
        assertThat(otp.length()).isEqualTo(6);
        verify(valueOperations).set(eq(REDIS_PREFIX + USER_ID), eq(otp), any(Duration.class));
    }

    @Test
    @DisplayName("Should return TRUE and delete key when OTP is correct")
    void shouldReturnTrueWhenOtpIsValid() {
        // Arrange
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(REDIS_PREFIX + USER_ID)).thenReturn("123456");

        // Act
        boolean isValid = otpService.isValidOtp(USER_ID, "123456");

        // Assert
        assertThat(isValid).isTrue();
        verify(redisTemplate).delete(REDIS_PREFIX + USER_ID);
    }

    @Test
    @DisplayName("Should return FALSE when OTP does not match")
    void shouldReturnFalseWhenOtpIsIncorrect() {
        // Arrange
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(REDIS_PREFIX + USER_ID)).thenReturn("123456");

        // Act
        boolean isValid = otpService.isValidOtp(USER_ID, "000000");

        // Assert
        assertThat(isValid).isFalse();
        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    @DisplayName("Should return FALSE when OTP is expired (not in Redis)")
    void shouldReturnFalseWhenOtpIsExpired() {
        // Arrange
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(REDIS_PREFIX + USER_ID)).thenReturn(null);

        // Act
        boolean isValid = otpService.isValidOtp(USER_ID, "123456");

        // Assert
        assertThat(isValid).isFalse();
        verify(redisTemplate, never()).delete(anyString());
    }
}