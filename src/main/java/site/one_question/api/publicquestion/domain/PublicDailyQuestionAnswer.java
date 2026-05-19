package site.one_question.api.publicquestion.domain;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import site.one_question.api.answerpost.domain.AnonymousNickname;
import site.one_question.api.member.domain.Member;
import site.one_question.api.publicquestion.domain.exception.AnswerContentTooLongException;
import site.one_question.api.publicquestion.domain.exception.EmptyAnswerContentException;
import site.one_question.common.domain.BaseEntity;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(
        name = "public_daily_question_answer",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_pdqa_question_member",
                columnNames = {"public_daily_question_id", "member_id"}
        ),
        indexes = {
                @Index(name = "idx_pdqa_question_answered", columnList = "public_daily_question_id, answered_at DESC")
        }
)
public class PublicDailyQuestionAnswer extends BaseEntity {

    private static final int MAX_CONTENT_LENGTH = 3000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "public_daily_question_id", nullable = false)
    private PublicDailyQuestion publicDailyQuestion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Lob
    @Column(nullable = false)
    private String content;

    @Column(name = "anonymous_nickname", length = 100, nullable = false)
    private String anonymousNickname;

    @Column(name = "answered_at", nullable = false)
    private Instant answeredAt;

    @Column(nullable = false, length = 30)
    private String timezone;

    public static PublicDailyQuestionAnswer create(
            PublicDailyQuestion question,
            Member member,
            String content,
            String timezone
    ) {
        validateContent(content);
        String nickname = AnonymousNickname.generate(member.getLocale()).getValue();
        return new PublicDailyQuestionAnswer(
                null,
                question,
                member,
                content,
                nickname,
                Instant.now(),
                timezone
        );
    }

    public void updateContent(String newContent) {
        validateContent(newContent);
        this.content = newContent;
    }

    public boolean isOwnedBy(Long memberId) {
        return this.member.getId().equals(memberId);
    }

    private static void validateContent(String content) {
        if (content == null || content.isBlank()) {
            throw new EmptyAnswerContentException();
        }
        if (content.length() > MAX_CONTENT_LENGTH) {
            throw new AnswerContentTooLongException(MAX_CONTENT_LENGTH);
        }
    }
}
