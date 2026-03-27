package site.one_question.api.answerpost.domain;

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
@Table(name = "answer_post_like", uniqueConstraints = {
        @UniqueConstraint(name = "uk_answer_post_like", columnNames = {"answer_post_id", "member_id"})
})
public class AnswerPostLike extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "answer_post_id", nullable = false)
    private AnswerPost answerPost;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(name = "liked_at", nullable = false)
    private Instant likedAt;

    public static AnswerPostLike create(AnswerPost answerPost, Member member) {
        return new AnswerPostLike(
                null,
                answerPost,
                member,
                Instant.now()
        );
    }
}
