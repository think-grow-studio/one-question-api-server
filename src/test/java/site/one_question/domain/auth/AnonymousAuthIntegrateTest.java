package site.one_question.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.http.HttpHeaders.ACCEPT_LANGUAGE;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import site.one_question.api.auth.domain.exception.FirebaseTokenVerificationException;
import site.one_question.api.auth.infrastructure.oauth.FirebaseTokenVerifier.FirebaseTokenPayload;
import site.one_question.api.member.domain.AuthSocialProvider;
import site.one_question.api.member.domain.Member;
import site.one_question.api.question.domain.QuestionCycle;
import site.one_question.global.common.HttpHeaderConstant;
import site.one_question.test_config.IntegrateTest;

@DisplayName("익명 인증 API 통합 테스트")
class AnonymousAuthIntegrateTest extends IntegrateTest {

    private final String AUTH_ANONYMOUS_URL = AUTH_API + "/anonymous";

    @Nested
    @DisplayName("성공 케이스")
    class SuccessTest {

        @Test
        @DisplayName("익명 회원가입 성공 시 회원이 생성되고 토큰이 반환된다")
        void anonymous_signup_creates_member_and_returns_tokens() throws Exception {
            // given
            String firebaseUid = "firebase-uid-abc123";
            given(firebaseTokenVerifier.verify(anyString()))
                    .willReturn(new FirebaseTokenPayload(firebaseUid));

            String requestBody = objectMapper.writeValueAsString(
                    new TestAnonymousAuthRequest("fake-firebase-id-token")
            );

            // when & then
            mockMvc.perform(post(AUTH_ANONYMOUS_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .header(ACCEPT_LANGUAGE, "ko-KR")
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
            assertThat(member.getEmail()).isNull();
            assertThat(member.getFullName()).isEqualTo("Anonymous");
            assertThat(member.getProvider()).isEqualTo(AuthSocialProvider.ANONYMOUS);
            assertThat(member.getProviderId()).isEqualTo(firebaseUid);
            assertThat(member.getPublicId()).startsWith("mem_");

            // QuestionCycle 검증
            List<QuestionCycle> cycles = questionCycleRepository.findAll();
            assertThat(cycles).hasSize(1);
            assertThat(cycles.get(0).getMember().getId()).isEqualTo(member.getId());
        }

        @Test
        @DisplayName("기존 익명 회원이 다시 로그인하면 isNewMember가 false이다")
        void anonymous_login_existing_member_returns_false() throws Exception {
            // given
            Member existingMember = testMemberUtils.createSave_Anonymous();
            String firebaseUid = existingMember.getProviderId();

            given(firebaseTokenVerifier.verify(anyString()))
                    .willReturn(new FirebaseTokenPayload(firebaseUid));

            String requestBody = objectMapper.writeValueAsString(
                    new TestAnonymousAuthRequest("fake-firebase-id-token")
            );

            // when & then
            mockMvc.perform(post(AUTH_ANONYMOUS_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .header(ACCEPT_LANGUAGE, "ko-KR")
                            .header(HttpHeaderConstant.TIMEZONE, "Asia/Seoul")
                            .content(requestBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isNewMember").value(false));

            // 회원이 추가 생성되지 않았는지 검증
            assertThat(memberRepository.findAll()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("실패 케이스")
    class ExceptionTest {

        @Test
        @DisplayName("Firebase 토큰 검증 실패 시 401을 반환한다")
        void firebase_token_verification_failure_returns_401() throws Exception {
            // given
            given(firebaseTokenVerifier.verify(anyString()))
                    .willThrow(new FirebaseTokenVerificationException("Invalid token"));

            String requestBody = objectMapper.writeValueAsString(
                    new TestAnonymousAuthRequest("invalid-token")
            );

            // when & then
            mockMvc.perform(post(AUTH_ANONYMOUS_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .header(ACCEPT_LANGUAGE, "ko-KR")
                            .header(HttpHeaderConstant.TIMEZONE, "Asia/Seoul")
                            .content(requestBody))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("AUTH-011"));
        }
    }

    private record TestAnonymousAuthRequest(String idToken) {}
}
