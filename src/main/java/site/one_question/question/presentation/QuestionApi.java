package site.one_question.question.presentation;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import site.one_question.question.presentation.request.CreateAnswerRequest;
import site.one_question.question.domain.HistoryDirection;
import site.one_question.question.presentation.request.UpdateAnswerRequest;
import site.one_question.question.presentation.response.CreateAnswerResponse;
import site.one_question.question.presentation.response.GetQuestionHistoryResponse;
import site.one_question.question.presentation.response.ServeDailyQuestionResponse;
import site.one_question.question.presentation.response.UpdateAnswerResponse;

import java.time.LocalDate;

@Tag(name = "Question", description = "질문 관련 API")
public interface QuestionApi {

    @Operation(
            summary = "오늘의 질문 제공",
            description = """
                    지정한 날짜의 질문을 제공합니다.
                    클라이언트가 원하는 날짜를 path parameter로 직접 지정합니다.
                    해당 날짜에 이미 질문이 할당되어 있으면 기존 질문을 반환하고,
                    없으면 새 질문을 할당하여 반환합니다.
                    """
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
    ResponseEntity<ServeDailyQuestionResponse> serveDailyQuestion(
            Long memberId,

            @Parameter(
                    name = "date",
                    description = "질문을 제공받을 날짜 (yyyy-MM-dd 형식). 클라이언트의 로컬 타임존 기준 날짜를 전송해야 합니다.",
                    example = "2024-01-15",
                    required = true,
                    in = ParameterIn.PATH
            )
            LocalDate date,

            @Parameter(
                    name = "Timezone",
                    description = "클라이언트의 타임존 (IANA 타임존 형식)",
                    example = "Asia/Seoul",
                    required = true,
                    in = ParameterIn.HEADER
            )
            String timezone
    );

    @Operation(
            summary = "질문 히스토리 조회",
            description = """
                    질문/답변 히스토리를 조회합니다.
                    기준 날짜를 중심으로 이전/이후 날짜의 기록을 가져옵니다.

                    **중요**: baseDate는 클라이언트의 로컬 타임존 기준 날짜를 전송해야 합니다.
                    Timezone 헤더를 통해 미래 날짜 조회를 방지하며,
                    조회 범위의 endDate가 오늘(타임존 기준)을 초과하지 않도록 제한합니다.

                    상태:
                    - ANSWERED: 질문 받음 + 답변 완료
                    - UNANSWERED: 질문 받음
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
                                                "histories": [
                                                    {
                                                        "date": "2024-01-15",
                                                        "status": "ANSWERED",
                                                        "question": {
                                                            "dailyQuestionId": 43,
                                                            "content": "오늘 하루에 제목을 붙인다면?",
                                                            "description": "ex) 폭풍 전야",
                                                            "questionCycle": 1,
                                                            "changeCount": 2
                                                        },
                                                        "answer": {
                                                            "dailyAnswerId": 156,
                                                            "content": "새로운 시작의 날",
                                                            "answeredAt": "2024-01-15T14:30:00"
                                                        }
                                                    },
                                                    {
                                                        "date": "2024-01-14",
                                                        "status": "UNANSWERED",
                                                        "question": {
                                                            "dailyQuestionId": 42,
                                                            "content": "오늘 가장 감사했던 순간은?",
                                                            "description": null,
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
            Long memberId,

            @Parameter(
                    description = "기준 날짜 (yyyy-MM-dd 형식). 클라이언트의 로컬 타임존 기준 날짜를 전송해야 합니다.",
                    example = "2024-01-15",
                    required = true
            )
            LocalDate baseDate,

            @Parameter(
                    description = "조회 방향 (PREVIOUS: 이전 날짜들, NEXT: 다음 날짜들, BOTH: 양방향)",
                    example = "BOTH",
                    schema = @Schema(implementation = HistoryDirection.class)
            )
            HistoryDirection historyDirection,

            @Parameter(
                    description = "가져올 항목 개수 (기본값: 5)",
                    example = "5"
            )
            Integer size,

            @Parameter(
                    name = "Timezone",
                    description = "클라이언트의 타임존 (IANA 타임존 형식). 미래 날짜 조회 방지 및 답변 시각 변환에 사용됩니다.",
                    example = "Asia/Seoul",
                    required = true,
                    in = ParameterIn.HEADER
            )
            String timezone
    );

    @Operation(
            summary = "오늘의 질문 재할당",
            description = """
                    지정한 날짜의 질문을 새로운 질문으로 재할당합니다.
                    클라이언트가 원하는 날짜를 path parameter로 직접 지정합니다.
                    기존 질문이 마음에 들지 않을 때 사용할 수 있습니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "질문 재할당 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ServeDailyQuestionResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "재할당 횟수 초과 또는 이미 답변한 질문",
                    content = @Content(
                            mediaType = "application/json",
                            examples = {
                                    @ExampleObject(
                                            name = "재할당 횟수 초과",
                                            value = """
                                                    {
                                                        "code": "RELOAD_LIMIT_EXCEEDED",
                                                        "message": "질문 변경 횟수를 초과했습니다. (최대 2회)"
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "이미 답변한 질문",
                                            value = """
                                                    {
                                                        "code": "ALREADY_ANSWERED",
                                                        "message": "이미 답변한 질문은 변경할 수 없습니다."
                                                    }
                                                    """
                                    )
                            }
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
                                                "code": "DAILY_QUESTION_NOT_FOUND",
                                                "message": "해당 날짜의 질문을 찾을 수 없습니다."
                                            }
                                            """
                            )
                    )
            )
    })
    ResponseEntity<ServeDailyQuestionResponse> reloadDailyQuestion(
            Long memberId,

            @Parameter(
                    name = "date",
                    description = "재할당할 질문의 날짜 (yyyy-MM-dd 형식). 클라이언트의 로컬 타임존 기준 날짜를 전송해야 합니다.",
                    example = "2024-01-15",
                    required = true,
                    in = ParameterIn.PATH
            )
            LocalDate date,

            @Parameter(
                    name = "Timezone",
                    description = "클라이언트의 타임존 (IANA 타임존 형식)",
                    example = "Asia/Seoul",
                    required = true,
                    in = ParameterIn.HEADER
            )
            String timezone
    );

