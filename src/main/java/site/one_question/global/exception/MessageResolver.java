package site.one_question.global.exception;

import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MessageResolver {

    private final MessageSource messageSource;

    public String resolve(String key, Object... args) {
        return messageSource.getMessage(
            key,
            args,
            key,
            LocaleContextHolder.getLocale()
        );
    }
}
