package site.one_question.question.presentation;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import site.one_question.question.presentation.response.GetDailyQuestionResponse;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/questions")
public class QuestionController implements QuestionApi {

    @Override
    @GetMapping("/{date}")
    public GetDailyQuestionResponse getQuestionByDate(@PathVariable LocalDate date) {
        // TODO: 실제 구현
        return new GetDailyQuestionResponse(
                1L,
                "오늘 가장 감사했던 순간은?",
                1L,
                1L
        );
    }
}
