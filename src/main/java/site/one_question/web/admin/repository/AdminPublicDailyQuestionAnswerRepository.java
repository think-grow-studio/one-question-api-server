package site.one_question.web.admin.repository;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import site.one_question.api.publicquestion.domain.PublicDailyQuestionAnswer;
import site.one_question.web.admin.dto.PublicQuestionDailyCountRow;

public interface AdminPublicDailyQuestionAnswerRepository
        extends JpaRepository<PublicDailyQuestionAnswer, Long> {

    @Query("""
            SELECT new site.one_question.web.admin.dto.PublicQuestionDailyCountRow(
                pdq.questionDate, COUNT(a)
            )
            FROM PublicDailyQuestionAnswer a
            JOIN a.publicDailyQuestion pdq
            WHERE pdq.questionDate >= :from AND pdq.questionDate <= :to
            GROUP BY pdq.questionDate
            ORDER BY pdq.questionDate DESC
            """)
    List<PublicQuestionDailyCountRow> findDailyCounts(
            @Param("from") LocalDate from,
            @Param("to") LocalDate to
    );

    @Query("""
            SELECT a FROM PublicDailyQuestionAnswer a
            JOIN FETCH a.publicDailyQuestion pdq
            JOIN FETCH pdq.question
            JOIN FETCH a.member
            WHERE pdq.questionDate = :date
            ORDER BY a.answeredAt DESC, a.id DESC
            """)
    List<PublicDailyQuestionAnswer> findAnswersByQuestionDate(
            @Param("date") LocalDate date,
            Pageable pageable
    );
}
