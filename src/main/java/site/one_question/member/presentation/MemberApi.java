package site.one_question.member.presentation;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import site.one_question.member.presentation.request.UpdateMemberRequest;
import site.one_question.member.presentation.response.GetMemberResponse;

@Tag(name = "Member", description = "회원 관련 API")
public interface MemberApi {

    @Operation(
            summary = "내 정보 조회",
            description = "현재 로그인한 회원의 정보를 조회합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = GetMemberResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 실패",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = """
                                            {
                                                "code": "UNAUTHORIZED",
                                                "message": "인증이 필요합니다."
                                            }
                                            """
                            )
                    )
            )
    })
    ResponseEntity<GetMemberResponse> getMe(Long memberId);

    @Operation(
            summary = "내 정보 수정",
            description = """
                    현재 로그인한 회원의 정보를 수정합니다.
                    수정 가능한 필드: 이름(fullName), 사용 언어(locale)
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "204",
                    description = "수정 성공"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 실패",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = """
                                            {
                                                "code": "UNAUTHORIZED",
                                                "message": "인증이 필요합니다."
                                            }
                                            """
                            )
                    )
            )
    })
    ResponseEntity<Void> updateMe(
            Long memberId,
            @RequestBody(
                    description = "회원 정보 수정 요청",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = UpdateMemberRequest.class)
                    )
            )
            UpdateMemberRequest request
    );
}
