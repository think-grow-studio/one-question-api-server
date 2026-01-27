package site.one_question.global.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import site.one_question.global.filter.MdcLoggingFilter;
import site.one_question.global.filter.MdcMemberIdFilter;
import site.one_question.global.security.service.JwtService;
import site.one_question.global.security.filter.JwtValidationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

  private final ObjectMapper objectMapper;
  private final JwtService jwtService;
  private final AuthenticationEntryPoint authenticationEntryPoint;
  private final MdcLoggingFilter mdcLoggingFilter;
  private final MdcMemberIdFilter mdcMemberIdFilter;

  @Bean
  public SecurityFilterChain securityFilterChain(
      HttpSecurity http, AuthenticationManager authenticationManager) throws Exception {
    return http.authorizeHttpRequests(
            authorize ->
                authorize
                    .requestMatchers("/api/v1/auth/**", "/error","/api/v1/app-versions", "/health","/legal-document","/static/**","/images/**",
                        "/swagger-ui/**","swagger-ui.html","/v3/api-docs/**","swagger-resources/**")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .addFilterBefore(
            new JwtValidationFilter(jwtService, objectMapper), UsernamePasswordAuthenticationFilter.class)
        .addFilterAfter(mdcMemberIdFilter, JwtValidationFilter.class)
        .addFilterBefore(mdcLoggingFilter, JwtValidationFilter.class)
        .exceptionHandling(
            exceptions -> exceptions.authenticationEntryPoint(authenticationEntryPoint))
        .csrf(AbstractHttpConfigurer::disable)
        .httpBasic(AbstractHttpConfigurer::disable)
        .build();
  }

  @Bean
  AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
    return config.getAuthenticationManager();
  }
}


