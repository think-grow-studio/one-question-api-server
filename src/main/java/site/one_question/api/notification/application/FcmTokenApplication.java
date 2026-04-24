package site.one_question.api.notification.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.one_question.api.member.domain.Member;
import site.one_question.api.member.domain.MemberService;
import site.one_question.api.notification.domain.FcmTokenService;
import site.one_question.api.notification.presentation.request.DeleteFcmTokenRequest;
import site.one_question.api.notification.presentation.request.RegisterFcmTokenRequest;

@Service
@Transactional
@RequiredArgsConstructor
public class FcmTokenApplication {

    private final MemberService memberService;
    private final FcmTokenService fcmTokenService;

    public void registerFcmToken(Long memberId, RegisterFcmTokenRequest request) {
        Member member = memberService.findById(memberId);
        fcmTokenService.register(member, request.tokenValue());
    }

    public void deleteFcmToken(Long memberId, DeleteFcmTokenRequest request) {
        Member member = memberService.findById(memberId);
        fcmTokenService.delete(member, request.tokenValue());
    }
}
