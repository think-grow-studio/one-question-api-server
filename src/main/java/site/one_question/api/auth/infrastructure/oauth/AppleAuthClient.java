package site.one_question.api.auth.infrastructure.oauth;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Apple `/auth/token` (authorization_code → refresh_token 교환)와
 * `/auth/revoke` (refresh_token 폐기) 호출.
 *
 * 회원 탈퇴 시 App Store Review Guideline 5.1.1(v) 충족을 위한 token revoke 흐름에 사용.
 */
@Component
@Slf4j
public class AppleAuthClient {

    private static final String TOKEN_URL = "https://appleid.apple.com/auth/token";
    private static final String REVOKE_URL = "https://appleid.apple.com/auth/revoke";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final AppleClientSecretGenerator clientSecretGenerator;
    private final String clientId;

    public AppleAuthClient(
            HttpClient httpClient,
            ObjectMapper objectMapper,
            AppleClientSecretGenerator clientSecretGenerator,
            @Value("${apple.oauth.client-id}") String clientId
    ) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.clientSecretGenerator = clientSecretGenerator;
        this.clientId = clientId;
    }

    /**
     * authorization_code를 refresh_token으로 교환.
     * 실패 시 빈 Optional 대신 예외를 던지지 않고 null 반환 — 호출부에서 best-effort 처리.
     */
    public String exchangeCodeForRefreshToken(String authorizationCode) {
        try {
            Map<String, String> form = new LinkedHashMap<>();
            form.put("client_id", clientId);
            form.put("client_secret", clientSecretGenerator.generate());
            form.put("code", authorizationCode);
            form.put("grant_type", "authorization_code");

            HttpResponse<String> response = post(TOKEN_URL, form);

            if (response.statusCode() != 200) {
                log.warn("Apple token 교환 실패: status={}, body={}", response.statusCode(), response.body());
                return null;
            }

            Map<String, Object> json = objectMapper.readValue(response.body(), new TypeReference<>() {});
            return (String) json.get("refresh_token");
        } catch (Exception e) {
            log.warn("Apple token 교환 중 예외 발생: {}", e.getMessage());
            return null;
        }
    }

    /**
     * refresh_token 폐기. App Store 심사 가이드라인 5.1.1(v) 대응.
     * 실패해도 회원 탈퇴 흐름은 계속 진행되어야 하므로 예외를 던지지 않는다.
     */
    public void revokeRefreshToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        try {
            Map<String, String> form = new LinkedHashMap<>();
            form.put("client_id", clientId);
            form.put("client_secret", clientSecretGenerator.generate());
            form.put("token", refreshToken);
            form.put("token_type_hint", "refresh_token");

            HttpResponse<String> response = post(REVOKE_URL, form);

            if (response.statusCode() != 200) {
                log.warn("Apple token revoke 실패: status={}, body={}", response.statusCode(), response.body());
            }
        } catch (Exception e) {
            log.warn("Apple token revoke 중 예외 발생: {}", e.getMessage());
        }
    }

    private HttpResponse<String> post(String url, Map<String, String> form) throws Exception {
        String body = form.entrySet().stream()
                .map(entry -> URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8)
                        + "=" + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
