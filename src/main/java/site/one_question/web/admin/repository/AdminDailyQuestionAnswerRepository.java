package site.one_question.web.admin.repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import site.one_question.api.question.domain.DailyQuestionAnswer;
import site.one_question.web.admin.dto.AnswerDateRow;
import site.one_question.web.admin.dto.LeaderboardRow;
import site.one_question.web.admin.dto.WauRow;

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

    @Query("SELECT COUNT(dqa) FROM DailyQuestionAnswer dqa JOIN dqa.member m WHERE m.id != 1")
    long countAllAnswers();

    @Query("""
            SELECT COUNT(dqa) FROM DailyQuestionAnswer dqa JOIN dqa.member m
            WHERE m.id != 1 AND dqa.answeredAt >= :from AND dqa.answeredAt < :to
            """)
    long countAnswersBetween(@Param("from") Instant from, @Param("to") Instant to);

    @Query("""
            SELECT COUNT(dqa) FROM DailyQuestionAnswer dqa JOIN dqa.member m
            WHERE m.id != 1 AND dqa.answeredAt >= :from AND dqa.answeredAt < :to AND m.joinedDate = :date
            """)
    long countNewAnswersBetween(@Param("from") Instant from, @Param("to") Instant to, @Param("date") LocalDate date);

    @Query("""
            SELECT new site.one_question.web.admin.dto.AnswerDateRow(dqa.answeredAt, m.joinedDate)
            FROM DailyQuestionAnswer dqa JOIN dqa.member m
            WHERE m.id != 1 AND dqa.answeredAt >= :from
            """)
    List<AnswerDateRow> findAnswerDatesAndJoinedDatesSince(@Param("from") Instant from);

    @Query("""
            SELECT new site.one_question.web.admin.dto.LeaderboardRow(
                m.id, m.fullName, m.joinedDate, COUNT(dqa), MAX(dqa.answeredAt)
            )
            FROM DailyQuestionAnswer dqa JOIN dqa.member m
            WHERE m.id != 1
            GROUP BY m.id, m.fullName, m.joinedDate
            ORDER BY COUNT(dqa) DESC
            """)
    List<LeaderboardRow> findLeaderboardData();

    @Query("""
            SELECT new site.one_question.web.admin.dto.WauRow(m.id, m.fullName, COUNT(dqa))
            FROM DailyQuestionAnswer dqa JOIN dqa.member m
            WHERE m.id != 1 AND dqa.answeredAt >= :from AND dqa.answeredAt < :to
            GROUP BY m.id, m.fullName
            HAVING COUNT(dqa) >= 3
            ORDER BY m.id
            """)
    List<WauRow> findWauData(@Param("from") Instant from, @Param("to") Instant to);

}
