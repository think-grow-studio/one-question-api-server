package site.one_question.api.publicquestion.domain;

import jakarta.persistence.*;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import site.one_question.api.question.domain.Question;
import site.one_question.common.domain.BaseEntity;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(
        name = "public_daily_question",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_pdq_date_locale",
                columnNames = {"question_date", "locale"}
        )
)
public class PublicDailyQuestion extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    @Column(name = "question_date", nullable = false)
    private LocalDate questionDate;

    @Column(nullable = false, length = 20)
    private String locale;

    public static PublicDailyQuestion publish(Question question, LocalDate date) {
        return new PublicDailyQuestion(null, question, date, question.getLocale());
    }
}
