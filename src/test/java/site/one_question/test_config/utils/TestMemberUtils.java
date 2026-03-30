package site.one_question.test_config.utils;

import java.time.LocalDate;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import site.one_question.api.member.domain.AuthSocialProvider;
import site.one_question.api.member.domain.Member;
import site.one_question.api.member.domain.MemberRepository;

@Component
@RequiredArgsConstructor
public class TestMemberUtils {

    private final MemberRepository memberRepository;
    private static int uniqueId = 0;

    public Member createSave() {
        Member member = Member.create(
                "test" + uniqueId + "@test.com",
                "테스트유저" + uniqueId,
                AuthSocialProvider.GOOGLE,
                "provider-id-" + uniqueId++,
                "ko-KR",
                LocalDate.now(ZoneId.of("Asia/Seoul"))
        );
        return memberRepository.save(member);
    }

    public Member createSave_With_Email(String email) {
        Member member = Member.create(
                email,
                "테스트유저" + uniqueId,
                AuthSocialProvider.GOOGLE,
                "provider-id-" + uniqueId++,
                "ko-KR",
                LocalDate.now(ZoneId.of("Asia/Seoul"))
        );
        return memberRepository.save(member);
    }

    public Member createSave_Anonymous() {
        Member member = Member.create(
                null,
                "Anonymous",
                AuthSocialProvider.ANONYMOUS,
                "firebase-uid-" + uniqueId++,
                "ko-KR",
                LocalDate.now(ZoneId.of("Asia/Seoul"))
        );
        return memberRepository.save(member);
    }

    public Member createSave_With_JoinedDate(LocalDate joinedDate) {
        Member member = Member.create(
                "test" + uniqueId + "@test.com",
                "테스트유저" + uniqueId,
                AuthSocialProvider.GOOGLE,
                "provider-id-" + uniqueId++,
                "ko-KR",
                joinedDate
        );
        return memberRepository.save(member);
    }
}
