package site.one_question.test_config.utils;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.test.util.ReflectionTestUtils;
import site.one_question.api.answerpost.domain.AnonymousNickname;
import site.one_question.api.answerpost.domain.AnswerPost;
import site.one_question.api.answerpost.domain.AnswerPostRepository;
import site.one_question.api.member.domain.Member;
import site.one_question.api.question.domain.DailyQuestionAnswer;

@Component
@RequiredArgsConstructor
public class TestAnswerPostUtils {

    private final AnswerPostRepository repository;

    public AnswerPost createSave(DailyQuestionAnswer questionAnswer, Member member) {
        AnonymousNickname nickname = AnonymousNickname.generate(member.getLocale());
        return repository.save(AnswerPost.create(questionAnswer, member, nickname));
    }

    public AnswerPost createSave_Unpublished(DailyQuestionAnswer questionAnswer, Member member) {
        AnswerPost answerPost = createSave(questionAnswer, member);
        answerPost.unpublish();
        return repository.save(answerPost);
    }

    public AnswerPost createSave_With_PostedAt(DailyQuestionAnswer questionAnswer, Member member, Instant postedAt) {
        AnonymousNickname nickname = AnonymousNickname.generate(member.getLocale());
        AnswerPost answerPost = AnswerPost.create(questionAnswer, member, nickname);
        ReflectionTestUtils.setField(answerPost, "postedAt", postedAt);
        return repository.save(answerPost);
    }
}
