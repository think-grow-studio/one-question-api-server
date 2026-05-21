package site.one_question.api.publicquestion.domain;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import site.one_question.api.question.domain.QuestionStatus;

public interface PublicDailyQuestionRepository extends JpaRepository<PublicDailyQuestion, Long> {

    boolean existsByQuestionDateAndLocale(LocalDate questionDate, String locale);

    @Query("""
            SELECT new site.one_question.api.publicquestion.domain.QuestionNumberUsage(
                q.questionNumber, COUNT(pdq.id)
            )
            FROM Question q
            LEFT JOIN PublicDailyQuestion pdq ON pdq.question = q
            WHERE q.locale = :locale AND q.status = :status
            GROUP BY q.questionNumber
            """)
    List<QuestionNumberUsage> findQuestionNumberUsageCounts(
            @Param("locale") String locale,
            @Param("status") QuestionStatus status
    );
}
