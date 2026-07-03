package site.one_question.api.question.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DailyQuestionAnswerRepository extends JpaRepository<DailyQuestionAnswer, Long> {

    boolean existsByDailyQuestionId(DailyQuestion dailyQuestion);

    Optional<DailyQuestionAnswer> findByDailyQuestionId(DailyQuestion dailyQuestion);

    @Query("""
            SELECT answer FROM DailyQuestionAnswer answer
            JOIN FETCH answer.dailyQuestion dailyQuestion
            JOIN FETCH dailyQuestion.question
            WHERE answer.member.id = :memberId
              AND answer.id IN :answerIds
            """)
    List<DailyQuestionAnswer> findAllOwnedByMemberWithQuestion(
            @Param("memberId") Long memberId,
            @Param("answerIds") Collection<Long> answerIds
    );

    @Modifying
    @Query("DELETE FROM DailyQuestionAnswer dqa WHERE dqa.member.id = :memberId")
    int deleteByMemberId(@Param("memberId") Long memberId);
}
