package site.one_question.question.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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
@Table(
        name = "daily_question",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_daily_question_member_date",
                columnNames = {"member_id", "date"}
        )
)
public class DailyQuestion extends BaseEntity {

    private static final int MAX_FREE_CHANGE_COUNT = 2;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_cycle_id", nullable = false)
    private QuestionCycle questionCycle;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    @Column(name = "date",nullable = false) // memberId + date -> unique
    private LocalDate date;

    @Column(name = "assigned_at", nullable = false)
    private Instant assignedAt;

    @Column(nullable = false, length = 30)
    private String timezone;

    @Column(name = "change_count", nullable = false)
    private int changeCount;

    public static DailyQuestion create(
            Member member,
            QuestionCycle questionCycle,
            Question question,
            LocalDate date,
            String timezone
    ) {
        return new DailyQuestion(
                null,
                member,
                questionCycle,
                question,
                date,
                Instant.now(),
                timezone,
                0
        );
    }

    public void changeQuestion(Question newQuestion, boolean isPremiumMember) {
        if (!isPremiumMember && changeCount >= MAX_FREE_CHANGE_COUNT) {
            throw new IllegalStateException("무료 회원은 질문 변경을 " + MAX_FREE_CHANGE_COUNT + "회까지만 할 수 있습니다.");
        }
        this.question = newQuestion;
        this.changeCount++;
    }

    public boolean canChangeQuestion(boolean isPremiumMember) {
        return isPremiumMember || changeCount < MAX_FREE_CHANGE_COUNT;
    }

    public int getRemainingChangeCount(boolean isPremiumMember) {
        if (isPremiumMember) {
            return Integer.MAX_VALUE;
        }
        return Math.max(0, MAX_FREE_CHANGE_COUNT - changeCount);
    }

    public LocalDate getTargetDate() {
        return assignedAt.atZone(ZoneId.of(timezone)).toLocalDate();
    }

    public boolean isOwnedBy(Long memberId) {
        return this.member.getId().equals(memberId);
    }
}
