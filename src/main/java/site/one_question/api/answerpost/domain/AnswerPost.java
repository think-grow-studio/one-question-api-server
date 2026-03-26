package site.one_question.api.answerpost.domain;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import site.one_question.api.member.domain.Member;
import site.one_question.api.question.domain.DailyQuestionAnswer;
import site.one_question.global.common.domain.BaseEntity;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(name = "answer_post", indexes = {
        @Index(name = "idx_answer_post_status_posted", columnList = "status, posted_at DESC")
})
public class AnswerPost extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_answer_id", nullable = false, unique = true)
    private DailyQuestionAnswer questionAnswer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(name = "anonymous_nickname", length = 100,nullable = false)
    private String anonymousNickname;

    @Column(name = "posted_at",nullable = false)
    private Instant postedAt;

    @Enumerated(EnumType.STRING)
    @Column(length = 30,nullable = false)
    private AnswerPostStatus status;

    @Column(name = "hidden_at")
    private Instant hiddenAt;

    public static AnswerPost create(
            DailyQuestionAnswer questionAnswer,
            Member member,
            String anonymousNickname
    ) {
        return new AnswerPost(
                null,
                questionAnswer,
                member,
                anonymousNickname,
                Instant.now(),
                AnswerPostStatus.PUBLISHED,
                null
        );
    }

    public void hide() {
        this.status = AnswerPostStatus.HIDDEN;
        this.hiddenAt = Instant.now();
    }

    public void unpublish() {
        this.status = AnswerPostStatus.UNPUBLISHED;
    }

    public void republish() {
        this.status = AnswerPostStatus.PUBLISHED;
    }

    public boolean isOwnedBy(Long memberId) {
        return this.member.getId().equals(memberId);
    }

    public boolean isPublished() {
        return this.status == AnswerPostStatus.PUBLISHED;
    }
}
