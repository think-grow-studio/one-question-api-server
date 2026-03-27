package site.one_question.api.answerpost.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import site.one_question.api.question.domain.DailyQuestionAnswer;

public interface AnswerPostRepository extends JpaRepository<AnswerPost, Long> {

    Optional<AnswerPost> findByQuestionAnswer(DailyQuestionAnswer questionAnswer);

    boolean existsByQuestionAnswer(DailyQuestionAnswer questionAnswer);

    @Query("SELECT ap FROM AnswerPost ap " +
           "JOIN FETCH ap.questionAnswer qa " +
           "JOIN FETCH qa.dailyQuestionId dq " +
           "JOIN FETCH dq.question q " +
           "WHERE ap.status = 'PUBLISHED' AND ap.postedAt < :cursor " +
           "ORDER BY ap.postedAt DESC")
    List<AnswerPost> findFeed(@Param("cursor") Instant cursor, Pageable pageable);

    @Modifying
    @Query("DELETE FROM AnswerPost ap WHERE ap.member.id = :memberId")
    int deleteByMemberId(@Param("memberId") Long memberId);
}
