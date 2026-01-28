package site.one_question.question.domain;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import site.one_question.member.domain.Member;
import site.one_question.question.domain.exception.BeforeSignupDateException;
import site.one_question.question.domain.exception.FirstCycleNotFoundException;
import site.one_question.question.domain.exception.FutureDateQuestionException;

@Service
@RequiredArgsConstructor
public class QuestionCycleService {

    private final QuestionCycleRepository cycleRepository;

    public QuestionCycle createFirstCycle(Member member, String timezone) {
        QuestionCycle firstCycle = QuestionCycle.createFirstCycle(member, timezone);
        return cycleRepository.save(firstCycle);
    }

    public QuestionCycle getFirstCycle(Long memberId) {
        return cycleRepository.findByMemberIdOrderByCycleNumberDesc(memberId).stream()
                .min(Comparator.comparing(QuestionCycle::getCycleNumber))
                .orElseThrow(FirstCycleNotFoundException::new);
    }

    public QuestionCycle getOrCreateCycle(Member member, LocalDate date, String timezone) {
        // 1. 미래 날짜 검증
        LocalDate today = LocalDate.now(ZoneId.of(timezone));
        if (date.isAfter(today)) {
            throw new FutureDateQuestionException();
        }

        List<QuestionCycle> cycles = cycleRepository.findByMemberIdOrderByCycleNumberDesc(member.getId());

        // 2. 해당 날짜를 포함하는 사이클 찾기
        return cycles.stream()
                .filter(cycle -> cycle.containsDate(date))
                .findFirst()
                .orElseGet(() -> handleNoCycleFound(cycles, member, date, timezone));
    }

    private QuestionCycle handleNoCycleFound(List<QuestionCycle> cycles, Member member, LocalDate date, String timezone) {
        // 사이클이 없으면 첫 사이클 생성
        if (cycles.isEmpty()) {
            QuestionCycle firstCycle = QuestionCycle.createFirstCycle(member, timezone);
            return cycleRepository.save(firstCycle);
        }

        // 첫 번째 사이클(cycleNumber=1) 찾기
        QuestionCycle firstCycle = cycles.stream()
                .min(Comparator.comparing(QuestionCycle::getCycleNumber))
                .orElseThrow();

        // 가입일(첫 사이클 시작일) 이전 날짜 검증
        if (date.isBefore(firstCycle.getStartDate())) {
            throw new BeforeSignupDateException();
        }

        // 최신 사이클 이후의 날짜면 새 사이클 생성 (요청 날짜 포함할 때까지 반복)
        QuestionCycle latestCycle = cycles.get(0); // DESC 정렬이므로 첫 번째가 최신
        QuestionCycle newCycle = QuestionCycle.createNextCycle(latestCycle);
        newCycle = cycleRepository.save(newCycle);

        while (!newCycle.containsDate(date)) {
            newCycle = QuestionCycle.createNextCycle(newCycle);
            newCycle = cycleRepository.save(newCycle);
        }

        return newCycle;
    }
}
