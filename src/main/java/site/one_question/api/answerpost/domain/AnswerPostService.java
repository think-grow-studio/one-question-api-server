package site.one_question.api.answerpost.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import site.one_question.api.answerpost.domain.exception.AnswerPostNotFoundException;
import site.one_question.api.member.domain.Member;
import site.one_question.api.question.domain.DailyQuestionAnswer;

@Service
@RequiredArgsConstructor
public class AnswerPostService {

    private final AnswerPostRepository answerPostRepository;

    public AnswerPost save(AnswerPost answerPost) {
        return answerPostRepository.save(answerPost);
    }

    public AnswerPost findByIdOrThrow(Long id) {
        return answerPostRepository.findById(id)
                .orElseThrow(() -> new AnswerPostNotFoundException(id));
    }

    public Optional<AnswerPost> findByQuestionAnswer(DailyQuestionAnswer questionAnswer) {
        return answerPostRepository.findByQuestionAnswer(questionAnswer);
    }

    private void publish(DailyQuestionAnswer questionAnswer, Member member) {
        AnonymousNickname nickname = AnonymousNickname.generate(member.getLocale());
        answerPostRepository.save(AnswerPost.createPublish(questionAnswer, member, nickname));
    }

    public void publishOrCreate(DailyQuestionAnswer questionAnswer, Member member) {
        var existing = findByQuestionAnswer(questionAnswer);
        if (existing.isPresent()) {
            AnswerPost post = existing.get();
            post.republish();
        } else {
            publish(questionAnswer, member);
        }
    }

    public List<AnswerPost> getFeed(Instant cursor, int size) {
        return answerPostRepository.findFeed(cursor, PageRequest.of(0, size));
    }

    public void deleteByMemberId(Long memberId) {
        answerPostRepository.deleteByMemberId(memberId);
    }
}
