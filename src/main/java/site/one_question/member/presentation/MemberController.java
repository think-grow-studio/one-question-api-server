package site.one_question.member.presentation;

import lombok.RequiredArgsConstructor;
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

@RestController
@RequestMapping("/api/v1/members")
@RequiredArgsConstructor
public class MemberController implements MemberApi {

    private final MemberApplication memberApplication;

    @Override
    @GetMapping("/me")
    public ResponseEntity<GetMemberResponse> getMe(@PrincipalId Long memberId) {
        GetMemberResponse response = memberApplication.getMe(memberId);
        return ResponseEntity.ok(response);
    }

    @Override
    @PatchMapping("/me")
    public ResponseEntity<Void> updateMe(
            @PrincipalId Long memberId,
            @RequestBody UpdateMemberRequest request
    ) {
        memberApplication.updateMe(memberId, request.fullName(), request.locale());
        return ResponseEntity.noContent().build();
    }
}
