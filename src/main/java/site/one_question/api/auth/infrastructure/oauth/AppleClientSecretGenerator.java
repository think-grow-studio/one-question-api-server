package site.one_question.api.auth.infrastructure.oauth;

import io.jsonwebtoken.Jwts;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Apple `/auth/token`, `/auth/revoke` 호출 시 필요한 client_secret JWT 생성기.
 * Apple Developer Console의 .p8 PEM 키로 ES256 서명한다.
 *
 * - iss: Apple Developer Team ID
 * - sub: client_id (Service ID 또는 App Bundle ID)
 * - aud: https://appleid.apple.com
 * - exp: 최대 6개월. 호출 직전 단기 발급 (5분).
 */
@Component
@Slf4j
public class AppleClientSecretGenerator {

    private static final String AUDIENCE = "https://appleid.apple.com";
    private static final long EXPIRY_SECONDS = 300L;

    private final String teamId;
    private final String keyId;
    private final String clientId;
    private final String privateKeyPem;

    public AppleClientSecretGenerator(
            @Value("${apple.oauth.team-id}") String teamId,
            @Value("${apple.oauth.key-id}") String keyId,
            @Value("${apple.oauth.client-id}") String clientId,
            @Value("${apple.oauth.private-key}") String privateKeyPem
    ) {
        this.teamId = teamId;
        this.keyId = keyId;
        this.clientId = clientId;
        this.privateKeyPem = privateKeyPem;
    }

    public String generate() {
        Instant now = Instant.now();
        return Jwts.builder()
                .header()
                .keyId(keyId)
                .add("alg", "ES256")
                .and()
                .issuer(teamId)
                .subject(clientId)
                .audience().add(AUDIENCE).and()
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(EXPIRY_SECONDS)))
                .signWith(parsePrivateKey(privateKeyPem), Jwts.SIG.ES256)
                .compact();
    }

    private PrivateKey parsePrivateKey(String pem) {
        try {
            // 환경변수로 multiline PEM을 주입하기 어려운 운영 환경(GHA + docker run -e 등)을 위해
            // PEM 통째로 base64 인코딩한 한 줄도 허용한다. PEM 헤더 유무로 자동 감지.
            String content = pem.trim();
            if (!content.startsWith("-----BEGIN")) {
                content = new String(Base64.getDecoder().decode(content));
            }
            String stripped = content
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s+", "");
            byte[] der = Base64.getDecoder().decode(stripped);
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(der);
            return KeyFactory.getInstance("EC").generatePrivate(spec);
        } catch (Exception e) {
            throw new IllegalStateException("Apple private key 파싱 실패", e);
        }
    }
}
