package site.one_question.api.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import site.one_question.api.member.domain.Member;
import site.one_question.global.common.domain.BaseEntity;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(name = "question_reminder_setting")
public class QuestionReminderSetting extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false, unique = true)
    private Member member;

    @Column(name = "alarm_time", nullable = false, length = 5)
    private String alarmTime;

    @Column(name = "timezone", nullable = false, length = 50)
    private String timezone;

    @Column(name = "is_enabled", nullable = false)
    private boolean isEnabled;

    public static QuestionReminderSetting create(Member member, String alarmTime, String timezone, boolean isEnabled) {
        return new QuestionReminderSetting(null, member, alarmTime, timezone, isEnabled);
    }

    public void update(String alarmTime, String timezone, boolean isEnabled) {
        this.alarmTime = alarmTime;
        this.timezone = timezone;
        this.isEnabled = isEnabled;
    }
}
