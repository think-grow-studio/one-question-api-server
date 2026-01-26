package site.one_question.global.security.filter;

import static site.one_question.auth.domain.exception.AuthExceptionSpec.ACCESS_TOKEN_EXCEPTION;
import static site.one_question.auth.domain.exception.AuthExceptionSpec.ACCESS_TOKEN_EXPIRED;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import site.one_question.auth.domain.OneQuestionPrincipal;
import site.one_question.auth.domain.exception.AuthExceptionSpec;
import site.one_question.global.common.MdcKey;
import site.one_question.global.exception.ExceptionResponse;
import site.one_question.global.security.service.JwtService;

@RequiredArgsConstructor
public class JwtValidationFilter extends OncePerRequestFilter {
  private final Logger logger = LoggerFactory.getLogger(this.getClass());
  private final JwtService jwtService;
  private final ObjectMapper objectMapper;

  private static final String HTTP_HEADER_AUTHORIZATION = "Authorization";
  private static final String HTTP_HEADER_AUTH_BEARER = "Bearer ";

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    String token = extractTokenFrom(request);

    if (token == null) {
      filterChain.doFilter(request, response);
      return;
    }
    try {
      jwtService.isValid(token);
      setAuthentication(token);
      filterChain.doFilter(request, response);
    } catch (ExpiredJwtException e) {
      logger.error("JWT 만료 예외 발생 : {}", e.getMessage());
      writeErrorResponse(response, ACCESS_TOKEN_EXPIRED, "토큰이 만료되었습니다.");
    } catch (JwtException e) {
      logger.error("JWT 예외 발생 : {}", e.getMessage());
      writeErrorResponse(response, ACCESS_TOKEN_EXCEPTION, "유효하지 않은 토큰입니다.");
    }
  }

  private String extractTokenFrom(HttpServletRequest request) {
    String value = request.getHeader(HTTP_HEADER_AUTHORIZATION);
    if (value == null || !value.contains(HTTP_HEADER_AUTH_BEARER)) {
      return null;
    }
    return value.substring(HTTP_HEADER_AUTH_BEARER.length());
  }

  private void setAuthentication(String token) {
    Authentication authentication = getAuthentication(token);
    SecurityContextHolder.getContext().setAuthentication(authentication);
    logger.info("인증 정보 구성 완료 : {}", authentication.getPrincipal());
  }

  private Authentication getAuthentication(String token) {
    OneQuestionPrincipal principal = jwtService.extractPrincipal(token);
    return new UsernamePasswordAuthenticationToken(principal, "", principal.getAuthorities());
  }

  private void writeErrorResponse(HttpServletResponse response, AuthExceptionSpec spec, String message)
      throws IOException {
    response.setStatus(spec.getStatus().value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding("UTF-8");
    ExceptionResponse errorResponse = ExceptionResponse.of(
        MDC.get(MdcKey.REQUEST_ID), spec.getStatus().value(), spec.getCode(), message);
    response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
  }
}
