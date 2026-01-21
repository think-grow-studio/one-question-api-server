package site.one_question.question.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import site.one_question.global.common.domain.BaseEntity;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(name = "question_cycle")
public class QuestionCycle extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "cycle_number", nullable = false)
    private int cycleNumber;

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Column(name = "end_at", nullable = false)
    private Instant endAt;

    @Column(nullable = false, length = 30)
    private String timezone;

    public static QuestionCycle createFirstCycle(Long memberId, String timezone) {
        ZoneId zoneId = ZoneId.of(timezone);
        ZonedDateTime now = ZonedDateTime.now(zoneId);
        LocalDate today = now.toLocalDate();

        Instant startAt = today.atStartOfDay(zoneId).toInstant();
        Instant endAt = today.plusYears(1).minusDays(1).atStartOfDay(zoneId).toInstant();

        return new QuestionCycle(
                null,
                memberId,
                1,
                startAt,
                endAt,
                timezone
        );
    }

    public static QuestionCycle createNextCycle(QuestionCycle previousCycle) {
        ZoneId zoneId = ZoneId.of(previousCycle.getTimezone());

        LocalDate newStartDate = previousCycle.getEndAt()
                .atZone(zoneId)
                .toLocalDate()
                .plusDays(1);

        Instant startAt = newStartDate.atStartOfDay(zoneId).toInstant();
        Instant endAt = newStartDate.plusYears(1).minusDays(1).atStartOfDay(zoneId).toInstant();

        return new QuestionCycle(
                null,
                previousCycle.getMemberId(),
                previousCycle.getCycleNumber() + 1,
                startAt,
                endAt,
                previousCycle.getTimezone()
        );
    }

    public boolean containsDate(LocalDate date) {
        ZoneId zoneId = ZoneId.of(timezone);
        LocalDate startDate = startAt.atZone(zoneId).toLocalDate();
        LocalDate endDate = endAt.atZone(zoneId).toLocalDate();
        return !date.isBefore(startDate) && !date.isAfter(endDate);
    }

    public boolean isExpired() {
        return Instant.now().isAfter(endAt);
    }
}
