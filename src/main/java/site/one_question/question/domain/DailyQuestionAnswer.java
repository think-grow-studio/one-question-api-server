package site.one_question.question.domain;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(name = "daily_question_answer")
public class DailyQuestionAnswer {

    private static final int MAX_CONTENT_LENGTH = 5000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "daily_question_id", nullable = false)
    private Long dailyQuestionId;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "answered_at", nullable = false)
    private Instant answeredAt;

    @Column(nullable = false, length = 30)
    private String timezone;

    public static DailyQuestionAnswer create(
            Long dailyQuestionId,
            Long memberId,
            String content,
            String timezone
    ) {
        validateContent(content);
        return new DailyQuestionAnswer(
                null,
                dailyQuestionId,
                memberId,
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
        return this.memberId.equals(memberId);
    }
}
