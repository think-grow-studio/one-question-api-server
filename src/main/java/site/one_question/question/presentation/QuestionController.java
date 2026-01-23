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
            @RequestParam(defaultValue = "BOTH") String direction,
            @RequestParam(defaultValue = "5") Integer size,
            @RequestHeader("Timezone") String timezone
    ) {
        /*
         * [질문 히스토리 조회]
         *
         * 1. 현재 로그인한 사용자 정보 조회
         *
         * 2. 미래 날짜 검증
         *    - baseDate > LocalDate.now(ZoneId.of(timezone)) → 에러 (FUTURE_DATE_QUESTION)
         *
         * 3. 조회 날짜 범위 계산
         *    - direction=PREVIOUS: baseDate 기준 이전 size개 날짜
         *    - direction=NEXT: baseDate 기준 이후 size개 날짜
         *    - direction=BOTH: 양방향 각각 size개 (총 size*2 + 1개, baseDate 포함)
         *    - ※ endDate는 today(timezone)를 초과하지 않도록 제한
         *
         * 4. 해당 날짜 범위의 DailyQuestion 조회
         *    - member_id = 현재 사용자
         *    - target_date BETWEEN startDate AND endDate
         *    - LEFT JOIN Answer (답변이 없을 수도 있음)
         *
         * 5. 날짜별 상태 매핑
         *    - 날짜 범위 내 모든 날짜를 순회하며:
         *      a) DailyQuestion 있음 + Answer 있음 → ANSWERED
         *      b) DailyQuestion 있음 + Answer 없음 → UNANSWERED
         *      c) DailyQuestion 없음 → NO_QUESTION
         *
         * 6. 페이징 정보 계산
         *    - hasPrevious: startDate 이전에 데이터가 더 있는지
         *    - hasNext: endDate 이후 ~ today(timezone) 사이에 데이터가 더 있는지
         *    - ※ 사용자 가입일 이전은 조회 불가 (hasPrevious = false)
         *
         * 7. 응답 반환
         *    - histories: [{date, status, question, answer}, ...]
         *    - hasPrevious, hasNext, startDate, endDate
         */
        return null;
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
        /*
         * [답변 작성]
         *
         * 1. 현재 로그인한 사용자 정보 조회
         *
         * 2. DailyQuestion 조회 (memberId + date)
         *    - 없으면 → 에러 (DAILY_QUESTION_NOT_FOUND)
         *    - 다른 사용자의 질문이면 → 에러 (FORBIDDEN)
         *
         * 3. 기존 답변 존재 여부 확인
         *    - 이미 답변이 있으면 → 에러 (ANSWER_ALREADY_EXISTS)
         *    - ※ 답변 수정은 updateAnswer API 사용
         *
         * 4. Answer 레코드 생성
         *    - daily_question_id = 조회된 DailyQuestion의 ID
         *    - content = request.answer()
         *    - answered_at = Instant.now() (DB 저장: UTC)
         *    - timezone = 요청 헤더의 Timezone (DB 저장)
         *
         * 5. 응답 반환
         *    - dailyAnswerId, content, answeredAt (LocalDateTime, timezone 기준 변환)
         */
        return null;
    }

    @Override
    @PatchMapping("/daily/{date}/answer")
    public UpdateAnswerResponse updateAnswer(
            @PathVariable LocalDate date,
            @RequestHeader("Timezone") String timezone,
            @RequestBody UpdateAnswerRequest request
    ) {
        /*
         * [답변 수정]
         *
         * 1. 현재 로그인한 사용자 정보 조회
         *
         * 2. DailyQuestion 조회 (memberId + date)
         *    - 없으면 → 에러 (DAILY_QUESTION_NOT_FOUND)
         *    - 다른 사용자의 질문이면 → 에러 (FORBIDDEN)
         *
         * 3. 기존 Answer 조회
         *    - 없으면 → 에러 (ANSWER_NOT_FOUND)
         *
         * 4. Answer 업데이트
         *    - content = request.answer()
         *    - ※ answered_at은 최초 답변 시간 유지
         *
         * 5. 응답 반환
         *    - dailyAnswerId, content, updatedAt (LocalDateTime, timezone 기준 변환)
         */
        return null;
    }
}
