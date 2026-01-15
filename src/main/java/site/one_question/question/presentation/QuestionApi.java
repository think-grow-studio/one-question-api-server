package site.one_question.question.presentation;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import site.one_question.question.presentation.response.GetDailyQuestionResponse;

import java.time.LocalDate;

@Tag(name = "Question", description = "질문 관련 API")
public interface QuestionApi {

    @Operation(
            summary = "날짜별 질문 조회",
            description = "특정 날짜에 해당하는 오늘의 질문을 조회합니다. 매일 하나의 질문이 제공됩니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "질문 조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = GetDailyQuestionResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "해당 날짜의 질문이 존재하지 않음",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = """
                                            {
                                                "code": "QUESTION_NOT_FOUND",
                                                "message": "해당 날짜의 질문을 찾을 수 없습니다."
                                            }
                                            """
                            )
                    )
            )
    })
    GetDailyQuestionResponse getQuestionByDate(
            @Parameter(
                    description = "조회할 날짜 (yyyy-MM-dd 형식)",
                    example = "2024-01-15",
                    required = true
            )
            LocalDate date
    );
}
