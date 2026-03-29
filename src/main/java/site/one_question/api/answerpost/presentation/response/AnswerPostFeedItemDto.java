package site.one_question.api.answerpost.presentation.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.time.ZoneId;
import site.one_question.api.answerpost.domain.AnswerPost;

@Schema(description = "공개 답변 피드 아이템")
public record AnswerPostFeedItemDto(
        @Schema(description = "공개 답변 ID", example = "1")
        Long answerPostId,

        @Schema(description = "질문 내용", example = "오늘 하루에 제목을 붙인다면?")
        String questionContent,

        @Schema(description = "질문 설명", example = "하루를 돌아보며 의미 있는 제목을 붙여보세요")
        String description,

        @Schema(description = "답변 내용", example = "새로운 시작의 날")
        String answerContent,

        @Schema(description = "익명 닉네임", example = "용감한 고양이 42")
        String anonymousNickname,

        @Schema(description = "게시 시각", example = "2024-01-15T14:30:00")
        LocalDateTime postedAt,

        @Schema(description = "좋아요 수", example = "5")
        long likeCount,

        @Schema(description = "현재 사용자의 좋아요 여부", example = "false")
        boolean liked,

        @Schema(description = "본인 작성 여부", example = "false")
        boolean mine
) {
    public static AnswerPostFeedItemDto from(AnswerPost post, String timezone, long likeCount, boolean liked, boolean mine) {
        LocalDateTime postedAt = LocalDateTime.ofInstant(
                post.getPostedAt(), ZoneId.of(timezone));
        return new AnswerPostFeedItemDto(
                post.getId(),
                post.getQuestionAnswer().getDailyQuestionId().getQuestion().getContent(),
                post.getQuestionAnswer().getDailyQuestionId().getQuestion().getDescription(),
                post.getQuestionAnswer().getContent(),
                post.getAnonymousNickname(),
                postedAt,
                likeCount,
                liked,
                mine
        );
    }
}
