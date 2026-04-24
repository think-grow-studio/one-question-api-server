package site.one_question.api.notification.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import site.one_question.api.member.domain.Member;

public interface QuestionReminderSettingRepository extends JpaRepository<QuestionReminderSetting, Long> {

    Optional<QuestionReminderSetting> findByMember(Member member);

    @Query("SELECT s FROM QuestionReminderSetting s JOIN FETCH s.member m " +
           "WHERE s.isEnabled = true AND s.alarmTime = :alarmTime AND s.timezone = :timezone AND m.status = site.one_question.api.member.domain.MemberStatus.ACTIVE")
    List<QuestionReminderSetting> findActiveByAlarmTimeAndTimezone(
            @Param("alarmTime") String alarmTime,
            @Param("timezone") String timezone);

    @Query("SELECT DISTINCT s.timezone FROM QuestionReminderSetting s JOIN s.member m " +
           "WHERE s.isEnabled = true AND m.status = site.one_question.api.member.domain.MemberStatus.ACTIVE")
    List<String> findDistinctActiveTimezones();

    @Modifying
    @Query("DELETE FROM QuestionReminderSetting s WHERE s.member.id = :memberId")
    void deleteByMemberId(@Param("memberId") Long memberId);
}
