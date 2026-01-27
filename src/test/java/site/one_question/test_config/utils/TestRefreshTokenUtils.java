package site.one_question.test_config.utils;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import site.one_question.auth.domain.RefreshToken;
import site.one_question.auth.domain.RefreshTokenRepository;
import site.one_question.member.domain.Member;

@Component
@RequiredArgsConstructor
public class TestRefreshTokenUtils {

    private final RefreshTokenRepository repository;

    public RefreshToken createSave(Member member, String token, Instant expiredAt) {
        RefreshToken refreshToken = RefreshToken.create(member, token, expiredAt);
        return repository.save(refreshToken);
    }

    public RefreshToken createSave_Valid(Member member, String token) {
        Instant expiredAt = Instant.now().plusSeconds(3600);
        RefreshToken refreshToken = RefreshToken.create(member, token, expiredAt);
        return repository.save(refreshToken);
    }

    public RefreshToken createSave_Expired(Member member, String token) {
        Instant expiredAt = Instant.now().minusSeconds(3600);
        RefreshToken refreshToken = RefreshToken.create(member, token, expiredAt);
        return repository.save(refreshToken);
    }
}
