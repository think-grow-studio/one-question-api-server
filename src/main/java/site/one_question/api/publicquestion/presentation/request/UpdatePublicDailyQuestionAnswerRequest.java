package site.one_question.api.publicquestion.presentation.request;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "공개 일일 질문 답변 수정 요청")
public record UpdatePublicDailyQuestionAnswerRequest(
        @Schema(description = "수정할 답변 내용", example = "오늘은 정말 특별한 하루였다")
        String content
) {}
