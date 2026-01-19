package site.one_question.member.presentation;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import site.one_question.member.presentation.request.UpdateMemberRequest;
import site.one_question.member.presentation.response.GetMemberResponse;
import site.one_question.member.presentation.response.UpdateMemberResponse;

@RestController
@RequestMapping("/api/v1/members")
public class MemberController implements MemberApi {

    @Override
    @GetMapping("/me")
    public GetMemberResponse getMe() {
        // TODO: 실제 구현
        return null;
    }

    @Override
    @PatchMapping("/me")
    public UpdateMemberResponse updateMe(@RequestBody UpdateMemberRequest request) {
        // TODO: 실제 구현
        return null;
    }
}
