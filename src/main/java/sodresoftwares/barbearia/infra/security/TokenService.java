package sodresoftwares.barbearia.infra.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTCreationException;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;
import sodresoftwares.barbearia.infra.exception.AppException;
import sodresoftwares.barbearia.model.user.User;
import sodresoftwares.barbearia.model.user.UserRole;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
@Slf4j
public class TokenService {
    @Value("${api.security.token.secret}")
    private String secret;

    public String generateToken(User user, String lgpdVersion){
        try{
            Algorithm algorithm = Algorithm.HMAC256(secret);
            return JWT.create()
                    .withIssuer("auth-api")
                    .withSubject(user.getLogin())
                    .withClaim("user_id", user.getId())
                    .withClaim("role", user.getRole().name())
                    .withClaim("lgpd_version", lgpdVersion)
                    .withClaim("email_verified", user.isEmailVerified())
                    .withExpiresAt(genExpirationDate())
                    .sign(algorithm);
        } catch (JWTCreationException ex) {
            log.error("Failed to generate JWT", ex);
            throw new AppException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "TOKEN_GENERATION_ERROR",
                    "Error while generating authentication token."
            );
        }
    }

    public DecodedJWT validateAndDecodeToken(String token){
        try {
            Algorithm algorithm = Algorithm.HMAC256(secret);
            return JWT.require(algorithm)
                    .withIssuer("auth-api")
                    .build()
                    .verify(token);
        } catch (JWTVerificationException exception){
            return null;
        }
    }

    public UsernamePasswordAuthenticationToken getAuthentication(DecodedJWT decodedJWT) {
        String login = decodedJWT.getSubject();
        String userId = decodedJWT.getClaim("user_id").asString();
        String roleString = decodedJWT.getClaim("role").asString();

        if (userId != null && roleString != null) {
            User authenticatedUser = User.builder()
                    .id(userId)
                    .login(login)
                    .role(UserRole.valueOf(roleString))
                    .build();

            return new UsernamePasswordAuthenticationToken(
                    authenticatedUser,
                    null,
                    authenticatedUser.getAuthorities()
            );
        }
        return null;
    }

    private Instant genExpirationDate() {
        return Instant.now().plus(45, ChronoUnit.MINUTES);
    }
}