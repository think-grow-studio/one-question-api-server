package site.one_question.auth.domain;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import site.one_question.auth.domain.exception.RefreshTokenExpiredException;
import site.one_question.auth.domain.exception.RefreshTokenMismatchException;
import site.one_question.auth.domain.exception.RefreshTokenNotFoundException;
import site.one_question.member.domain.Member;

@Component
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;

    public void save(Member member, String token, Instant expiredAt) {
        refreshTokenRepository.findByMemberId(member.getId())
                .ifPresentOrElse(
                        existing -> existing.updateToken(token, expiredAt),
                        () -> refreshTokenRepository.save(RefreshToken.create(member, token, expiredAt))
                );
    }

    public RefreshToken validateAndGet(Long memberId, String token) {
        RefreshToken refreshToken = refreshTokenRepository.findByMemberId(memberId)
                .orElseThrow(() -> new RefreshTokenNotFoundException(memberId));

        if (refreshToken.isExpired()) {
            throw new RefreshTokenExpiredException(memberId);
        }

        if (!refreshToken.matches(token)) {
            throw new RefreshTokenMismatchException(memberId);
        }

        return refreshToken;
    }

    public void delete(Long memberId) {
        refreshTokenRepository.deleteByMemberId(memberId);
    }
}
