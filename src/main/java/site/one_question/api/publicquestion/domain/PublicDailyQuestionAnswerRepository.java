package site.one_question.api.publicquestion.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import site.one_question.api.member.domain.Member;

public interface PublicDailyQuestionAnswerRepository extends JpaRepository<PublicDailyQuestionAnswer, Long> {

    boolean existsByPublicDailyQuestionAndMember(PublicDailyQuestion publicDailyQuestion, Member member);

    Optional<PublicDailyQuestionAnswer> findByPublicDailyQuestionAndMember(PublicDailyQuestion publicDailyQuestion, Member member);

    @Modifying
    @Query("DELETE FROM PublicDailyQuestionAnswer a WHERE a.member.id = :memberId")
    int deleteByMemberId(@Param("memberId") Long memberId);
}
