package site.one_question.api.question.domain;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import site.one_question.api.member.domain.Member;

@Service
@RequiredArgsConstructor
public class QuestionLikeService {

    private final QuestionLikeRepository questionLikeRepository;

    public QuestionLike save(QuestionLike like) {
        return questionLikeRepository.save(like);
    }

    public Optional<QuestionLike> findByQuestionAndMember(Question question, Member member) {
        return questionLikeRepository.findByQuestionAndMember(question, member);
    }

    public void delete(QuestionLike like) {
        questionLikeRepository.delete(like);
    }

    public void deleteByMemberId(Long memberId) {
        questionLikeRepository.deleteByMemberId(memberId);
    }
}
