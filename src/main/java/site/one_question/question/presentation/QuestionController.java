package site.one_question.question.presentation;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import site.one_question.question.presentation.request.CreateAnswerRequest;
import site.one_question.question.presentation.response.CreateAnswerResponse;
import site.one_question.question.presentation.response.ReloadDailyQuestionResponse;
import site.one_question.question.presentation.response.ServeDailyQuestionResponse;
import site.one_question.question.presentation.response.GetQuestionHistoryResponse;
import site.one_question.question.presentation.response.QuestionHistoryItemDto;
import site.one_question.question.presentation.response.QuestionHistoryItemDto.AnswerInfoDto;
import site.one_question.question.presentation.response.QuestionHistoryItemDto.QuestionInfoDto;
import site.one_question.question.presentation.response.QuestionHistoryItemDto.Status;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/questions")
public class QuestionController implements QuestionApi {

    @Override
    @GetMapping("/daily/{date}")
    public ServeDailyQuestionResponse serveDailyQuestion(@PathVariable LocalDate date) {
        // TODO: 실제 구현
        return new ServeDailyQuestionResponse(
                1L,
                "오늘 가장 감사했던 순간은?",
                "보충 설명",
                1L,
                1L
        );
    }

    @Override
    @GetMapping("/histories")
    public GetQuestionHistoryResponse getQuestionHistory(
            @RequestParam LocalDate baseDate,
            @RequestParam(defaultValue = "BOTH") String direction,
            @RequestParam(defaultValue = "5") Integer size
    ) {
        // TODO: 실제 구현
        // 더미 데이터로 3가지 상태를 보여주는 예시
        List<QuestionHistoryItemDto> items = List.of(
                // 상태 1: 질문 받음 + 답변 완료
                new QuestionHistoryItemDto(
                        baseDate,
                        Status.ANSWERED,
                        new QuestionInfoDto(1L, "오늘 하루에 제목을 붙인다면?", "ex) 폭풍 전야", 2L, 3L),
                        new AnswerInfoDto(101L, "새로운 시작의 날", "2024-01-15T14:30:00")
                ),
                // 상태 2: 질문 받음 + 답변 없음
                new QuestionHistoryItemDto(
                        baseDate.minusDays(1),
                        Status.UNANSWERED,
                        new QuestionInfoDto(2L, "오늘 가장 감사했던 순간은?", null, 1L, 2L),
                        null
                ),
                // 상태 3: 질문 없음
                new QuestionHistoryItemDto(
                        baseDate.minusDays(2),
                        Status.NO_QUESTION,
                        null,
                        null
                )
        );

        return new GetQuestionHistoryResponse(
                items,
                true,  // hasPrevious
                true,  // hasNext
                baseDate.minusDays(2),
                baseDate
        );
    }

    @Override
    @PostMapping("/daily/{date}/reload")
    public ReloadDailyQuestionResponse reloadDailyQuestion(@PathVariable LocalDate date) {
        // TODO: 실제 구현
        return new ReloadDailyQuestionResponse(
                2L,
                "오늘 가장 행복했던 순간은?",
                "ex) 점심 먹을 때",
                1L,
                1L
        );
    }
}
