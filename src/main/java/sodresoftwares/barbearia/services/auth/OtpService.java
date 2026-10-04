package sodresoftwares.barbearia.services.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;

@Service
@RequiredArgsConstructor
public class OtpService {

    private final StringRedisTemplate redisTemplate;
    private final SecureRandom secureRandom = new SecureRandom();

    private static final int OTP_LENGTH = 6;
    private static final Duration OTP_TTL = Duration.ofMinutes(15);
    private static final String OTP_PREFIX = "otp:verification:";

    public String generateAndSaveOtp(String userId) {
        String otp = String.format("%0" + OTP_LENGTH + "d", secureRandom.nextInt(1000000));

        redisTemplate.opsForValue().set(OTP_PREFIX + userId, otp, OTP_TTL);

        return otp;
    }

    public boolean isValidOtp(String userId, String inputOtp) {
        String key = OTP_PREFIX + userId;
        String storedOtp = redisTemplate.opsForValue().get(key);

        if (storedOtp != null && storedOtp.equals(inputOtp)) {
            redisTemplate.delete(key);
            return true;
        }

        return false;
    }
}