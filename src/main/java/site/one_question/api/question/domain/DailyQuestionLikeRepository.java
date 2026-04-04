package site.one_question.api.question.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import site.one_question.api.member.domain.Member;

public interface DailyQuestionLikeRepository extends JpaRepository<DailyQuestionLike, Long> {

    Optional<DailyQuestionLike> findByDailyQuestionAndMember(DailyQuestion dailyQuestion, Member member);

    @Modifying
    @Query("DELETE FROM DailyQuestionLike dql WHERE dql.member.id = :memberId")
    int deleteByMemberId(@Param("memberId") Long memberId);
}
