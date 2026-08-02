package site.one_question.integrate.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import site.one_question.api.member.domain.Member;
import site.one_question.api.member.domain.MemberPermission;
import site.one_question.integrate.test_config.IntegrateTest;

@DisplayName("토큰 재발급 type 검증 통합 테스트")
class ReissueTokenTypeIntegrateTest extends IntegrateTest {

    private static final String SECRET =
            "dGVzdC1zZWNyZXQta2V5LWZvci1kZXZlbG9wbWVudC1vbmx5LTMyYnl0ZXM=";

    @Test
    @DisplayName("문자열이 아닌 type 클레임의 토큰은 유효하지 않은 리프레시 토큰으로 응답한다")
    void reissue_rejects_token_with_non_string_type() throws Exception {
        Member member = testMemberUtils.createSave();
        String token = createToken(member, 42);
        testRefreshTokenUtils.createSave_Valid(member, token);

        mockMvc.perform(post(AUTH_API + "/reissue-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TokenRequest(token))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH-001"));
    }

    @Test
    @DisplayName("type 클레임이 없는 토큰은 유효하지 않은 리프레시 토큰으로 응답한다")
    void reissue_rejects_token_without_type() throws Exception {
        Member member = testMemberUtils.createSave();
        String token = createToken(member, null);
        testRefreshTokenUtils.createSave_Valid(member, token);

        mockMvc.perform(post(AUTH_API + "/reissue-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TokenRequest(token))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH-001"));
    }

    private String createToken(Member member, Object type) {
        long now = System.currentTimeMillis();
        SecretKey secretKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET));
        var builder = Jwts.builder()
                .subject(String.valueOf(member.getId()))
                .claim("email", member.getEmail())
                .claim("permissions", MemberPermission.FREE)
                .issuedAt(new Date(now))
                .expiration(Date.from(Instant.ofEpochMilli(now).plusSeconds(3600)));
        if (type != null) {
            builder.claim("type", type);
        }
        return builder.signWith(secretKey).compact();
    }

    private record TokenRequest(String refreshToken) {
    }
}
