package site.one_question.api.publicquestion.presentation;

import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import site.one_question.api.auth.infrastructure.annotation.PrincipalId;
import site.one_question.api.publicquestion.application.PublicQuestionApplication;
import site.one_question.api.publicquestion.presentation.request.CreatePublicDailyQuestionAnswerRequest;
import site.one_question.api.publicquestion.presentation.request.UpdatePublicDailyQuestionAnswerRequest;
import site.one_question.api.publicquestion.presentation.response.CreatePublicDailyQuestionAnswerResponse;
import site.one_question.api.publicquestion.presentation.response.GetPublicDailyQuestionResponse;
import site.one_question.api.publicquestion.presentation.response.ToggleLikeResponse;
import site.one_question.api.publicquestion.presentation.response.UpdatePublicDailyQuestionAnswerResponse;
import site.one_question.common.HttpHeaderConstant;

@Slf4j
@RestController
@RequestMapping("/api/v1/public-questions")
@RequiredArgsConstructor
public class PublicQuestionController implements PublicQuestionApi {

    private final PublicQuestionApplication publicQuestionApplication;

    @Override
    @GetMapping("/daily/{date}")
    public ResponseEntity<GetPublicDailyQuestionResponse> getPublicDailyQuestion(
            @PrincipalId Long memberId,
            @PathVariable LocalDate date
    ) {
        log.info("[API] 공개 일일 질문 조회 요청 시작 - date: {}", date);
        GetPublicDailyQuestionResponse response = publicQuestionApplication.getPublicDailyQuestion(memberId, date);
        log.info("[API] 공개 일일 질문 조회 요청 종료 - date: {}", date);
        return ResponseEntity.ok(response);
    }

    @Override
    @PostMapping("/{pdqId}/answers")
    public ResponseEntity<CreatePublicDailyQuestionAnswerResponse> createAnswer(
            @PrincipalId Long memberId,
            @PathVariable Long pdqId,
            @RequestHeader(HttpHeaderConstant.TIMEZONE) String timezone,
            @RequestBody CreatePublicDailyQuestionAnswerRequest request
    ) {
        log.info("[API] 공개 일일 질문 답변 작성 요청 시작 - pdqId: {}", pdqId);
        CreatePublicDailyQuestionAnswerResponse response = publicQuestionApplication.createAnswer(
                memberId, pdqId, request.content(), timezone);
        log.info("[API] 공개 일일 질문 답변 작성 요청 종료 - pdqId: {}", pdqId);
        return ResponseEntity.ok(response);
    }

    @Override
    @PatchMapping("/{pdqId}/answers/{answerId}")
    public ResponseEntity<UpdatePublicDailyQuestionAnswerResponse> updateAnswer(
            @PrincipalId Long memberId,
            @PathVariable Long pdqId,
            @PathVariable Long answerId,
            @RequestHeader(HttpHeaderConstant.TIMEZONE) String timezone,
            @RequestBody UpdatePublicDailyQuestionAnswerRequest request
    ) {
        log.info("[API] 공개 일일 질문 답변 수정 요청 시작 - pdqId: {}, answerId: {}", pdqId, answerId);
        UpdatePublicDailyQuestionAnswerResponse response = publicQuestionApplication.updateAnswer(
                memberId, pdqId, answerId, request.content(), timezone);
        log.info("[API] 공개 일일 질문 답변 수정 요청 종료 - pdqId: {}, answerId: {}", pdqId, answerId);
        return ResponseEntity.ok(response);
    }

    @Override
    @DeleteMapping("/{pdqId}/answers/{answerId}")
    public ResponseEntity<Void> deleteAnswer(
            @PrincipalId Long memberId,
            @PathVariable Long pdqId,
            @PathVariable Long answerId
    ) {
        log.info("[API] 공개 일일 질문 답변 삭제 요청 시작 - pdqId: {}, answerId: {}", pdqId, answerId);
        publicQuestionApplication.deleteAnswer(memberId, pdqId, answerId);
        log.info("[API] 공개 일일 질문 답변 삭제 요청 종료 - pdqId: {}, answerId: {}", pdqId, answerId);
        return ResponseEntity.noContent().build();
    }

    @Override
    @PostMapping("/{pdqId}/answers/{answerId}/like")
    public ResponseEntity<ToggleLikeResponse> toggleLike(
            @PrincipalId Long memberId,
            @PathVariable Long pdqId,
            @PathVariable Long answerId
    ) {
        log.info("[API] 공개 일일 질문 답변 좋아요 토글 요청 시작 - pdqId: {}, answerId: {}", pdqId, answerId);
        ToggleLikeResponse response = publicQuestionApplication.toggleLike(memberId, pdqId, answerId);
        log.info("[API] 공개 일일 질문 답변 좋아요 토글 요청 종료 - answerId: {}, liked: {}", answerId, response.liked());
        return ResponseEntity.ok(response);
    }
}
