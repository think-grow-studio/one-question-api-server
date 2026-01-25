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
    public ResponseEntity<ServeDailyQuestionResponse> serveDailyQuestion(@PathVariable(name = "date")LocalDate date ,@RequestHeader("Timezone") String timezone) {
        // TODO: SecurityContext에서 memberId 조회 (인증 구현 후)
        Long memberId = 1L;
        ServeDailyQuestionResponse response = questionApplication.serveDailyQuestion(memberId, date, timezone);
        return ResponseEntity.ok(response);
    }

    @Override
    @GetMapping("/histories")
    public GetQuestionHistoryResponse getQuestionHistory(
            @RequestParam LocalDate baseDate,
            @RequestParam(defaultValue = "BOTH") HistoryDirection historyDirection,
            @RequestParam(defaultValue = "5") Integer size,
            @RequestHeader("Timezone") String timezone
    ) {
        Long memberId = 1L; // TODO: SecurityContext에서 조회
        return questionApplication.getQuestionHistory(memberId, baseDate, historyDirection, size, timezone);
    }

    @Override
    @PostMapping("/daily/{date}/reload")
    public ResponseEntity<ServeDailyQuestionResponse> reloadDailyQuestion(
            @PathVariable LocalDate date,
            @RequestHeader("Timezone") String timezone
    ) {
        Long memberId = 1L; // TODO: SecurityContext에서 조회
        ServeDailyQuestionResponse response = questionApplication.reloadDailyQuestion(memberId, date, timezone);
        return ResponseEntity.ok(response);
    }

    @Override
    @PostMapping("/daily/{date}/answer")
    public CreateAnswerResponse createAnswer(
            @PathVariable LocalDate date,
            @RequestHeader("Timezone") String timezone,
            @RequestBody CreateAnswerRequest request
    ) {
        Long memberId = 1L; // TODO: SecurityContext에서 조회
        return questionApplication.createAnswer(memberId, date, request.answer(), timezone);
    }

    @Override
    @PatchMapping("/daily/{date}/answer")
    public UpdateAnswerResponse updateAnswer(
            @PathVariable LocalDate date,
            @RequestHeader("Timezone") String timezone,
            @RequestBody UpdateAnswerRequest request
    ) {
        Long memberId = 1L; // TODO: SecurityContext에서 조회
        return questionApplication.updateAnswer(memberId, date, request.answer(), timezone);
    }
}
