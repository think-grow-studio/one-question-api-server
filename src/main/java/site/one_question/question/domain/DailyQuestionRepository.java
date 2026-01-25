package site.one_question.question.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DailyQuestionRepository extends JpaRepository<DailyQuestion, Long> {

    @Query("""
        SELECT dq FROM DailyQuestion dq
        LEFT JOIN FETCH dq.answer
        WHERE dq.member.id = :memberId AND dq.date = :date
        """)
    Optional<DailyQuestion> findByMemberIdAndDate(
        @Param("memberId") Long memberId,
        @Param("date") LocalDate date
    );

    @Query("SELECT dq.question.id FROM DailyQuestion dq WHERE dq.questionCycle.id = :questionCycleId")
    List<Long> findQuestionIdsByCycleId(@Param("questionCycleId") Long questionCycleId);

    @Query("""
        SELECT dq FROM DailyQuestion dq
        LEFT JOIN FETCH dq.question q
        LEFT JOIN FETCH dq.questionCycle qc
        LEFT JOIN FETCH dq.answer a
        WHERE dq.member.id = :memberId
        AND dq.date BETWEEN :startDate AND :endDate
        ORDER BY dq.date DESC
        """)
    List<DailyQuestion> findByMemberIdAndDateBetween(
        @Param("memberId") Long memberId,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );
}
