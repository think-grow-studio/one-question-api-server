package site.one_question.question.presentation;

import lombok.RequiredArgsConstructor;
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
import site.one_question.auth.infrastructure.annotation.PrincipalId;
import site.one_question.global.common.HttpHeaders;
import site.one_question.question.application.QuestionApplication;
import site.one_question.question.presentation.request.CreateAnswerRequest;
import site.one_question.question.domain.HistoryDirection;
import site.one_question.question.presentation.request.UpdateAnswerRequest;
import site.one_question.question.presentation.response.CreateAnswerResponse;
import site.one_question.question.presentation.response.ServeDailyQuestionResponse;
import site.one_question.question.presentation.response.GetQuestionHistoryResponse;
import site.one_question.question.presentation.response.UpdateAnswerResponse;

import java.time.LocalDate;

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
            @RequestHeader(HttpHeaders.TIMEZONE) String timezone
    ) {
        ServeDailyQuestionResponse response = questionApplication.serveDailyQuestion(memberId, date, timezone);
        return ResponseEntity.ok(response);
    }

    @Override
    @GetMapping("/histories")
    public GetQuestionHistoryResponse getQuestionHistory(
            @PrincipalId Long memberId,
            @RequestParam LocalDate baseDate,
            @RequestParam(defaultValue = "BOTH") HistoryDirection historyDirection,
            @RequestParam(defaultValue = "5") Integer size,
            @RequestHeader(HttpHeaders.TIMEZONE) String timezone
    ) {
        return questionApplication.getQuestionHistory(memberId, baseDate, historyDirection, size, timezone);
    }

    @Override
    @PostMapping("/daily/{date}/reload")
    public ResponseEntity<ServeDailyQuestionResponse> reloadDailyQuestion(
            @PrincipalId Long memberId,
            @PathVariable LocalDate date,
            @RequestHeader(HttpHeaders.TIMEZONE) String timezone
    ) {
        ServeDailyQuestionResponse response = questionApplication.reloadDailyQuestion(memberId, date, timezone);
        return ResponseEntity.ok(response);
    }

    @Override
    @PostMapping("/daily/{date}/answer")
    public CreateAnswerResponse createAnswer(
            @PrincipalId Long memberId,
            @PathVariable LocalDate date,
            @RequestHeader(HttpHeaders.TIMEZONE) String timezone,
            @RequestBody CreateAnswerRequest request
    ) {
        return questionApplication.createAnswer(memberId, date, request.answer(), timezone);
    }

    @Override
    @PatchMapping("/daily/{date}/answer")
    public UpdateAnswerResponse updateAnswer(
            @PrincipalId Long memberId,
            @PathVariable LocalDate date,
            @RequestHeader(HttpHeaders.TIMEZONE) String timezone,
            @RequestBody UpdateAnswerRequest request
    ) {
        return questionApplication.updateAnswer(memberId, date, request.answer(), timezone);
    }
}
