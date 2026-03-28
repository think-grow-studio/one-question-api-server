package site.one_question.api.answerpost.domain;

import java.util.List;
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

    @Query("SELECT apl.answerPost.id, COUNT(apl) FROM AnswerPostLike apl " +
           "WHERE apl.answerPost.id IN :postIds GROUP BY apl.answerPost.id")
    List<Object[]> countByAnswerPostIds(@Param("postIds") List<Long> postIds);

    @Query("SELECT apl.answerPost.id FROM AnswerPostLike apl " +
           "WHERE apl.answerPost.id IN :postIds AND apl.member.id = :memberId")
    List<Long> findLikedPostIdsByMember(@Param("postIds") List<Long> postIds, @Param("memberId") Long memberId);

    @Modifying
    @Query("DELETE FROM AnswerPostLike apl WHERE apl.member.id = :memberId")
    int deleteByMemberId(@Param("memberId") Long memberId);
}
