package site.one_question.global.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.AppenderBase;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Sends ERROR level log events to a Discord webhook so critical issues raise an immediate alert.
 */
public class DiscordWebhookAppender extends AppenderBase<ILoggingEvent> {

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                    .withZone(ZoneId.systemDefault());
    private static final int MESSAGE_LIMIT = 400;
    private static final int STACKTRACE_LIMIT = 1200;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private String webhookUrl;
    private String applicationName = "application";
    private boolean enabled;

    public void setWebhookUrl(String webhookUrl) {
        this.webhookUrl = webhookUrl;
    }

    public void setApplicationName(String applicationName) {
        if (applicationName != null && !applicationName.isBlank()) {
            this.applicationName = applicationName;
        }
    }

    @Override
    public void start() {
        this.enabled = webhookUrl != null && !webhookUrl.isBlank();
        if (!enabled) {
            addInfo("DiscordWebhookAppender disabled because webhookUrl is not provided.");
        }
        super.start();
    }

    @Override
    protected void append(ILoggingEvent eventObject) {
        if (!enabled || eventObject == null) {
            return;
        }
        if (!eventObject.getLevel().isGreaterOrEqual(Level.ERROR)) {
            return;
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(webhookUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(buildPayload(eventObject)))
                    .build();

            httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                    .whenComplete((response, throwable) -> {
                        if (throwable != null) {
                            addError("Failed to send log event to Discord", throwable);
                        } else if (response.statusCode() >= 400) {
                            addError("Discord webhook responded with status code " + response.statusCode());
                        }
                    });
        } catch (Exception exception) {
            addError("Unexpected error while sending Discord webhook", exception);
        }
    }

    private String buildPayload(ILoggingEvent eventObject) throws JsonProcessingException {
        Map<String, Object> payload = Map.of("content", buildContent(eventObject));
        return objectMapper.writeValueAsString(payload);
    }

    private String buildContent(ILoggingEvent eventObject) {
        StringBuilder builder = new StringBuilder();
        builder.append("**[").append(applicationName).append("] ")
                .append(eventObject.getLevel()).append("** ");
        builder.append(DATE_TIME_FORMATTER.format(Instant.ofEpochMilli(eventObject.getTimeStamp())));
        builder.append("\nLogger: `").append(eventObject.getLoggerName()).append('`');

        Map<String, String> mdc = eventObject.getMDCPropertyMap();
        appendIfPresent(builder, "Request ID", mdc.get("requestId"));
        appendIfPresent(builder, "URI", mdc.get("uri"));
        appendIfPresent(builder, "IP", mdc.get("clientIp"));
        appendIfPresent(builder, "Member", mdc.get("memberId"));

        builder.append("\nMessage: ")
                .append(truncate(eventObject.getFormattedMessage(), MESSAGE_LIMIT));

        String stackTraceBlock = buildStackTraceBlock(eventObject.getThrowableProxy());
        if (!stackTraceBlock.isBlank()) {
            builder.append('\n').append(stackTraceBlock);
        }

        return builder.toString();
    }

    private void appendIfPresent(StringBuilder builder, String label, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        builder.append("\n").append(label).append(": `").append(value).append('`');
    }

    private String buildStackTraceBlock(IThrowableProxy throwableProxy) {
        if (throwableProxy == null) {
            return "";
        }
        String stackTrace = ThrowableProxyUtil.asString(throwableProxy);
        if (stackTrace == null || stackTrace.isBlank()) {
            return "";
        }
        return "```" + truncate(stackTrace.trim(), STACKTRACE_LIMIT) + "```";
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        if (maxLength <= 3) {
            return value.substring(0, maxLength);
        }
        return value.substring(0, maxLength - 3) + "...";
    }
}
