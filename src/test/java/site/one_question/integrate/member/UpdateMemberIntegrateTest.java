package site.one_question.integrate.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.MediaType;
import site.one_question.api.member.domain.Member;
import site.one_question.integrate.test_config.IntegrateTest;

@DisplayName("내 정보 수정 API 통합 테스트")
class UpdateMemberIntegrateTest extends IntegrateTest {

    private final String UPDATE_ME_URL = MEMBERS_API + "/me";

    @Test
    @DisplayName("locale 수정 시 Accept-Language 형식 문자열을 정규화된 단일 locale로 저장한다")
    void updateMe_normalizesLocale_whenLocaleIsUpdated() throws Exception {
        Member member = testMemberUtils.createSave();
        String token = testAuthUtils.createBearerToken(member);

        String requestBody = objectMapper.writeValueAsString(
                new UpdateMemberRequest("새 이름", "en-GB,en;q=0.9,ko;q=0.8")
        );

        mockMvc.perform(patch(UPDATE_ME_URL)
                        .header(AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isNoContent());

        Member updatedMember = memberRepository.findById(member.getId()).orElseThrow();
        assertThat(updatedMember.getFullName()).isEqualTo("새 이름");
        assertThat(updatedMember.getLocale()).isEqualTo("en-GB");
    }

    @ParameterizedTest
    @MethodSource("existingLocales")
    @DisplayName("locale이 null이면 기본 locale로 덮어쓰지 않고 기존 locale을 유지한다")
    void updateMe_keepsLocale_whenLocaleIsNull(String existingLocale) throws Exception {
        Member member = testMemberUtils.createSave_With_Locale(existingLocale);
        String token = testAuthUtils.createBearerToken(member);

        String requestBody = objectMapper.writeValueAsString(
                new UpdateMemberRequest("새 이름", null)
        );

        mockMvc.perform(patch(UPDATE_ME_URL)
                        .header(AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isNoContent());

        Member updatedMember = memberRepository.findById(member.getId()).orElseThrow();
        assertThat(updatedMember.getFullName()).isEqualTo("새 이름");
        assertThat(updatedMember.getLocale()).isEqualTo(existingLocale);
    }

    private record UpdateMemberRequest(String fullName, String locale) {}

    private static Stream<Arguments> existingLocales() {
        return Stream.of(
                Arguments.of("en-US"),
                Arguments.of("ko-KR")
        );
    }
}
