package sodresoftwares.barbearia.infra.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

@Component
public class EmailVerificationInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            try {
                DecodedJWT jwt = JWT.decode(token);
                String role = jwt.getClaim("role").asString();
                Boolean emailVerified = jwt.getClaim("email_verified").asBoolean();

                if (role == null || !role.equals("PROFESSIONAL")) {
                    return true;
                }

                if (emailVerified == null || !emailVerified) {
                    response.setStatus(HttpStatus.FORBIDDEN.value());
                    response.setContentType("application/json");
                    response.setCharacterEncoding("UTF-8");

                    response.getWriter().write("{" +
                            "\"error\": \"EMAIL_NOT_VERIFIED\", " +
                            "\"message\": \"Please verify your email to use this feature.\"}");

                    return false;
                }
            } catch (Exception e) {
                return true;
            }
        }

        return true;
    }
}