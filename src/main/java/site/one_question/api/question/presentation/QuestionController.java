package site.one_question.api.question.presentation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import site.one_question.api.auth.infrastructure.annotation.PrincipalId;
import site.one_question.global.common.HttpHeaderConstant;
import site.one_question.api.question.application.QuestionApplication;
import site.one_question.api.question.presentation.request.CreateAnswerRequest;
import site.one_question.api.question.domain.HistoryDirection;
import site.one_question.api.question.presentation.request.UpdateAnswerRequest;
import site.one_question.api.question.presentation.response.CreateAnswerResponse;
import site.one_question.api.question.presentation.response.ServeDailyQuestionResponse;
import site.one_question.api.question.presentation.response.GetQuestionHistoryResponse;
import site.one_question.api.question.presentation.response.ToggleLikeResponse;
import site.one_question.api.question.presentation.response.UpdateAnswerResponse;

import java.time.LocalDate;

@Slf4j
@RestController
@RequestMapping("/api/v1/questions")
@RequiredArgsConstructor
public class QuestionController implements QuestionApi {

    private final QuestionApplication questionApplication;

    @Override
    @GetMapping("/daily/{date}")
    public ResponseEntity<ServeDailyQuestionResponse> serveDailyQuestion(
            @PrincipalId Long memberId,
            @PathVariable(name = "date") LocalDate date,
            @RequestHeader(HttpHeaderConstant.TIMEZONE) String timezone
    ) {
        log.info("[API] 오늘의 질문 조회 요청 시작 - date: {}", date);
        ServeDailyQuestionResponse response = questionApplication.serveDailyQuestion(memberId, date, timezone);
        log.info("[API] 오늘의 질문 조회 요청 종료 - date: {}", date);
        return ResponseEntity.ok(response);
    }

    @Override
    @GetMapping("/histories")
    public ResponseEntity<GetQuestionHistoryResponse> getQuestionHistory(
            @PrincipalId Long memberId,
            @RequestParam LocalDate baseDate,
            @RequestParam(defaultValue = "BOTH") HistoryDirection historyDirection,
            @RequestParam(defaultValue = "5") Integer size,
            @RequestHeader(HttpHeaderConstant.TIMEZONE) String timezone
    ) {
        log.info("[API] 질문 히스토리 조회 요청 시작 - baseDate: {}, direction: {}, size: {}", baseDate, historyDirection, size);
        GetQuestionHistoryResponse response = questionApplication.getQuestionHistory(memberId, baseDate, historyDirection, size, timezone);
        log.info("[API] 질문 히스토리 조회 요청 종료 - baseDate: {}, direction: {}, size: {}", baseDate, historyDirection, size);
        return ResponseEntity.ok(response);
    }

    @Override
    @PostMapping("/daily/{date}/reload")
    public ResponseEntity<ServeDailyQuestionResponse> reloadDailyQuestion(
            @PrincipalId Long memberId,
            @PathVariable LocalDate date,
            @RequestHeader(HttpHeaderConstant.TIMEZONE) String timezone
    ) {
        log.info("[API] 질문 재요청 시작 - date: {}", date);
        ServeDailyQuestionResponse response = questionApplication.reloadDailyQuestion(memberId, date, timezone);
        log.info("[API] 질문 재요청 요청 종료 - date: {}", date);
        return ResponseEntity.ok(response);
    }

    @Override
    @PostMapping("/daily/{date}/answer")
    public ResponseEntity<CreateAnswerResponse> createAnswer(
            @PrincipalId Long memberId,
            @PathVariable LocalDate date,
            @RequestHeader(HttpHeaderConstant.TIMEZONE) String timezone,
            @RequestBody CreateAnswerRequest request
    ) {
        log.info("[API] 답변 생성 요청 시작 - date: {}", date);
        CreateAnswerResponse response = questionApplication.createAnswer(memberId, date, request.answer(), request.shouldPublish(), timezone);
        log.info("[API] 답변 생성 요청 종료 - date: {}", date);
        return ResponseEntity.ok(response);
    }

    @Override
    @PostMapping("/{questionId}/like")
    public ResponseEntity<ToggleLikeResponse> toggleLike(
            @PrincipalId Long memberId,
            @PathVariable Long questionId
    ) {
        log.info("[API] 질문 좋아요 토글 요청 시작 - questionId: {}", questionId);
        ToggleLikeResponse response = questionApplication.toggleLike(memberId, questionId);
        log.info("[API] 질문 좋아요 토글 요청 종료 - questionId: {}, liked: {}", questionId, response.liked());
        return ResponseEntity.ok(response);
    }

    @Override
    @PatchMapping("/daily/{date}/answer")
    public ResponseEntity<UpdateAnswerResponse> updateAnswer(
            @PrincipalId Long memberId,
            @PathVariable LocalDate date,
            @RequestHeader(HttpHeaderConstant.TIMEZONE) String timezone,
            @RequestBody UpdateAnswerRequest request
    ) {
        log.info("[API] 답변 수정 요청 시작 - date: {}", date);
        UpdateAnswerResponse response = questionApplication.updateAnswer(memberId, date, request.answer(), request.publish(), timezone);
        log.info("[API] 답변 수정 요청 종료 - date: {}", date);
        return ResponseEntity.ok(response);
    }
}
