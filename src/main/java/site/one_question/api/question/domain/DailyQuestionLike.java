package site.one_question.api.question.domain;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import site.one_question.api.member.domain.Member;
import site.one_question.global.common.domain.BaseEntity;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(name = "daily_question_like", uniqueConstraints = {
        @UniqueConstraint(name = "uk_daily_question_like", columnNames = {"daily_question_id", "member_id"})
})
public class DailyQuestionLike extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "daily_question_id", nullable = false)
    private DailyQuestion dailyQuestion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(name = "liked_at", nullable = false)
    private Instant likedAt;

    public static DailyQuestionLike create(DailyQuestion dailyQuestion, Member member) {
        return new DailyQuestionLike(
                null,
                dailyQuestion,
                member,
                Instant.now()
        );
    }
}
