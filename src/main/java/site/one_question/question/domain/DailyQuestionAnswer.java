package site.one_question.question.domain;

import jakarta.persistence.*;
import java.time.Instant;
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
@Table(name = "daily_question_answer")
public class DailyQuestionAnswer extends BaseEntity {

    private static final int MAX_CONTENT_LENGTH = 5000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "daily_question_id", nullable = false)
    private DailyQuestion dailyQuestionId;

    @ManyToOne
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Lob
    @Column(nullable = false)
    private String content;

    @Column(name = "answered_at", nullable = false)
    private Instant answeredAt;

    @Column(nullable = false, length = 30)
    private String timezone;

    public static DailyQuestionAnswer create(
            DailyQuestion dailyQuestion,
            Member member,
            String content,
            String timezone
    ) {
        validateContent(content);
        return new DailyQuestionAnswer(
                null,
                dailyQuestion,
                member,
                content,
                Instant.now(),
                timezone
        );
    }

    private static void validateContent(String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("답변 내용은 비어있을 수 없습니다.");
        }
        if (content.length() > MAX_CONTENT_LENGTH) {
            throw new IllegalArgumentException("답변 내용은 " + MAX_CONTENT_LENGTH + "자를 초과할 수 없습니다.");
        }
    }

    public void updateContent(String newContent) {
        validateContent(newContent);
        this.content = newContent;
    }

    public boolean isOwnedBy(Long memberId) {
        return this.member.getId().equals(memberId);
    }
}
