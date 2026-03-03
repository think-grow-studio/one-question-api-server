package site.one_question.member.domain;

import java.time.LocalDate;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import site.one_question.member.domain.exception.MemberNotFoundException;

@Component
@RequiredArgsConstructor
public class MemberService {
  private final MemberRepository memberRepository;

  public Member findById(Long memberId) {
    return memberRepository.findById(memberId)
        .orElseThrow(() -> new MemberNotFoundException(memberId));
  }

  public Optional<Member> findByProviderAndProviderId(AuthSocialProvider provider, String providerId) {
    return memberRepository.findByProviderAndProviderId(provider, providerId);
  }

  public Member createMember(
          String email,
          String fullName,
          AuthSocialProvider provider,
          String providerId,
          String locale,
          LocalDate localDate
  ) {
    Member member = Member.create(email, fullName, provider, providerId, locale, localDate);
    return memberRepository.save(member);
  }

  public Member updateMember(Long memberId, String fullName, String locale) {
    Member member = findById(memberId);
    member.updateProfile(fullName, locale);
    return member;
  }

  public void withdraw(Long memberId) {
    Member member = findById(memberId);
    memberRepository.delete(member);
  }
}
