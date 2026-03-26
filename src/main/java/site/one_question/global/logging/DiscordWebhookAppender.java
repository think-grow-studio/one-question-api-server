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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import site.one_question.global.common.MdcKey;

/**
 * Sends ERROR level log events to a Discord webhook so critical issues raise an immediate alert.
 */
public class DiscordWebhookAppender extends AppenderBase<ILoggingEvent> {

    private static final int MESSAGE_LIMIT = 400;
    private static final int STACKTRACE_LIMIT = 1200;
    private static final int COLOR_ERROR = 0xFF0000;   // red
    private static final int COLOR_WARN = 0xFFA500;    // orange
    private static final int COLOR_DEFAULT = 0x95A5A6;  // gray

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
        Map<String, Object> embed = new LinkedHashMap<>();
        embed.put("title", "[" + applicationName + "] " + eventObject.getLevel());
        embed.put("color", resolveColor(eventObject.getLevel()));
        embed.put("timestamp", Instant.ofEpochMilli(eventObject.getTimeStamp()).toString());

        // description = 로그 메시지
        embed.put("description", truncate(eventObject.getFormattedMessage(), MESSAGE_LIMIT));

        // fields = MDC 정보 + logger
        List<Map<String, Object>> fields = new ArrayList<>();
        fields.add(inlineField("Logger", eventObject.getLoggerName()));

        Map<String, String> mdc = eventObject.getMDCPropertyMap();
        addFieldIfPresent(fields, "Request ID", mdc.get(MdcKey.REQUEST_ID));
        addFieldIfPresent(fields, "URI", mdc.get(MdcKey.REQUEST_URI));
        addFieldIfPresent(fields, "IP", mdc.get(MdcKey.CLIENT_IP));
        addFieldIfPresent(fields, "MemberId", mdc.get(MdcKey.MEMBER_ID));

        // stacktrace
        String stackTrace = buildStackTraceBlock(eventObject.getThrowableProxy());
        if (!stackTrace.isBlank()) {
            fields.add(Map.of("name", "Stacktrace", "value", stackTrace, "inline", false));
        }

        embed.put("fields", fields);

        Map<String, Object> payload = Map.of("embeds", List.of(embed));
        return objectMapper.writeValueAsString(payload);
    }

    private int resolveColor(Level level) {
        if (Level.ERROR.equals(level)) return COLOR_ERROR;
        if (Level.WARN.equals(level)) return COLOR_WARN;
        return COLOR_DEFAULT;
    }

    private Map<String, Object> inlineField(String name, String value) {
        return Map.of("name", name, "value", value != null ? value : "N/A", "inline", true);
    }

    private void addFieldIfPresent(List<Map<String, Object>> fields, String name, String value) {
        if (value != null && !value.isBlank()) {
            fields.add(inlineField(name, value));
        }
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
