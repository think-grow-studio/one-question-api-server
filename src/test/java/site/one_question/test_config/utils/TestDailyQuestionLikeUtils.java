package site.one_question.test_config.utils;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import site.one_question.api.member.domain.Member;
import site.one_question.api.question.domain.DailyQuestion;
import site.one_question.api.question.domain.DailyQuestionLike;
import site.one_question.api.question.domain.DailyQuestionLikeRepository;

@Component
@RequiredArgsConstructor
public class TestDailyQuestionLikeUtils {

    private final DailyQuestionLikeRepository repository;

    public DailyQuestionLike createSave(DailyQuestion dailyQuestion, Member member) {
        DailyQuestionLike like = DailyQuestionLike.create(dailyQuestion, member);
        return repository.save(like);
    }
}
