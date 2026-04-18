package site.one_question.web.admin.repository;

import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import site.one_question.api.question.domain.DailyQuestionAnswer;

public interface AdminDailyQuestionAnswerRepository extends JpaRepository<DailyQuestionAnswer, Long> {

    @Query("""
            SELECT dqa FROM DailyQuestionAnswer dqa
            JOIN FETCH dqa.member m
            JOIN FETCH dqa.dailyQuestionId dq
            JOIN FETCH dq.question q
            WHERE m.id != 1
            ORDER BY dqa.answeredAt DESC
            """)
    List<DailyQuestionAnswer> findRecentAnswers(Pageable pageable);

    @Query("""
            SELECT dqa FROM DailyQuestionAnswer dqa
            JOIN FETCH dqa.member m
            WHERE m.id != 1
            """)
    List<DailyQuestionAnswer> findAllWithMember();

    @Query("""
            SELECT dqa.answeredAt FROM DailyQuestionAnswer dqa
            WHERE dqa.member.id != 1
              AND dqa.answeredAt >= :from
            """)
    List<Instant> findAnsweredAtsFrom(@Param("from") Instant from);

    @Query("""
            SELECT dqa FROM DailyQuestionAnswer dqa
            JOIN FETCH dqa.member m
            WHERE m.id != 1
              AND dqa.answeredAt >= :from
              AND dqa.answeredAt < :to
            """)
    List<DailyQuestionAnswer> findAnswersInDateRange(
            @Param("from") Instant from,
            @Param("to") Instant to);

    @Query("SELECT COUNT(dqa) FROM DailyQuestionAnswer dqa WHERE dqa.member.id != 1")
    long countExcludingAdmin();

    @Query("""
            SELECT COUNT(dqa) FROM DailyQuestionAnswer dqa
            WHERE dqa.member.id != 1
              AND dqa.answeredAt >= :from
              AND dqa.answeredAt < :to
            """)
    long countInDateRange(@Param("from") Instant from, @Param("to") Instant to);
}
