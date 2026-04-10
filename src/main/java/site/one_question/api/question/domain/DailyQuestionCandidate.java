package site.one_question.api.question.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import site.one_question.global.common.domain.BaseEntity;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(
    name = "daily_question_candidate",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_dqc_daily_question_order",
            columnNames = {"daily_question_id", "received_order"}
        ),
        @UniqueConstraint(
            name = "uk_dqc_daily_question_question",
            columnNames = {"daily_question_id", "question_id"}
        )
    }
)
public class DailyQuestionCandidate extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "daily_question_id", nullable = false)
    private DailyQuestion dailyQuestion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    @Column(name = "received_order", nullable = false)
    private int receivedOrder;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    public static DailyQuestionCandidate create(
            DailyQuestion dailyQuestion,
            Question question,
            int receivedOrder
    ) {
        return new DailyQuestionCandidate(null, dailyQuestion, question, receivedOrder, Instant.now());
    }
}
