package site.one_question.api.publicquestion.presentation;

import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import site.one_question.api.auth.infrastructure.annotation.PrincipalId;
import site.one_question.api.publicquestion.application.PublicQuestionApplication;
import site.one_question.api.publicquestion.presentation.response.GetPublicDailyQuestionResponse;

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
}
