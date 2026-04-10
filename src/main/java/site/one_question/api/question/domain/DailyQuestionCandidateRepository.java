package site.one_question.api.question.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DailyQuestionCandidateRepository extends JpaRepository<DailyQuestionCandidate, Long> {

    @Query("""
        SELECT c FROM DailyQuestionCandidate c
        JOIN FETCH c.question
        WHERE c.dailyQuestion = :dailyQuestion
        ORDER BY c.receivedOrder ASC
        """)
    List<DailyQuestionCandidate> findAllByDailyQuestionOrderByReceivedOrderAsc(
        @Param("dailyQuestion") DailyQuestion dailyQuestion);

    @Query("""
        SELECT c FROM DailyQuestionCandidate c
        JOIN FETCH c.question
        WHERE c.dailyQuestion.id IN :ids
        ORDER BY c.dailyQuestion.id, c.receivedOrder ASC
        """)
    List<DailyQuestionCandidate> findAllByDailyQuestionIdInOrderByReceivedOrder(@Param("ids") List<Long> ids);

    Optional<DailyQuestionCandidate> findByDailyQuestionAndQuestionId(DailyQuestion dailyQuestion, Long questionId);

    @Query("""
        SELECT c.question.id FROM DailyQuestionCandidate c
        WHERE c.dailyQuestion.questionCycle.id = :cycleId
        """)
    List<Long> findQuestionIdsByCycleId(@Param("cycleId") Long cycleId);

    @Modifying
    @Query("DELETE FROM DailyQuestionCandidate c WHERE c.dailyQuestion = :dailyQuestion")
    void deleteByDailyQuestion(@Param("dailyQuestion") DailyQuestion dailyQuestion);

    @Modifying
    @Query("DELETE FROM DailyQuestionCandidate c WHERE c.dailyQuestion.member.id = :memberId")
    void deleteByMemberId(@Param("memberId") Long memberId);
}
