package site.one_question.api.publicquestion.domain;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import site.one_question.api.member.domain.Member;
import site.one_question.common.domain.BaseEntity;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(
        name = "public_daily_question_answer_like",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_pdqal_answer_member",
                columnNames = {"public_daily_question_answer_id", "member_id"}
        )
)
public class PublicDailyQuestionAnswerLike extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "public_daily_question_answer_id", nullable = false)
    private PublicDailyQuestionAnswer publicDailyQuestionAnswer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(name = "liked_at", nullable = false)
    private Instant likedAt;

    public static PublicDailyQuestionAnswerLike create(PublicDailyQuestionAnswer answer, Member member) {
        return new PublicDailyQuestionAnswerLike(null, answer, member, Instant.now());
    }
}
