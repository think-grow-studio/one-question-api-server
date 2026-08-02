package site.one_question.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.Cookie;
import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import site.one_question.api.member.domain.MemberPermission;
import site.one_question.security.filter.JwtValidationFilter;
import site.one_question.security.service.JwtService;
import site.one_question.web.security.AdminJwtCookieFilter;

@DisplayName("JWT 토큰 타입 필터 검증")
class JwtTokenTypeFilterTest {

    private static final String SECRET =
            "dGVzdC1zZWNyZXQta2V5LWZvci1kZXZlbG9wbWVudC1vbmx5LTMyYnl0ZXM=";
    private static final String ADMIN_EMAIL = "admin@one-question.site";
    private static final long ACCESS_EXPIRE_TIME = 3_600_000L;
    private static final long REFRESH_EXPIRE_TIME = 604_800_000L;

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        jwtService = new JwtService(SECRET, ACCESS_EXPIRE_TIME, REFRESH_EXPIRE_TIME);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("일반 API 필터는 리프레시 토큰을 거부한다")
    void api_filter_rejects_refresh_token() throws Exception {
        String token = jwtService.issueRefreshToken(1L, "member@example.com", MemberPermission.FREE);

        MockHttpServletResponse response = executeApiFilter(token);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("\"code\":\"AUTH-009\"");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("일반 API 필터는 type 클레임이 없는 토큰을 거부한다")
    void api_filter_rejects_token_without_type() throws Exception {
        MockHttpServletResponse response = executeApiFilter(createTokenWithoutType("member@example.com"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("\"code\":\"AUTH-009\"");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("일반 API 필터는 문자열이 아닌 type 클레임을 거부한다")
    void api_filter_rejects_token_with_non_string_type() throws Exception {
        MockHttpServletResponse response = executeApiFilter(createToken(42, "member@example.com"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("\"code\":\"AUTH-009\"");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("관리자 쿠키 필터는 리프레시 토큰을 인증하지 않는다")
    void admin_filter_rejects_refresh_token() throws Exception {
        String token = jwtService.issueRefreshToken(0L, ADMIN_EMAIL, MemberPermission.FREE);

        executeAdminFilter(token);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("관리자 쿠키 필터는 type 클레임이 없는 토큰을 인증하지 않는다")
    void admin_filter_rejects_token_without_type() throws Exception {
        executeAdminFilter(createTokenWithoutType(ADMIN_EMAIL));

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("관리자 쿠키 필터는 문자열이 아닌 type 클레임을 인증하지 않는다")
    void admin_filter_rejects_token_with_non_string_type() throws Exception {
        executeAdminFilter(createToken(42, ADMIN_EMAIL));

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    private MockHttpServletResponse executeApiFilter(String token) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean filterChainInvoked = new AtomicBoolean(false);

        new JwtValidationFilter(jwtService, new ObjectMapper())
                .doFilter(request, response, (ignoredRequest, ignoredResponse) -> filterChainInvoked.set(true));

        assertThat(filterChainInvoked)
                .as("잘못된 토큰은 보호된 API 필터 체인으로 전달되면 안 됨")
                .isFalse();
        return response;
    }

    private void executeAdminFilter(String token) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("ADMIN_TOKEN", token));

        new AdminJwtCookieFilter(jwtService, ADMIN_EMAIL)
                .doFilter(request, new MockHttpServletResponse(), (ignoredRequest, ignoredResponse) -> { });
    }

    private String createTokenWithoutType(String email) {
        return createToken(null, email);
    }

    private String createToken(Object type, String email) {
        long now = System.currentTimeMillis();
        SecretKey secretKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET));
        var builder = Jwts.builder()
                .subject("1")
                .claim("email", email)
                .claim("permissions", MemberPermission.FREE)
                .issuedAt(new Date(now))
                .expiration(new Date(now + ACCESS_EXPIRE_TIME));
        if (type != null) {
            builder.claim("type", type);
        }
        return builder.signWith(secretKey).compact();
    }
}
