package site.one_question.api.notification.domain;

import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import site.one_question.api.member.domain.Member;
import site.one_question.api.notification.domain.exception.QuestionReminderSettingNotFoundException;

@Component
@RequiredArgsConstructor
public class QuestionReminderSettingService {

    private final QuestionReminderSettingRepository repository;

    public QuestionReminderSetting upsert(Member member, String alarmTime, String timezone, boolean enabled) {
        return repository.findByMember(member)
                .map(s -> {
                    s.update(alarmTime, timezone, enabled);
                    return s;
                })
                .orElseGet(() -> repository.save(QuestionReminderSetting.create(member, alarmTime, timezone, enabled)));
    }

    public QuestionReminderSetting findByMemberOrThrow(Member member) {
        return repository.findByMember(member)
                .orElseThrow(() -> new QuestionReminderSettingNotFoundException(member.getId()));
    }

    public Optional<QuestionReminderSetting> findByMember(Member member) {
        return repository.findByMember(member);
    }

    public List<QuestionReminderSetting> findActiveByAlarmTimeAndTimezone(String alarmTime, String timezone) {
        return repository.findActiveByAlarmTimeAndTimezone(alarmTime, timezone);
    }

    public List<String> findDistinctActiveTimezones() {
        return repository.findDistinctActiveTimezones();
    }

    public void deleteByMemberId(Long memberId) {
        repository.deleteByMemberId(memberId);
    }
}
