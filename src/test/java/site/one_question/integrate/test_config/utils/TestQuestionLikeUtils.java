package site.one_question.integrate.test_config.utils;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import site.one_question.api.member.domain.Member;
import site.one_question.api.question.domain.Question;
import site.one_question.api.question.domain.QuestionLike;
import site.one_question.api.question.domain.QuestionLikeRepository;

@Component
@RequiredArgsConstructor
public class TestQuestionLikeUtils {

    private final QuestionLikeRepository repository;

    public QuestionLike createSave(Question question, Member member) {
        QuestionLike like = QuestionLike.create(question, member);
        return repository.save(like);
    }
}
