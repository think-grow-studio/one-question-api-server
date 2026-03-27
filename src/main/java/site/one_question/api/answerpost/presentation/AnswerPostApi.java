package site.one_question.api.answerpost.presentation;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import org.springframework.http.ResponseEntity;
import site.one_question.api.answerpost.presentation.request.PublishAnswerPostRequest;
import site.one_question.api.answerpost.presentation.response.AnswerPostFeedResponse;
import site.one_question.api.answerpost.presentation.response.ToggleLikeResponse;

@Tag(name = "AnswerPost", description = "공개 답변 게시글 관련 API")
public interface AnswerPostApi {

    @Operation(summary = "답변 공개 게시", description = "작성한 답변을 익명으로 공개 게시합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "게시 성공"),
            @ApiResponse(responseCode = "404", description = "답변을 찾을 수 없음"),
            @ApiResponse(responseCode = "409", description = "이미 공개된 답변")
    })
    ResponseEntity<Void> publish(
            Long memberId,
            PublishAnswerPostRequest request
    );

    @Operation(summary = "게시 취소", description = "공개된 답변의 게시를 취소합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "게시 취소 성공"),
            @ApiResponse(responseCode = "404", description = "공개 답변을 찾을 수 없음"),
            @ApiResponse(responseCode = "403", description = "본인의 답변이 아님")
    })
    ResponseEntity<Void> unpublish(
            Long memberId,
            @Parameter(name = "id", description = "공개 답변 ID", required = true, in = ParameterIn.PATH)
            Long id
    );

    @Operation(summary = "공개 답변 피드 조회", description = "최신순으로 공개 답변 피드를 조회합니다. 커서 기반 무한 스크롤.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "피드 조회 성공",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = AnswerPostFeedResponse.class)))
    })
    ResponseEntity<AnswerPostFeedResponse> getFeed(
            Long memberId,
            @Parameter(name = "cursor", description = "커서 (이전 응답의 nextCursor)", in = ParameterIn.QUERY)
            Instant cursor,
            @Parameter(name = "size", description = "페이지 크기", example = "20", in = ParameterIn.QUERY)
            Integer size,
            String timezone
    );

    @Operation(summary = "좋아요 토글", description = "공개 답변에 좋아요를 누르거나 취소합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "좋아요 토글 성공",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ToggleLikeResponse.class))),
            @ApiResponse(responseCode = "404", description = "공개 답변을 찾을 수 없음")
    })
    ResponseEntity<ToggleLikeResponse> toggleLike(
            Long memberId,
            @Parameter(name = "id", description = "공개 답변 ID", required = true, in = ParameterIn.PATH)
            Long id
    );
}
