package site.one_question.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.http.HttpHeaders.ACCEPT_LANGUAGE;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import site.one_question.api.auth.infrastructure.oauth.GoogleTokenVerifier;
import site.one_question.global.common.HttpHeaderConstant;
import site.one_question.api.member.domain.Member;
import site.one_question.api.question.domain.QuestionCycle;
import site.one_question.test_config.IntegrateTest;

@DisplayName("회원가입 API 통합 테스트")
class AuthSignupIntegrateTest extends IntegrateTest {

    private final String AUTH_GOOGLE_URL = AUTH_API + "/google";

    @Autowired
    private GoogleTokenVerifier googleTokenVerifier;

    @Test
    @DisplayName("Google 회원가입 성공 시 회원이 생성되고 토큰이 반환된다")
    void google_signup_creates_member_and_returns_tokens() throws Exception {
        // given
        GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
        payload.setSubject("google-provider-id-123");
        payload.setEmail("newuser@gmail.com");
        payload.set("name", "테스트유저");

        given(googleTokenVerifier.verify(anyString())).willReturn(payload);

        String requestBody = objectMapper.writeValueAsString(
                new TestGoogleAuthRequest("fake-id-token", "newuser@gmail.com", "테스트유저")
        );

        // when & then
        mockMvc.perform(post(AUTH_GOOGLE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(ACCEPT_LANGUAGE, "ko-KR,ko;q=0.9,en-US;q=0.8")
                        .header(HttpHeaderConstant.TIMEZONE, "Asia/Seoul")
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.isNewMember").value(true));

        // DB 검증
        List<Member> members = memberRepository.findAll();
        assertThat(members).hasSize(1);

        Member member = members.get(0);
        assertThat(member.getEmail()).isEqualTo("newuser@gmail.com");
        assertThat(member.getFullName()).isEqualTo("테스트유저");
        assertThat(member.getLocale()).isEqualTo("ko-KR");
        assertThat(member.getPublicId()).startsWith("mem_");

        String uuidPart = member.getPublicId().substring(4);
        assertThat(UUID.fromString(uuidPart))
                .as("publicId의 mem_ 접두사 뒤는 유효한 UUID여야 한다")
                .isNotNull();

        // QuestionCycle 검증
        List<QuestionCycle> cycles = questionCycleRepository.findAll();
        assertThat(cycles).hasSize(1);

        QuestionCycle cycle = cycles.get(0);
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));

        assertThat(cycle.getMember().getId()).isEqualTo(member.getId());
        assertThat(cycle.getCycleNumber()).isEqualTo(1);
        assertThat(cycle.getStartDate()).isEqualTo(today);
        assertThat(cycle.getEndDate()).isEqualTo(today.plusYears(1).minusDays(1));
        assertThat(cycle.getTimezone()).isEqualTo("Asia/Seoul");
        assertThat(cycle.getStartedAt()).isNotNull();
        assertThat(cycle.getEndedAt()).isNotNull();
        assertThat(cycle.getEndedAt()).isAfter(cycle.getStartedAt());
    }

    @Test
    @DisplayName("Accept-Language 헤더의 full locale은 정규화된 단일 locale로 저장된다")
    void google_signup_normalizes_locale_to_fullLocale() throws Exception {
        GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
        payload.setSubject("google-provider-id-en");
        payload.setEmail("english@gmail.com");
        payload.set("name", "English User");

        given(googleTokenVerifier.verify(anyString())).willReturn(payload);

        String requestBody = objectMapper.writeValueAsString(
                new TestGoogleAuthRequest("fake-id-token", "english@gmail.com", "English User")
        );

        mockMvc.perform(post(AUTH_GOOGLE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(ACCEPT_LANGUAGE, "en-GB,en;q=0.9,ko;q=0.8")
                        .header(HttpHeaderConstant.TIMEZONE, "Asia/Seoul")
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isNewMember").value(true));

        Member member = memberRepository.findAll().get(0);
        assertThat(member.getLocale()).isEqualTo("en-GB");
    }

    @Test
    @DisplayName("기존 회원이 다시 로그인하면 isNewMember가 false이고 publicId가 유지된다")
    void google_login_existing_member_keeps_publicId() throws Exception {
        // given - 기존 회원 생성
        Member existingMember = testMemberUtils.createSave();
        String originalPublicId = existingMember.getPublicId();

        GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
        payload.setSubject(existingMember.getProviderId());
        payload.setEmail(existingMember.getEmail());

        given(googleTokenVerifier.verify(anyString())).willReturn(payload);

        String requestBody = objectMapper.writeValueAsString(
                new TestGoogleAuthRequest("fake-id-token", existingMember.getEmail(), null)
        );

        // when & then
        mockMvc.perform(post(AUTH_GOOGLE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(ACCEPT_LANGUAGE, "ko-KR,ko;q=0.9,en-US;q=0.8")
                        .header(HttpHeaderConstant.TIMEZONE, "Asia/Seoul")
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isNewMember").value(false));

        // publicId가 변경되지 않았는지 검증
        Member found = memberRepository.findById(existingMember.getId()).orElseThrow();
        assertThat(found.getPublicId()).isEqualTo(originalPublicId);
    }

    private record TestGoogleAuthRequest(String idToken, String email, String name) {}
}
