package site.one_question.api.question.presentation.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import site.one_question.api.question.domain.DailyQuestion;
import site.one_question.api.question.domain.DailyQuestionCandidate;

@Schema(description = "오늘의 질문 응답")
public record ServeDailyQuestionResponse(
        @Schema(description = "오늘의 질문 ID", example = "43")
        Long dailyQuestionId,

        @Schema(description = "현재 선택된 질문 ID", example = "7")
        Long questionId,

        @Schema(description = "현재 선택된 질문 내용", example = "오늘 하루에 제목을 붙인다면?")
        String content,

        @Schema(description = "현재 선택된 질문 보충 설명", example = "ex) 폭풍 전야", nullable = true)
        String description,

        @Schema(description = "질문 1년 사이클 횟수", example = "1")
        Long questionCycle,

        @Schema(description = "질문 변경 횟수 (리로드 횟수)", example = "2")
        Long changeCount,

        @Schema(description = "현재 선택된 질문 좋아요 여부", example = "false")
        boolean liked,

        @Schema(description = "후보 질문 목록 (최초 제공 질문 + 리로드한 질문들)")
        List<CandidateDto> candidates
) {
    @Schema(description = "후보 질문 항목")
    public record CandidateDto(
            @Schema(description = "질문 ID", example = "7")
            Long questionId,

            @Schema(description = "질문 내용", example = "오늘 하루에 제목을 붙인다면?")
            String content,

            @Schema(description = "질문 보충 설명", example = "ex) 폭풍 전야", nullable = true)
            String description,

            @Schema(description = "받은 순서 (1=최초, 2=첫 번째 리로드, ...)", example = "1")
            int receivedOrder,

            @Schema(description = "좋아요 여부", example = "false")
            boolean liked,

            @Schema(description = "현재 선택된 질문 여부", example = "true")
            boolean selected
    ) {}

    public static ServeDailyQuestionResponse from(
            DailyQuestion dailyQuestion,
            List<DailyQuestionCandidate> candidates,
            Set<Long> likedQuestionIds
    ) {
        Long currentQuestionId = dailyQuestion.getQuestion().getId();

        List<CandidateDto> candidateDtos = candidates.stream()
                .map(c -> new CandidateDto(
                        c.getQuestion().getId(),
                        c.getQuestion().getContent(),
                        c.getQuestion().getDescription(),
                        c.getReceivedOrder(),
                        likedQuestionIds.contains(c.getQuestion().getId()),
                        c.getQuestion().getId().equals(currentQuestionId)
                ))
                .collect(Collectors.toList());

        return new ServeDailyQuestionResponse(
                dailyQuestion.getId(),
                currentQuestionId,
                dailyQuestion.getQuestion().getContent(),
                dailyQuestion.getQuestion().getDescription(),
                (long) dailyQuestion.getQuestionCycle().getCycleNumber(),
                (long) dailyQuestion.getChangeCount(),
                likedQuestionIds.contains(currentQuestionId),
                candidateDtos
        );
    }
}
