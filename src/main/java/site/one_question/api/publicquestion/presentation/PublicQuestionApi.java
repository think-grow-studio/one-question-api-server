package site.one_question.api.publicquestion.presentation;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import org.springframework.http.ResponseEntity;
import site.one_question.api.publicquestion.presentation.request.CreatePublicDailyQuestionAnswerRequest;
import site.one_question.api.publicquestion.presentation.request.UpdatePublicDailyQuestionAnswerRequest;
import site.one_question.api.publicquestion.presentation.response.CreatePublicDailyQuestionAnswerResponse;
import site.one_question.api.publicquestion.presentation.response.GetPublicDailyQuestionResponse;
import site.one_question.api.publicquestion.presentation.response.UpdatePublicDailyQuestionAnswerResponse;

@Tag(name = "PublicQuestion", description = "공개 질문 관련 API")
public interface PublicQuestionApi {

    @Operation(
            summary = "공개 일일 질문 조회",
            description = "지정한 날짜(UTC 기준)의 공개 일일 질문을 조회합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = GetPublicDailyQuestionResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 실패",
                    content = @Content
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "해당 날짜의 공개 질문이 존재하지 않음",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = """
                                            {
                                                "code": "PUBLIC-QUESTION-003",
                                                "message": "해당 날짜의 공개 질문을 찾을 수 없습니다."
                                            }
                                            """
                            )
                    )
            )
    })
    ResponseEntity<GetPublicDailyQuestionResponse> getPublicDailyQuestion(Long memberId, LocalDate date);

    @Operation(
            summary = "공개 일일 질문 답변 작성",
            description = "지정한 공개 일일 질문(PDQ)에 답변을 작성합니다. 한 멤버는 동일 PDQ에 1개의 답변만 작성 가능합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "답변 작성 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = CreatePublicDailyQuestionAnswerResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "답변 내용이 비어있거나 길이 초과",
                    content = @Content
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 실패",
                    content = @Content
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "해당 PDQ 가 존재하지 않음",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = """
                                            {
                                                "code": "PUBLIC-QUESTION-003",
                                                "message": "해당 공개 질문을 찾을 수 없습니다."
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "이미 답변한 공개 질문",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = """
                                            {
                                                "code": "PUBLIC-QUESTION-004",
                                                "message": "이미 답변한 공개 질문입니다."
                                            }
                                            """
                            )
                    )
            )
    })
    ResponseEntity<CreatePublicDailyQuestionAnswerResponse> createAnswer(
            Long memberId,
            Long pdqId,
            String timezone,
            CreatePublicDailyQuestionAnswerRequest request
    );

    @Operation(
            summary = "공개 일일 질문 답변 수정",
            description = "지정한 공개 일일 질문(PDQ)의 본인 답변 내용을 수정합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "답변 수정 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = UpdatePublicDailyQuestionAnswerResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "답변 내용이 비어있거나 길이 초과",
                    content = @Content
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 실패",
                    content = @Content
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "해당 PDQ 또는 본인 답변이 존재하지 않음",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = """
                                            {
                                                "code": "PUBLIC-QUESTION-005",
                                                "message": "해당 답변을 찾을 수 없습니다."
                                            }
                                            """
                            )
                    )
            )
    })
    ResponseEntity<UpdatePublicDailyQuestionAnswerResponse> updateAnswer(
            Long memberId,
            Long pdqId,
            String timezone,
            UpdatePublicDailyQuestionAnswerRequest request
    );
}