    @Operation(
            summary = "답변 작성",
            description = """
                    지정한 날짜의 질문에 대한 답변을 작성합니다.
                    클라이언트의 타임존을 헤더로 전달하면, 답변 시각이 해당 타임존 기준으로 변환되어 응답됩니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "답변 작성 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = CreateAnswerResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "이미 답변이 존재함",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = """
                                            {
                                                "code": "ANSWER_ALREADY_EXISTS",
                                                "message": "해당 질문에 이미 답변이 존재합니다."
                                            }
                                            """
                            )
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
                                                "code": "DAILY_QUESTION_NOT_FOUND",
                                                "message": "해당 날짜의 질문을 찾을 수 없습니다."
                                            }
                                            """
                            )
                    )
            )
    })
    CreateAnswerResponse createAnswer(
            Long memberId,

            @Parameter(
                    name = "date",
                    description = "답변할 질문의 날짜 (yyyy-MM-dd 형식). 클라이언트의 로컬 타임존 기준 날짜를 전송해야 합니다.",
                    example = "2024-01-15",
                    required = true,
                    in = ParameterIn.PATH
            )
            LocalDate date,

            @Parameter(
                    name = "Timezone",
                    description = "클라이언트의 타임존 (IANA 타임존 형식)",
                    example = "Asia/Seoul",
                    required = true,
                    in = ParameterIn.HEADER
            )
            String timezone,

            @RequestBody(
                    description = "답변 작성 요청",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = CreateAnswerRequest.class)
                    )
            )
            CreateAnswerRequest request
    );

    @Operation(
            summary = "답변 수정",
            description = """
                    지정한 날짜의 질문에 대한 답변을 수정합니다.
                    클라이언트의 타임존을 헤더로 전달하면, 수정 시각이 해당 타임존 기준으로 변환되어 응답됩니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "답변 수정 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = UpdateAnswerResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "해당 날짜의 질문 또는 답변이 존재하지 않음",
                    content = @Content(
                            mediaType = "application/json",
                            examples = {
                                    @ExampleObject(
                                            name = "질문 없음",
                                            value = """
                                                    {
                                                        "code": "DAILY_QUESTION_NOT_FOUND",
                                                        "message": "해당 날짜의 질문을 찾을 수 없습니다."
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "답변 없음",
                                            value = """
                                                    {
                                                        "code": "ANSWER_NOT_FOUND",
                                                        "message": "해당 질문에 대한 답변을 찾을 수 없습니다."
                                                    }
                                                    """
                                    )
                            }
                    )
            )
    })
    UpdateAnswerResponse updateAnswer(
            Long memberId,

            @Parameter(
                    name = "date",
                    description = "답변을 수정할 질문의 날짜 (yyyy-MM-dd 형식). 클라이언트의 로컬 타임존 기준 날짜를 전송해야 합니다.",
                    example = "2024-01-15",
                    required = true,
                    in = ParameterIn.PATH
            )
            LocalDate date,

            @Parameter(
                    name = "Timezone",
                    description = "클라이언트의 타임존 (IANA 타임존 형식)",
                    example = "Asia/Seoul",
                    required = true,
                    in = ParameterIn.HEADER
            )
            String timezone,

            @RequestBody(
                    description = "답변 수정 요청",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = UpdateAnswerRequest.class)
                    )
            )
            UpdateAnswerRequest request
    );
}
