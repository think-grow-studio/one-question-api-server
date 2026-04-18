package site.one_question.integrate.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import site.one_question.api.auth.domain.exception.AuthExceptionSpec;
import site.one_question.api.auth.presentation.request.LinkToGoogleRequest;
import site.one_question.api.member.domain.AuthSocialProvider;
import site.one_question.api.member.domain.Member;
import site.one_question.integrate.test_config.IntegrateTest;

@DisplayName("Google 계정 연결 API 통합 테스트")
class LinkToGoogleIntegrateTest extends IntegrateTest {

    private final String LINK_GOOGLE_URL = AUTH_API + "/google/link";

    private Member anonymousMember;
    private String token;

    @BeforeEach
    void setUp() {
        anonymousMember = testMemberUtils.createSave_Anonymous();
        token = testAuthUtils.createBearerToken(anonymousMember);
    }

    @Nested
    @DisplayName("성공 케이스")
    class SuccessTest {

        @Test
        @DisplayName("익명 회원이 Google 계정 연결에 성공한다")
        void anonymous_member_links_to_google_successfully() throws Exception {
            // given
            String googleProviderId = "google-provider-id-new";
            String email = "linked@gmail.com";
            String name = "홍길동";

            GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
            payload.setSubject(googleProviderId);
            payload.setEmail(email);
            payload.set("name", name);

            given(googleTokenVerifier.verify(anyString())).willReturn(payload);

            String requestBody = objectMapper.writeValueAsString(
                    new LinkToGoogleRequest("fake-google-id-token", name)
            );

            // when & then
            mockMvc.perform(post(LINK_GOOGLE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .content(requestBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").isNotEmpty())
                    .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                    .andExpect(jsonPath("$.isNewMember").value(false));

            // DB 검증 - 회원 정보가 업데이트되었는지
            Member updatedMember = memberRepository.findById(anonymousMember.getId()).orElseThrow();
            assertThat(updatedMember.getProvider()).isEqualTo(AuthSocialProvider.GOOGLE);
            assertThat(updatedMember.getProviderId()).isEqualTo(googleProviderId);
            assertThat(updatedMember.getEmail()).isEqualTo(email);
            assertThat(updatedMember.getFullName()).isEqualTo(name);
            // publicId는 유지되어야 함
            assertThat(updatedMember.getPublicId()).isEqualTo(anonymousMember.getPublicId());
        }
    }

    @Nested
    @DisplayName("실패 케이스")
    class ExceptionTest {

        @Test
        @DisplayName("익명이 아닌 회원이 연결을 시도하면 400을 반환한다")
        void non_anonymous_member_link_attempt_returns_400() throws Exception {
            // given
            Member googleMember = testMemberUtils.createSave();
            String googleToken = testAuthUtils.createBearerToken(googleMember);

            GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
            payload.setSubject("new-google-id");
            payload.setEmail("another@gmail.com");

            given(googleTokenVerifier.verify(anyString())).willReturn(payload);

            String requestBody = objectMapper.writeValueAsString(
                    new LinkToGoogleRequest("fake-google-id-token", "테스트")
            );

            // when & then
            mockMvc.perform(post(LINK_GOOGLE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .header(HttpHeaders.AUTHORIZATION, googleToken)
                            .content(requestBody))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(AuthExceptionSpec.ACCOUNT_ALREADY_LINKED.getCode()));
        }

        @Test
        @DisplayName("이미 존재하는 Google 계정으로 연결을 시도하면 409를 반환한다")
        void link_to_existing_google_account_returns_409() throws Exception {
            // given
            Member existingGoogleMember = testMemberUtils.createSave();

            GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
            payload.setSubject(existingGoogleMember.getProviderId());
            payload.setEmail(existingGoogleMember.getEmail());

            given(googleTokenVerifier.verify(anyString())).willReturn(payload);

            String requestBody = objectMapper.writeValueAsString(
                    new LinkToGoogleRequest("fake-google-id-token", "홍길동")
            );

            // when & then
            mockMvc.perform(post(LINK_GOOGLE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .content(requestBody))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value(AuthExceptionSpec.GOOGLE_ACCOUNT_ALREADY_EXISTS.getCode()));
        }
    }
}
