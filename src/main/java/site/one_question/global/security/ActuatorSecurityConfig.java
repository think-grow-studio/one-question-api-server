package site.one_question.global.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

// Management server (port 8080) shares the parent Security context.
// This chain permits only actuator paths on port 8080.
// Requests to port 8080 with non-actuator paths fall through to the main
// security chain (JWT required), and return 404 since those handlers are
// not registered in the management DispatcherServlet.
// Network-level access restriction is enforced by OCI Security Group.
@Configuration
public class ActuatorSecurityConfig {

  @Bean
  @Order(0)
  public SecurityFilterChain managementPortSecurityFilterChain(HttpSecurity http) throws Exception {
    return http
        .securityMatcher(request ->
            request.getServerPort() == 8080
            && request.getRequestURI().startsWith("/actuator"))
        .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
        .csrf(AbstractHttpConfigurer::disable)
        .build();
  }
}
