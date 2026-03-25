package site.one_question.admin.infrastructure.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import site.one_question.admin.infrastructure.filter.AdminJwtCookieFilter;
import site.one_question.global.security.service.JwtService;

@Configuration
public class AdminSecurityConfig {

    private final JwtService jwtService;
    private final String adminEmail;

    public AdminSecurityConfig(JwtService jwtService, @Value("${admin.email}") String adminEmail) {
        this.jwtService = jwtService;
        this.adminEmail = adminEmail;
    }

    @Bean
    @Order(1)
    public SecurityFilterChain adminSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                .securityMatcher("/admin/**")
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/admin/login").permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(
                        new AdminJwtCookieFilter(jwtService, adminEmail),
                        UsernamePasswordAuthenticationFilter.class
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) ->
                                response.sendRedirect("/admin/login"))
                )
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .build();
    }
}
