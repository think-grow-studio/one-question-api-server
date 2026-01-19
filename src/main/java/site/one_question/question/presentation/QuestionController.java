package site.one_question.question.presentation;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import site.one_question.question.presentation.request.CreateAnswerRequest;
import site.one_question.question.presentation.response.CreateAnswerResponse;
import site.one_question.question.presentation.response.ServeDailyQuestionResponse;
import site.one_question.question.presentation.response.GetQuestionHistoryResponse;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/questions")
public class QuestionController implements QuestionApi {

    @Override
    @GetMapping("/daily")
    public ServeDailyQuestionResponse serveDailyQuestion(@RequestHeader("Timezone") String timezone) {
        // TODO: 실제 구현
        return null;
    }

    @Override
    @GetMapping("/histories")
    public GetQuestionHistoryResponse getQuestionHistory(
            @RequestParam LocalDate baseDate,
            @RequestParam(defaultValue = "BOTH") String direction,
            @RequestParam(defaultValue = "5") Integer size
    ) {
        // TODO: 실제 구현
        return null;
    }

    @Override
    @PostMapping("/daily/reload")
    public ServeDailyQuestionResponse reloadDailyQuestion(@RequestHeader("Timezone") String timezone) {
        // TODO: 실제 구현
        return null;
    }

    @Override
    @PostMapping("/{dailyQuestionId}/answer")
    public CreateAnswerResponse createAnswer(
            @PathVariable Long dailyQuestionId,
            @RequestBody CreateAnswerRequest request
    ) {
        // TODO: 실제 구현
        return null;
    }
}
