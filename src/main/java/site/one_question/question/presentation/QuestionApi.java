package site.one_question.question.presentation;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import site.one_question.question.presentation.response.ServeDailyQuestionResponse;
import site.one_question.question.presentation.response.GetQuestionHistoryResponse;

import java.time.LocalDate;

@Tag(name = "Question", description = "질문 관련 API")
public interface QuestionApi {

    @Operation(
            summary = "날짜별 질문 제공",
            description = "지정한 날짜의 일일 질문을 제공합니다. 매일 하나의 질문이 제공됩니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "질문 제공 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ServeDailyQuestionResponse.class)
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
    ServeDailyQuestionResponse serveDailyQuestion(
            @Parameter(
                    description = "질문을 제공받을 날짜 (yyyy-MM-dd 형식)",
                    example = "2024-01-15",
                    required = true
            )
            LocalDate date
    );

    @Operation(
            summary = "질문 히스토리 조회",
            description = """
                    질문/답변 히스토리를 조회합니다.
                    기준 날짜를 중심으로 이전/이후 날짜의 기록을 가져옵니다.

                    상태:
                    - ANSWERED: 질문 받음 + 답변 완료
                    - UNANSWERED: 질문 받음 + 답변 없음
                    - NO_QUESTION: 해당 날짜에 질문 없음
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "히스토리 조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = GetQuestionHistoryResponse.class),
                            examples = @ExampleObject(
                                    name = "3가지 상태 예시",
                                    value = """
                                            {
                                                "questions": [
                                                    {
                                                        "date": "2024-01-15",
                                                        "status": "ANSWERED",
                                                        "question": {
                                                            "id": 43,
                                                            "question": "오늘 하루에 제목을 붙인다면?",
                                                            "questionCycle": 1,
                                                            "changeCount": 2
                                                        },
                                                        "answer": {
                                                            "id": 156,
                                                            "answer": "새로운 시작의 날",
                                                            "answeredAt": "2024-01-15T14:30:00"
                                                        }
                                                    },
                                                    {
                                                        "date": "2024-01-14",
                                                        "status": "UNANSWERED",
                                                        "question": {
                                                            "id": 42,
                                                            "question": "오늘 가장 감사했던 순간은?",
                                                            "questionCycle": 1,
                                                            "changeCount": 0
                                                        },
                                                        "answer": null
                                                    },
                                                    {
                                                        "date": "2024-01-13",
                                                        "status": "NO_QUESTION",
                                                        "question": null,
                                                        "answer": null
                                                    }
                                                ],
                                                "hasPrevious": true,
                                                "hasNext": true,
                                                "startDate": "2024-01-13",
                                                "endDate": "2024-01-15"
                                            }
                                            """
                            )
                    )
            )
    })
    GetQuestionHistoryResponse getQuestionHistory(
            @Parameter(
                    description = "기준 날짜 (yyyy-MM-dd 형식). 이 날짜를 중심으로 조회합니다.",
                    example = "2024-01-15",
                    required = true
            )
            LocalDate baseDate,

            @Parameter(
                    description = "조회 방향 (PREVIOUS: 이전 날짜들, NEXT: 다음 날짜들, BOTH: 양방향)",
                    example = "BOTH",
                    schema = @Schema(allowableValues = {"PREVIOUS", "NEXT", "BOTH"})
            )
            String direction,

            @Parameter(
                    description = "가져올 항목 개수 (기본값: 5)",
                    example = "5"
            )
            Integer size
    );
}
