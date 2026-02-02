package site.one_question.global.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import site.one_question.auth.domain.OneQuestionPrincipal;

/**
 * 인증 후에 실행되는 MDC 필터.
 * SecurityContext에서 memberId를 추출하여 MDC에 설정합니다.
 */
@Component
public class MdcMemberIdFilter extends OncePerRequestFilter {

  private static final String MEMBER_ID_KEY = "memberId";

  @Override
  protected void doFilterInternal(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull FilterChain filterChain)
      throws ServletException, IOException {

    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null
        && authentication.isAuthenticated()
        && authentication.getPrincipal() instanceof OneQuestionPrincipal principal) {
      MDC.put(MEMBER_ID_KEY, String.valueOf(principal.getId()));
    }

    try {
      filterChain.doFilter(request, response);
    } finally {
      MDC.remove(MEMBER_ID_KEY);
    }
  }
}
