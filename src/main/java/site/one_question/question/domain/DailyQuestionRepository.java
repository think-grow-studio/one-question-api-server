package site.one_question.question.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DailyQuestionRepository extends JpaRepository<DailyQuestion, Long> {

    Optional<DailyQuestion> findByMemberIdAndDate(Long memberId, LocalDate date);

    @Query("SELECT dq.question.id FROM DailyQuestion dq WHERE dq.questionCycle.id = :questionCycleId")
    List<Long> findQuestionIdsByCycleId(@Param("questionCycleId") Long questionCycleId);
}
