package site.one_question.api.notification.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import site.one_question.api.member.domain.Member;

public interface FcmTokenRepository extends JpaRepository<FcmToken, Long> {

    Optional<FcmToken> findByMember(Member member);

    Optional<FcmToken> findByMemberAndToken(Member member, String token);

    @Modifying
    @Query("DELETE FROM FcmToken f WHERE f.member = :member")
    void deleteByMember(@Param("member") Member member);

    @Query("SELECT f FROM FcmToken f JOIN FETCH f.member m " +
           "JOIN QuestionReminderSetting s ON s.member = m " +
           "WHERE s.isEnabled = true AND s.alarmTime = :alarmTime " +
           "AND s.timezone = :timezone AND m.status = site.one_question.api.member.domain.MemberStatus.ACTIVE")
    List<FcmToken> findTokensForNotification(@Param("alarmTime") String alarmTime,
                                             @Param("timezone") String timezone);

    @Modifying
    @Query("DELETE FROM FcmToken f WHERE f.member.id = :memberId")
    void deleteByMemberId(@Param("memberId") Long memberId);
}
