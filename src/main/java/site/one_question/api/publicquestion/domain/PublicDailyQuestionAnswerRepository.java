package site.one_question.api.publicquestion.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
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

    @Query("""
            SELECT a FROM PublicDailyQuestionAnswer a
            WHERE a.publicDailyQuestion.id = :pdqId
              AND a.member.id <> :excludeMemberId
              AND a.status = :status
              AND (a.answeredAt < :cursorAnsweredAt
                   OR (a.answeredAt = :cursorAnsweredAt AND a.id < :cursorId))
            ORDER BY a.answeredAt DESC, a.id DESC
            """)
    List<PublicDailyQuestionAnswer> findFeed(
            @Param("pdqId") Long pdqId,
            @Param("excludeMemberId") Long excludeMemberId,
            @Param("status") PublicDailyQuestionAnswerStatus status,
            @Param("cursorAnsweredAt") Instant cursorAnsweredAt,
            @Param("cursorId") Long cursorId,
            Pageable pageable
    );
}
