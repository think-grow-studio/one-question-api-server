package site.one_question.api.backgroundjob.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Pattern;
import site.one_question.api.backgroundjob.domain.exception.BackgroundJobRequestHashInvalidException;

public record RequestHash(String value) {

    private static final Pattern SHA_256_HEX_PATTERN = Pattern.compile("^[0-9a-f]{64}$");

    public RequestHash {
        if (value == null || !SHA_256_HEX_PATTERN.matcher(value).matches()) {
            throw new BackgroundJobRequestHashInvalidException(value);
        }
    }

    public static RequestHash sha256(String source) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String hash = HexFormat.of().formatHex(digest.digest(source.getBytes(StandardCharsets.UTF_8)));
            return new RequestHash(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm is not available", e);
        }
    }
}
