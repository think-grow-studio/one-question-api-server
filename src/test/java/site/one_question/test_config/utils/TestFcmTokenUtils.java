package site.one_question.test_config.utils;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import site.one_question.api.member.domain.Member;
import site.one_question.api.notification.domain.FcmToken;
import site.one_question.api.notification.domain.FcmTokenRepository;

@Component
@RequiredArgsConstructor
public class TestFcmTokenUtils {

    private final FcmTokenRepository fcmTokenRepository;

    public FcmToken createSave(Member member, String token) {
        return fcmTokenRepository.save(FcmToken.create(member, token));
    }
}
