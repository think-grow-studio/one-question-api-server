package site.one_question.question.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuestionCycleRepository extends JpaRepository<QuestionCycle, Long> {

    List<QuestionCycle> findByMemberIdOrderByCycleNumberDesc(Long memberId);

    @Modifying
    @Query("DELETE FROM QuestionCycle qc WHERE qc.member.id = :memberId")
    int deleteByMemberId(@Param("memberId") Long memberId);
}
