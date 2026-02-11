package site.one_question.question.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DailyQuestionAnswerRepository extends JpaRepository<DailyQuestionAnswer, Long> {

    boolean existsByDailyQuestionId(DailyQuestion dailyQuestion);

    Optional<DailyQuestionAnswer> findByDailyQuestionId(DailyQuestion dailyQuestion);

    @Modifying
    @Query("DELETE FROM DailyQuestionAnswer dqa WHERE dqa.member.id = :memberId")
    int deleteByMemberId(@Param("memberId") Long memberId);
}
