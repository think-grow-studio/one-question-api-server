package site.one_question.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;

@DisplayName("메시지 설정 테스트")
class MessageConfigTest {

    private static final String NOTIFICATION_TITLE_KEY = "notification.question-reminder.title";
    private static final String NOTIFICATION_BODY_KEY = "notification.question-reminder.body";

    private final Locale originalDefaultLocale = Locale.getDefault();

    @AfterEach
    void tearDown() {
        Locale.setDefault(originalDefaultLocale);
    }

    @Test
    @DisplayName("system locale이 영어여도 ko-KR 요청은 한국어 메시지를 반환해야 한다")
    void messageSource_returnsKoreanMessage_whenRequestedLocaleIsKoKrAndSystemLocaleIsEnglish() {
        Locale.setDefault(Locale.US);

        ReloadableResourceBundleMessageSource messageSource =
                (ReloadableResourceBundleMessageSource) new MessageConfig().messageSource();
        messageSource.clearCacheIncludingAncestors();

        Locale requestedLocale = Locale.forLanguageTag("ko-KR");

        String title = messageSource.getMessage(
                NOTIFICATION_TITLE_KEY,
                null,
                NOTIFICATION_TITLE_KEY,
                requestedLocale
        );
        String body = messageSource.getMessage(
                NOTIFICATION_BODY_KEY,
                null,
                NOTIFICATION_BODY_KEY,
                requestedLocale
        );

        assertThat(title).isEqualTo("오늘의 당신은 누구입니까?");
        assertThat(body).isEqualTo("새로운 질문으로 하루를 기록하세요");
    }

    @Test
    @DisplayName("system locale이 영어여도 미지원 언어 요청은 한국어 메시지를 반환해야 한다")
    void messageSource_returnsKoreanMessage_whenRequestedLocaleIsUnsupportedAndSystemLocaleIsEnglish() {
        Locale.setDefault(Locale.US);

        ReloadableResourceBundleMessageSource messageSource =
                (ReloadableResourceBundleMessageSource) new MessageConfig().messageSource();
        messageSource.clearCacheIncludingAncestors();

        Locale requestedLocale = Locale.forLanguageTag("ja-JP");

        String title = messageSource.getMessage(
                NOTIFICATION_TITLE_KEY,
                null,
                NOTIFICATION_TITLE_KEY,
                requestedLocale
        );
        String body = messageSource.getMessage(
                NOTIFICATION_BODY_KEY,
                null,
                NOTIFICATION_BODY_KEY,
                requestedLocale
        );

        assertThat(title).isEqualTo("오늘의 당신은 누구입니까?");
        assertThat(body).isEqualTo("새로운 질문으로 하루를 기록하세요");
    }

    @Test
    @DisplayName("system locale이 한국어여도 en-US 요청은 영어 메시지를 반환해야 한다")
    void messageSource_returnsEnglishMessage_whenRequestedLocaleIsEnUs() {
        Locale.setDefault(Locale.KOREA);

        ReloadableResourceBundleMessageSource messageSource =
                (ReloadableResourceBundleMessageSource) new MessageConfig().messageSource();
        messageSource.clearCacheIncludingAncestors();

        Locale requestedLocale = Locale.forLanguageTag("en-US");

        String title = messageSource.getMessage(
                NOTIFICATION_TITLE_KEY,
                null,
                NOTIFICATION_TITLE_KEY,
                requestedLocale
        );
        String body = messageSource.getMessage(
                NOTIFICATION_BODY_KEY,
                null,
                NOTIFICATION_BODY_KEY,
                requestedLocale
        );

        assertThat(title).isEqualTo("Who are you today?");
        assertThat(body).isEqualTo("Reflect on your day with a new question");
    }
}
