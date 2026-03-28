package site.one_question.api.answerpost.presentation;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import site.one_question.api.answerpost.application.AnswerPostApplication;
import site.one_question.api.answerpost.presentation.request.PublishAnswerPostRequest;
import site.one_question.api.answerpost.presentation.response.AnswerPostFeedResponse;

import site.one_question.api.answerpost.presentation.response.ToggleLikeResponse;
import site.one_question.api.auth.infrastructure.annotation.PrincipalId;
import site.one_question.global.common.HttpHeaderConstant;

@Slf4j
@RestController
@RequestMapping("/api/v1/answer-posts")
@RequiredArgsConstructor
public class AnswerPostController implements AnswerPostApi {

    private final AnswerPostApplication answerPostApplication;

    @Override
    @GetMapping
    public ResponseEntity<AnswerPostFeedResponse> getFeed(
            @PrincipalId Long memberId,
            @RequestParam(required = false) Instant cursor,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestHeader(HttpHeaderConstant.TIMEZONE) String timezone
    ) {
        log.info("[API] 피드 조회 요청 시작 - cursor: {}, size: {}", cursor, size);
        AnswerPostFeedResponse response = answerPostApplication.getFeed(memberId, cursor, size, timezone);
        log.info("[API] 피드 조회 요청 종료 - itemCount: {}", response.items().size());
        return ResponseEntity.ok(response);
    }

    @Override
    @PostMapping("/{id}/like")
    public ResponseEntity<ToggleLikeResponse> toggleLike(
            @PrincipalId Long memberId,
            @PathVariable Long id
    ) {
        log.info("[API] 좋아요 토글 요청 시작 - answerPostId: {}", id);
        ToggleLikeResponse response = answerPostApplication.toggleLike(memberId, id);
        log.info("[API] 좋아요 토글 요청 종료 - liked: {}", response.liked());
        return ResponseEntity.ok(response);
    }
}
