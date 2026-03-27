package site.one_question.api.answerpost.domain;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import site.one_question.api.member.domain.Member;

@Service
@RequiredArgsConstructor
public class AnswerPostLikeService {

    private final AnswerPostLikeRepository answerPostLikeRepository;

    public AnswerPostLike save(AnswerPostLike like) {
        return answerPostLikeRepository.save(like);
    }

    public Optional<AnswerPostLike> findByAnswerPostAndMember(AnswerPost answerPost, Member member) {
        return answerPostLikeRepository.findByAnswerPostAndMember(answerPost, member);
    }

    public boolean existsByAnswerPostAndMember(AnswerPost answerPost, Member member) {
        return answerPostLikeRepository.existsByAnswerPostAndMember(answerPost, member);
    }

    public long countByAnswerPost(AnswerPost answerPost) {
        return answerPostLikeRepository.countByAnswerPost(answerPost);
    }

    public void delete(AnswerPostLike like) {
        answerPostLikeRepository.delete(like);
    }

    public void deleteByMemberId(Long memberId) {
        answerPostLikeRepository.deleteByMemberId(memberId);
    }
}
