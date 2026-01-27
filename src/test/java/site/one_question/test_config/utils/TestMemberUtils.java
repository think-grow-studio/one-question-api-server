package site.one_question.test_config.utils;

import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import site.one_question.member.domain.AuthSocialProvider;
import site.one_question.member.domain.Member;
import site.one_question.member.domain.MemberRepository;

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
                LocalDate.now()
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
                LocalDate.now()
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
