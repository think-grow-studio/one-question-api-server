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
import site.one_question.question.domain.exception.ReloadLimitExceededException;

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

    @OneToOne(mappedBy = "dailyQuestionId", fetch = FetchType.LAZY)
    private DailyQuestionAnswer answer;

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
                0,
                null
        );
    }

    public void changeQuestion(Question newQuestion) {
        if (!canChangeQuestion()) {
            throw new ReloadLimitExceededException(
                member.getPermission().getMaxQuestionChangeCount());
        }
        this.question = newQuestion;
        this.changeCount++;
    }

    public boolean canChangeQuestion() {
        return changeCount < member.getPermission().getMaxQuestionChangeCount();
    }

    public boolean hasAnswer() {
        return this.answer != null;
    }
}
