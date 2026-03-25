package site.one_question.web.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import site.one_question.api.auth.domain.OneQuestionPrincipal;
import site.one_question.global.security.service.JwtService;
import site.one_question.api.member.domain.MemberPermission;

@RequiredArgsConstructor
public class AdminJwtCookieFilter extends OncePerRequestFilter {

    private static final String ADMIN_COOKIE_NAME = "ADMIN_TOKEN";

    private final JwtService jwtService;
    private final String adminEmail;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String token = extractTokenFromCookie(request);

        if (token != null && jwtService.isValid(token)) {
            String email = jwtService.extractEmail(token);
            if (adminEmail.equals(email)) {
                OneQuestionPrincipal principal = new OneQuestionPrincipal(0L, email, MemberPermission.FREE);
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(principal, "", principal.getAuthorities());
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }

        filterChain.doFilter(request, response);
    }

    private String extractTokenFromCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (ADMIN_COOKIE_NAME.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
