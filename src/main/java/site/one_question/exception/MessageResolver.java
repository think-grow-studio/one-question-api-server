package site.one_question.exception;

import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;
import site.one_question.i18n.LocaleNormalizer;

@Component
@RequiredArgsConstructor
public class MessageResolver {

    private final MessageSource messageSource;
    private final LocaleNormalizer localeNormalizer;

    public String resolve(String key, Object... args) {
        return resolve(key, LocaleContextHolder.getLocale(), args);
    }

    public String resolveByLocale(String key, String localeValue, Object... args) {
        String normalizedLocale = localeNormalizer.normalize(localeValue);
        return resolve(key, Locale.forLanguageTag(normalizedLocale), args);
    }

    private String resolve(String key, Locale locale, Object... args) {
        return messageSource.getMessage(
            key,
            args,
            key,
            locale
        );
    }
}
