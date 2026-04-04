package site.one_question.api.question.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import site.one_question.api.member.domain.Member;

public interface QuestionLikeRepository extends JpaRepository<QuestionLike, Long> {

    Optional<QuestionLike> findByQuestionAndMember(Question question, Member member);

    boolean existsByQuestionIdAndMemberId(Long questionId, Long memberId);

    @Query("SELECT ql.question.id FROM QuestionLike ql WHERE ql.question.id IN :questionIds AND ql.member.id = :memberId")
    List<Long> findLikedQuestionIdsByMember(@Param("questionIds") List<Long> questionIds, @Param("memberId") Long memberId);

    @Modifying
    @Query("DELETE FROM QuestionLike ql WHERE ql.member.id = :memberId")
    int deleteByMemberId(@Param("memberId") Long memberId);
}
