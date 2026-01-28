package site.one_question.member.presentation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import site.one_question.auth.infrastructure.annotation.PrincipalId;
import site.one_question.member.application.MemberApplication;
import site.one_question.member.presentation.request.UpdateMemberRequest;
import site.one_question.member.presentation.response.GetMemberResponse;

@Slf4j
@RestController
@RequestMapping("/api/v1/members")
@RequiredArgsConstructor
public class MemberController implements MemberApi {

    private final MemberApplication memberApplication;

    @Override
    @GetMapping("/me")
    public ResponseEntity<GetMemberResponse> getMe(@PrincipalId Long memberId) {
        log.info("[API] 내 정보 조회 요청 시작");
        GetMemberResponse response = memberApplication.getMe(memberId);
        log.info("[API] 내 정보 조회 요청 종료");
        return ResponseEntity.ok(response);
    }

    @Override
    @PatchMapping("/me")
    public ResponseEntity<Void> updateMe(
            @PrincipalId Long memberId,
            @RequestBody UpdateMemberRequest request
    ) {
        log.info("[API] 내 정보 수정 요청 시작");
        memberApplication.updateMe(memberId, request.fullName(), request.locale());
        log.info("[API] 내 정보 수정 요청 종료");
        return ResponseEntity.noContent().build();
    }
}
