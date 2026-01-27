package site.one_question.auth.infrastructure.oauth;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import site.one_question.auth.domain.exception.AppleTokenVerificationException;

@Component
@Slf4j
public class AppleTokenVerifier {

    private static final String APPLE_KEYS_URL = "https://appleid.apple.com/auth/keys";
    private static final String APPLE_ISSUER = "https://appleid.apple.com";
    private static final String REQUIRED_ALGORITHM = "RS256";
    private static final Duration CACHE_DURATION = Duration.ofHours(24);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final String clientId;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Map<String, CachedKey> keyCache = new ConcurrentHashMap<>();

    public AppleTokenVerifier(
            @Value("${apple.oauth.client-id}") String clientId,
            HttpClient httpClient,
            ObjectMapper objectMapper
    ) {
        this.clientId = clientId;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    public AppleTokenPayload verify(String identityToken) {
        try {
            JwtHeader header = extractHeader(identityToken);

            if (!REQUIRED_ALGORITHM.equals(header.alg())) {
                throw new AppleTokenVerificationException("Invalid algorithm: " + header.alg());
            }

            PublicKey publicKey = getPublicKey(header.kid());

            Claims claims = Jwts.parser()
                    .verifyWith(publicKey)
                    .requireIssuer(APPLE_ISSUER)
                    .requireAudience(clientId)
                    .build()
                    .parseSignedClaims(identityToken)
                    .getPayload();

            String providerId = claims.getSubject();
            String email = claims.get("email", String.class);
            return new AppleTokenPayload(providerId, email);
        } catch (AppleTokenVerificationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Apple ID Token 검증 실패: {}", e.getMessage());
            throw new AppleTokenVerificationException(e);
        }
    }

    public record AppleTokenPayload(String providerId, String email) {}

    private JwtHeader extractHeader(String identityToken) {
        try {
            String[] parts = identityToken.split("\\.");
            if (parts.length != 3) {
                throw new AppleTokenVerificationException("Invalid token format");
            }

            String headerJson = new String(Base64.getUrlDecoder().decode(parts[0]));
            Map<String, Object> header = objectMapper.readValue(headerJson, new TypeReference<>() {});

            return new JwtHeader(
                    (String) header.get("kid"),
                    (String) header.get("alg")
            );
        } catch (AppleTokenVerificationException e) {
            throw e;
        } catch (Exception e) {
            throw new AppleTokenVerificationException("Failed to extract header from token: " + e.getMessage());
        }
    }

    private PublicKey getPublicKey(String kid) {
        CachedKey cached = keyCache.get(kid);
        if (cached != null && !cached.isExpired()) {
            return cached.key();
        }

        return fetchPublicKeyFromApple(kid);
    }

    private PublicKey fetchPublicKeyFromApple(String kid) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(APPLE_KEYS_URL))
                    .timeout(REQUEST_TIMEOUT)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            Map<String, Object> jwks = objectMapper.readValue(response.body(), new TypeReference<>() {});

            @SuppressWarnings("unchecked")
            var keys = (java.util.List<Map<String, Object>>) jwks.get("keys");

            for (Map<String, Object> key : keys) {
                String keyId = (String) key.get("kid");
                if (kid.equals(keyId)) {
                    PublicKey publicKey = createPublicKey(key);
                    keyCache.put(keyId, new CachedKey(publicKey, Instant.now().plus(CACHE_DURATION)));
                    return publicKey;
                }
            }

            keyCache.clear();
            log.warn("Public key not found for kid: {}. Cache cleared.", kid);
            throw new AppleTokenVerificationException("Public key not found for kid: " + kid);
        } catch (AppleTokenVerificationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to fetch Apple public keys: {}", e.getMessage());
            throw new AppleTokenVerificationException("Failed to fetch Apple public keys: " + e.getMessage());
        }
    }

    private PublicKey createPublicKey(Map<String, Object> key) throws Exception {
        String n = (String) key.get("n");
        String e = (String) key.get("e");

        byte[] nBytes = Base64.getUrlDecoder().decode(n);
        byte[] eBytes = Base64.getUrlDecoder().decode(e);

        BigInteger modulus = new BigInteger(1, nBytes);
        BigInteger exponent = new BigInteger(1, eBytes);

        RSAPublicKeySpec spec = new RSAPublicKeySpec(modulus, exponent);
        KeyFactory factory = KeyFactory.getInstance("RSA");
        return factory.generatePublic(spec);
    }

    private record JwtHeader(String kid, String alg) {}

    private record CachedKey(PublicKey key, Instant expiration) {
        boolean isExpired() {
            return Instant.now().isAfter(expiration);
        }
    }
}
