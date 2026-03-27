package site.one_question.api.answerpost.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import site.one_question.api.member.domain.Member;

public interface AnswerPostLikeRepository extends JpaRepository<AnswerPostLike, Long> {

    Optional<AnswerPostLike> findByAnswerPostAndMember(AnswerPost answerPost, Member member);

    boolean existsByAnswerPostAndMember(AnswerPost answerPost, Member member);

    long countByAnswerPost(AnswerPost answerPost);

    @Modifying
    @Query("DELETE FROM AnswerPostLike apl WHERE apl.member.id = :memberId")
    int deleteByMemberId(@Param("memberId") Long memberId);
}
