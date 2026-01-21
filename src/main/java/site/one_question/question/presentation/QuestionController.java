package site.one_question.question.presentation;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import site.one_question.question.presentation.request.CreateAnswerRequest;
import site.one_question.question.presentation.request.UpdateAnswerRequest;
import site.one_question.question.presentation.response.CreateAnswerResponse;
import site.one_question.question.presentation.response.ServeDailyQuestionResponse;
import site.one_question.question.presentation.response.GetQuestionHistoryResponse;
import site.one_question.question.presentation.response.UpdateAnswerResponse;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/questions")
public class QuestionController implements QuestionApi {

    @Override
    @GetMapping("/daily")
    public ServeDailyQuestionResponse serveDailyQuestion(@RequestHeader("Timezone") String timezone) {
        /*
         * [오늘의 질문 제공]
         *
         * 1. 현재 로그인한 사용자 정보 조회 (SecurityContext)
         *
         * 2. 클라이언트 타임존 기준 "오늘" 날짜 계산
         *    - UTC 현재 시간 + timezone → LocalDate (오늘)
         *
         * 3. 해당 날짜에 이미 질문이 제공되었는지 확인 (DailyQuestion 테이블)
         *    - 있으면 → 기존 질문 반환 (+ 답변 여부 포함)
         *    - 없으면 → 새 질문 할당 로직 진행
         *
         * 4. 새 질문 할당 시:
         *    a) 현재 사이클 번호 계산
         *       - 사용자의 cycle_start_date 기준으로 오늘이 몇 번째 사이클인지 계산
         *       - (오늘 - cycle_start_date) / 365 + 1 = 사이클 번호
         *       - ※ 윤년 주의: 366일인 해 고려
         *
         *    b) 해당 사이클에서 "제공된" 질문 ID 목록 조회
         *       - DailyQuestion 테이블에서 현재 사이클에 해당하는 question_id들
         *       - ※ "답변 여부"가 아닌 "제공 여부" 기준
         *
         *    c) 전체 질문(365개+) 중 제공되지 않은 질문 필터링
         *       - 전체 Question 목록 - 이미 제공된 Question = 후보군
         *
         *    d) 후보군에서 랜덤 1개 선택
         *       - 후보군이 비어있으면 (365개 모두 제공됨):
         *         → 새 사이클 시작 OR 전체 질문에서 랜덤 (기획 확정 필요)
         *
         *    e) DailyQuestion 레코드 생성
         *       - member_id, question_id, target_date, cycle_number, change_count=0
         *
         * 5. 권한 체크 (과거 날짜 질문 요청 시)
         *    - 오늘 날짜가 아닌 경우:
         *      → FREE 사용자: 광고 시청 필요 (또는 거부)
         *      → PREMIUM 사용자: 허용
         *    - ※ 현재 API는 "오늘" 기준이므로 과거 날짜는 별도 API 필요할 수 있음
         *
         * 6. 응답 반환
         *    - dailyQuestionId, content, description, questionCycle, changeCount
         */
        return null;
    }

    @Override
    @GetMapping("/histories")
    public GetQuestionHistoryResponse getQuestionHistory(
            @RequestParam LocalDate baseDate,
            @RequestParam(defaultValue = "BOTH") String direction,
            @RequestParam(defaultValue = "5") Integer size
    ) {
        /*
         * [질문 히스토리 조회]
         *
         * 1. 현재 로그인한 사용자 정보 조회
         *
         * 2. 조회 날짜 범위 계산
         *    - direction=PREVIOUS: baseDate 기준 이전 size개 날짜
         *    - direction=NEXT: baseDate 기준 이후 size개 날짜
         *    - direction=BOTH: 양방향 각각 size개 (총 size*2 + 1개, baseDate 포함)
         *
         * 3. 해당 날짜 범위의 DailyQuestion 조회
         *    - member_id = 현재 사용자
         *    - target_date BETWEEN startDate AND endDate
         *    - LEFT JOIN Answer (답변이 없을 수도 있음)
         *
         * 4. 날짜별 상태 매핑
         *    - 날짜 범위 내 모든 날짜를 순회하며:
         *      a) DailyQuestion 있음 + Answer 있음 → ANSWERED
         *      b) DailyQuestion 있음 + Answer 없음 → UNANSWERED
         *      c) DailyQuestion 없음 → NO_QUESTION
         *
         * 5. 페이징 정보 계산
         *    - hasPrevious: startDate 이전에 데이터가 더 있는지
         *    - hasNext: endDate 이후에 데이터가 더 있는지
         *    - ※ 사용자 가입일 이전은 조회 불가 (hasPrevious = false)
         *
         * 6. 응답 반환
         *    - histories: [{date, status, question, answer}, ...]
         *    - hasPrevious, hasNext, startDate, endDate
         */
        return null;
    }

