package site.one_question.global.i18n;

import java.util.Locale.LanguageRange;
import java.util.Map;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class LocaleNormalizer {

    private static final String DEFAULT_LOCALE = "ko-KR";
    private static final Map<String, String> DEFAULT_LOCALES_BY_LANGUAGE = Map.of(
            "ko", "ko-KR",
            "en", "en-US",
            "ja", "ja-JP"
    );

    public String normalize(String localeValue) {
        if (localeValue == null || localeValue.isBlank()) {
            return DEFAULT_LOCALE;
        }

        String normalizedLocale = localeValue.replace('_', '-').trim();
        Locale locale = extractPrimaryLocale(normalizedLocale);
        if (locale == null) {
            return DEFAULT_LOCALE;
        }

        String language = locale.getLanguage();
        if (!DEFAULT_LOCALES_BY_LANGUAGE.containsKey(language)) {
            return DEFAULT_LOCALE;
        }

        if (locale.getCountry().isBlank()) {
            return DEFAULT_LOCALES_BY_LANGUAGE.get(language);
        }

        return locale.toLanguageTag();
    }

    private Locale extractPrimaryLocale(String localeValue) {
        try {
            for (LanguageRange range : LanguageRange.parse(localeValue)) {
                if (!"*".equals(range.getRange())) {
                    return Locale.forLanguageTag(range.getRange());
                }
            }
        } catch (IllegalArgumentException ignored) {
        }

        String primaryLocale = localeValue.split(",")[0].trim();
        Locale locale = Locale.forLanguageTag(primaryLocale);
        if (locale.getLanguage().isBlank()) {
            return null;
        }
        return locale;
    }
}
