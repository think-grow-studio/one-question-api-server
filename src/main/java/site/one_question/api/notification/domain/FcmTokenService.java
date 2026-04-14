package site.one_question.api.notification.domain;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import site.one_question.api.member.domain.Member;

@Component
@RequiredArgsConstructor
public class FcmTokenService {

    private final FcmTokenRepository repository;

    public void register(Member member, String token) {
        repository.deleteByMember(member);
        repository.save(FcmToken.create(member, token));
    }

    public void delete(Member member, String token) {
        repository.findByMemberAndToken(member, token)
                .ifPresent(repository::delete);
    }

    public Optional<FcmToken> findToken(Member member) {
        return repository.findByMember(member);
    }

    public void deleteByMemberId(Long memberId) {
        repository.deleteByMemberId(memberId);
    }
}
