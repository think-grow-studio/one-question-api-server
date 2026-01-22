package site.one_question.member.domain;

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
}
