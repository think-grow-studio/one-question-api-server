package site.one_question.api.publicquestion.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import site.one_question.api.member.domain.Member;

public interface PublicDailyQuestionAnswerLikeRepository extends JpaRepository<PublicDailyQuestionAnswerLike, Long> {

    Optional<PublicDailyQuestionAnswerLike> findByPublicDailyQuestionAnswerAndMember(
            PublicDailyQuestionAnswer publicDailyQuestionAnswer, Member member);

    boolean existsByPublicDailyQuestionAnswerAndMember(
            PublicDailyQuestionAnswer publicDailyQuestionAnswer, Member member);

    long countByPublicDailyQuestionAnswer(PublicDailyQuestionAnswer publicDailyQuestionAnswer);

    @Modifying
    int deleteByPublicDailyQuestionAnswer(PublicDailyQuestionAnswer publicDailyQuestionAnswer);

    @Modifying
    @Query("DELETE FROM PublicDailyQuestionAnswerLike l WHERE l.member.id = :memberId")
    int deleteByMemberId(@Param("memberId") Long memberId);

    @Modifying
    @Query("DELETE FROM PublicDailyQuestionAnswerLike l " +
           "WHERE l.publicDailyQuestionAnswer.member.id = :memberId")
    int deleteByAnswerOwnerId(@Param("memberId") Long memberId);
}