    @Override
    @PostMapping("/daily/reload")
    public ServeDailyQuestionResponse reloadDailyQuestion(@RequestHeader("Timezone") String timezone) {
        /*
         * [오늘의 질문 재할당 (변경)]
         *
         * 1. 현재 로그인한 사용자 정보 조회
         *
         * 2. 클라이언트 타임존 기준 "오늘" 날짜 계산
         *
         * 3. 오늘의 DailyQuestion 조회
         *    - 없으면 → 에러 (먼저 질문을 받아야 함)
         *
         * 4. 답변 여부 확인
         *    - 이미 답변했으면 → 에러 (답변 전에만 변경 가능)
         *
         * 5. 변경 횟수 제한 확인
         *    - FREE 사용자:
         *      → change_count >= 2 → 에러 (또는 광고 시청 유도)
         *    - PREMIUM 사용자:
         *      → 무제한 (또는 적절한 상한선)
         *
         * 6. 새 질문 할당
         *    a) 현재 사이클에서 "제공된" 질문 ID 목록 조회
         *       - 현재 질문도 포함 (이미 제공됨)
         *
         *    b) 전체 질문 중 제공되지 않은 질문 필터링
         *       - 후보군이 비어있으면:
         *         → 현재 질문 제외한 전체에서 랜덤 (중복 허용)
         *         → 또는 에러 반환 (변경 불가)
         *
         *    c) 후보군에서 랜덤 1개 선택
         *
         * 7. DailyQuestion 업데이트
         *    - question_id = 새 질문 ID
         *    - change_count += 1
         *    - ※ 기존 질문은 "제공됨" 상태로 유지? 아니면 취소?
         *      → 기획 확정 필요: 변경된 질문도 "제공됨"으로 카운트할지
         *
         * 8. 응답 반환
         *    - dailyQuestionId, content, description, questionCycle, changeCount(+1)
         */
        return null;
    }

    @Override
    @PostMapping("/{dailyQuestionId}/answer")
    public CreateAnswerResponse createAnswer(
            @PathVariable Long dailyQuestionId,
            @RequestBody CreateAnswerRequest request
    ) {
        /*
         * [답변 작성]
         *
         * 1. 현재 로그인한 사용자 정보 조회
         *
         * 2. DailyQuestion 조회 (dailyQuestionId)
         *    - 없으면 → 에러 (QUESTION_NOT_FOUND)
         *    - 다른 사용자의 질문이면 → 에러 (FORBIDDEN)
         *
         * 3. 기존 답변 존재 여부 확인
         *    - 이미 답변이 있으면 → 에러 (ANSWER_ALREADY_EXISTS)
         *    - ※ 답변 수정은 updateAnswer API 사용
         *
         * 4. 답변 유효성 검사
         *    - 빈 문자열 체크
         *    - 최대 길이 체크 (ex: 500자)
         *
         * 5. Answer 레코드 생성
         *    - daily_question_id = dailyQuestionId
         *    - content = request.answer()
         *    - answered_at = 현재 시간 (UTC)
         *
         * 6. 응답 반환
         *    - dailyAnswerId, content, answeredAt (LocalDateTime 변환)
         */
        return null;
    }

    @Override
    @PatchMapping("/{dailyQuestionId}/answer")
    public UpdateAnswerResponse updateAnswer(
            @PathVariable Long dailyQuestionId,
            @RequestBody UpdateAnswerRequest request
    ) {
        /*
         * [답변 수정]
         *
         * 1. 현재 로그인한 사용자 정보 조회
         *
         * 2. DailyQuestion 조회 (dailyQuestionId)
         *    - 없으면 → 에러 (QUESTION_NOT_FOUND)
         *    - 다른 사용자의 질문이면 → 에러 (FORBIDDEN)
         *
         * 3. 기존 Answer 조회
         *    - 없으면 → 에러 (ANSWER_NOT_FOUND)
         *    - ※ 답변이 없는데 수정 요청은 불가
         *
         * 4. 답변 유효성 검사
         *    - 빈 문자열 체크
         *    - 최대 길이 체크 (ex: 500자)
         *
         * 5. Answer 업데이트
         *    - content = request.answer()
         *    - updated_at = 현재 시간 (UTC)
         *    - ※ answered_at은 최초 답변 시간 유지
         *
         * 6. 응답 반환
         *    - dailyAnswerId, content, updatedAt (LocalDateTime 변환)
         */
        return null;
    }
}
