package site.one_question.global.security.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import site.one_question.auth.domain.OneQuestionPrincipal;
import site.one_question.member.domain.MemberPermission;

@Service
public class JwtService {

    private static final String TYPE = "type";
    private static final String ACCESS_TYPE = "access";
    private static final String REFRESH_TYPE = "refresh";
    private static final String EMAIL = "email";
    private static final String PERMISSIONS = "permissions";

    private final SecretKey secretKey;
    private final long accessExpireTime;
    private final long refreshExpireTime;

    public JwtService(
            @Value("${jwt.secret-key}") String secretKey,
            @Value("${jwt.access-expire-time}") long accessExpireTime,
            @Value("${jwt.refresh-expire-time}") long refreshExpireTime
    ) {
        this.secretKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secretKey));
        this.accessExpireTime = accessExpireTime;
        this.refreshExpireTime = refreshExpireTime;
    }

    public String issueAccessToken(Long memberId,String email, MemberPermission permission) {
        return createToken(memberId, ACCESS_TYPE,email,permission, accessExpireTime);
    }

    public String issueRefreshToken(Long memberId,String email, MemberPermission permission) {
        return createToken(memberId, REFRESH_TYPE,email,permission, refreshExpireTime);
    }

    private String createToken(Long memberId, String type,String email,MemberPermission permission, long expireTime) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(String.valueOf(memberId))
                .claim(TYPE, type)
                .claim(EMAIL,email)
                .claim(PERMISSIONS, permission)
                .issuedAt(new Date(now))
                .expiration(new Date(now + expireTime))
                .signWith(secretKey)
                .compact();
    }

    public boolean isValid(String token) {
        try {
            parseToken(token);
            return true;
        } catch (JwtException e) {
            return false;
        }
    }

    public boolean isRefreshToken(String token) {
        return REFRESH_TYPE.equals(parseToken(token).get(TYPE, String.class));
    }

    public Long extractMemberId(String token) {
        return Long.valueOf(parseToken(token).getSubject());
    }

    public Instant extractExpiration(String token) {
        return parseToken(token).getExpiration().toInstant();
    }

    public MemberPermission extractPermission(String token) {
        String permission = parseToken(token).get(PERMISSIONS, String.class);
        return MemberPermission.valueOf(permission);
    }

    public String extractEmail(String token) {
        return parseToken(token).get(EMAIL, String.class);
    }

    public OneQuestionPrincipal extractPrincipal(String token) {
        return new OneQuestionPrincipal(extractMemberId(token),extractEmail(token), extractPermission(token));
    }

    private Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
