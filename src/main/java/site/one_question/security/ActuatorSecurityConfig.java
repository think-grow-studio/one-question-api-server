package site.one_question.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

// Management server (port 8081) shares the parent Security context.
// This chain permits only actuator paths on port 8081.
// Requests to port 8081 with non-actuator paths fall through to the main
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
            request.getServerPort() == 8081
            && request.getRequestURI().startsWith("/actuator"))
        .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
        .csrf(AbstractHttpConfigurer::disable)
        .build();
  }
}
