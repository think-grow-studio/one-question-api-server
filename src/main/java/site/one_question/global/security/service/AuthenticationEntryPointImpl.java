package site.one_question.global.security.service;

import static site.one_question.auth.domain.exception.AuthExceptionSpec.AUTHENTICATION_FAIL;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import site.one_question.global.common.MdcKey;
import site.one_question.global.exception.ExceptionResponse;

@Component
@RequiredArgsConstructor
public class AuthenticationEntryPointImpl implements AuthenticationEntryPoint {
  private final ObjectMapper objectMapper;

  @Override
  public void commence(
      HttpServletRequest request,
      HttpServletResponse response,
      AuthenticationException authException)
      throws IOException {
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding("UTF-8");
    response.setStatus(HttpStatus.UNAUTHORIZED.value());

    ExceptionResponse errorResponse = ExceptionResponse.of(
        MDC.get(MdcKey.REQUEST_ID),
        AUTHENTICATION_FAIL.getStatus().value(),
        AUTHENTICATION_FAIL.getCode(),
        "로그인이 필요합니다.");
    response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
  }
}
