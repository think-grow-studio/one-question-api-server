package site.one_question.integrate.test_config.utils;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import site.one_question.api.answerpost.domain.AnswerPost;
import site.one_question.api.answerpost.domain.AnswerPostLike;
import site.one_question.api.answerpost.domain.AnswerPostLikeRepository;
import site.one_question.api.member.domain.Member;

@Component
@RequiredArgsConstructor
public class TestAnswerPostLikeUtils {

    private final AnswerPostLikeRepository repository;

    public AnswerPostLike createSave(AnswerPost answerPost, Member member) {
        AnswerPostLike like = AnswerPostLike.create(answerPost, member);
        return repository.save(like);
    }
}
