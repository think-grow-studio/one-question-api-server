package site.one_question.test_config.utils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Component;
import org.springframework.test.util.ReflectionTestUtils;
import site.one_question.member.domain.Member;
import site.one_question.question.domain.QuestionCycle;
import site.one_question.question.domain.QuestionCycleRepository;

@Component
@RequiredArgsConstructor
public class TestQuestionCycleUtils {

    private final QuestionCycleRepository questionCycleRepository;

    public QuestionCycle createSave(Member member) {
        QuestionCycle cycle = QuestionCycle.createFirstCycle(member, "Asia/Seoul");
        return questionCycleRepository.save(cycle);
    }

    public QuestionCycle createSave_With_Timezone(Member member, String timezone) {
        QuestionCycle cycle = QuestionCycle.createFirstCycle(member, timezone);
        return questionCycleRepository.save(cycle);
    }

    public QuestionCycle createSave_With_StartDate(Member member, LocalDate startDate, String timezone) {
        return createSave_With_StartDate(member, startDate, timezone, 1);
    }

    public QuestionCycle createSave_With_StartDate(Member member, LocalDate startDate, String timezone, int cycleNumber) {
        ZoneId zoneId = ZoneId.of(timezone);
        LocalDate endDate = startDate.plusYears(1).minusDays(1);
        LocalTime time = LocalTime.of(12, 0, 0);
        Instant startedAt = startDate.atTime(time).atZone(zoneId).toInstant();
        Instant endedAt = endDate.atTime(time).atZone(zoneId).toInstant();

        QuestionCycle cycle = BeanUtils.instantiateClass(QuestionCycle.class);
        ReflectionTestUtils.setField(cycle, "member", member);
        ReflectionTestUtils.setField(cycle, "cycleNumber", cycleNumber);
        ReflectionTestUtils.setField(cycle, "startDate", startDate);
        ReflectionTestUtils.setField(cycle, "endDate", endDate);
        ReflectionTestUtils.setField(cycle, "startedAt", startedAt);
        ReflectionTestUtils.setField(cycle, "endedAt", endedAt);
        ReflectionTestUtils.setField(cycle, "timezone", timezone);

        return questionCycleRepository.save(cycle);
    }
}
