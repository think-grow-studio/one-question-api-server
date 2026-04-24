package site.one_question.domain.global.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import site.one_question.global.i18n.LocaleNormalizer;

class LocaleNormalizerTest {

    private final LocaleNormalizer localeNormalizer = new LocaleNormalizer();

    @Test
    @DisplayName("Accept-Language 헤더에서 우선순위가 가장 높은 full locale을 저장한다")
    void normalize_keepsTopPriorityFullLocale_fromAcceptLanguageHeader() {
        assertThat(localeNormalizer.normalize("en-GB,en;q=0.9,ko;q=0.8")).isEqualTo("en-GB");
        assertThat(localeNormalizer.normalize("ko-KR,ko;q=0.9,en-US;q=0.8")).isEqualTo("ko-KR");
    }

    @Test
    @DisplayName("Accept-Language 헤더의 최우선 언어에 region이 없으면 기본 full locale로 승격한다")
    void normalize_upgradesTopPriorityLanguageOnlyRange_toDefaultFullLocale() {
        assertThat(localeNormalizer.normalize("en,ko;q=0.8")).isEqualTo("en-US");
        assertThat(localeNormalizer.normalize("ja,en-US;q=0.8")).isEqualTo("ja-JP");
    }

    @Test
    @DisplayName("Accept-Language 헤더가 지원하지 않는 언어면 기본 locale로 fallback 한다")
    void normalize_fallsBackToDefaultLocale_forUnsupportedAcceptLanguageHeader() {
        assertThat(localeNormalizer.normalize("fr-FR,fr;q=0.9")).isEqualTo("ko-KR");
        assertThat(localeNormalizer.normalize(null)).isEqualTo("ko-KR");
    }
}
