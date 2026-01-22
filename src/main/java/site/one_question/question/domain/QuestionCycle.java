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
import site.one_question.member.domain.Member;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(name = "question_cycle")
public class QuestionCycle extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(name = "cycle_number", nullable = false) // memberId + cycleNumber = unique
    private int cycleNumber;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;


    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "ended_at", nullable = false)
    private Instant endedAt;

    @Column(nullable = false, length = 30)
    private String timezone;

    public static QuestionCycle createFirstCycle(Member member, String timezone) {
        Instant now = Instant.now();
        ZoneId zoneId = ZoneId.of(timezone);
        ZonedDateTime startZdt = now.atZone(zoneId);

        LocalDate startDate = startZdt.toLocalDate();
        LocalDate endDate = startDate.plusYears(1).minusDays(1);
        Instant endAt = endDate.atTime(startZdt.toLocalTime()).atZone(zoneId).toInstant();

        return new QuestionCycle(
                null,
                member,
                1,
                startDate,
                endDate,
                now,
                endAt,
                timezone
        );
    }

    public static QuestionCycle createNextCycle(QuestionCycle previousCycle) {
        ZoneId zoneId = ZoneId.of(previousCycle.getTimezone());
        ZonedDateTime prevEndZdt = previousCycle.getEndedAt().atZone(zoneId);

        // 다음 사이클 시작: 이전 사이클 종료일 + 1일, 시/분/초 유지
        LocalDate startDate = prevEndZdt.toLocalDate().plusDays(1);
        Instant startAt = startDate.atTime(prevEndZdt.toLocalTime()).atZone(zoneId).toInstant();

        // 종료: 시작일 + 1년 - 1일, 시/분/초 유지
        LocalDate endDate = startDate.plusYears(1).minusDays(1);
        Instant endAt = endDate.atTime(prevEndZdt.toLocalTime()).atZone(zoneId).toInstant();

        return new QuestionCycle(
                null,
                previousCycle.getMember(),
                previousCycle.getCycleNumber() + 1,
                startDate,
                endDate,
                startAt,
                endAt,
                previousCycle.getTimezone()
        );
    }

    public boolean containsDate(LocalDate date) {
        return !date.isBefore(startDate) && !date.isAfter(endDate);
    }

    public boolean isExpired() {
        return Instant.now().isAfter(endedAt);
    }
}
