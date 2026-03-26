package site.one_question.global.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;

/**
 * Prevents noisy JWT 만료 예외 logs from generating Discord alerts.
 */
public class DiscordSkipJwtExpiredFilter extends Filter<ILoggingEvent> {

    private static final String JWT_FILTER_LOGGER =
            "site.one_question.global.security.filter.JwtValidationFilter";
    private static final String KEYWORD = "JWT 만료 예외 발생";

    @Override
    public FilterReply decide(ILoggingEvent event) {
        if (!isStarted() || event == null) {
            return FilterReply.NEUTRAL;
        }
        if (!JWT_FILTER_LOGGER.equals(event.getLoggerName())) {
            return FilterReply.NEUTRAL;
        }
        String message = event.getFormattedMessage();
        if (message != null && message.contains(KEYWORD)) {
            return FilterReply.DENY;
        }
        return FilterReply.NEUTRAL;
    }
}
