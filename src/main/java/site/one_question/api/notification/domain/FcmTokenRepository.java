package site.one_question.api.notification.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import site.one_question.api.member.domain.Member;

public interface FcmTokenRepository extends JpaRepository<FcmToken, Long> {

    Optional<FcmToken> findByMember(Member member);

    Optional<FcmToken> findByMemberAndToken(Member member, String token);

    void deleteByMember(Member member);

    @Modifying
    @Query("DELETE FROM FcmToken f WHERE f.member.id = :memberId")
    void deleteByMemberId(@Param("memberId") Long memberId);
}
