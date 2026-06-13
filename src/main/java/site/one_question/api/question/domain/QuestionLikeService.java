package site.one_question.api.question.domain;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
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

    public boolean existsByQuestionIdAndMemberId(Long questionId, Long memberId) {
        return questionLikeRepository.existsByQuestionIdAndMemberId(questionId, memberId);
    }

    public Set<Long> findLikedQuestionIdsByMember(List<Long> questionIds, Long memberId) {
        if (questionIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(questionLikeRepository.findLikedQuestionIdsByMember(questionIds, memberId));
    }

    public Map<Long, Long> countLikesByQuestionIds(List<Long> questionIds) {
        if (questionIds.isEmpty()) {
            return Map.of();
        }
        return questionLikeRepository.countLikesByQuestionIds(questionIds)
            .stream()
            .collect(Collectors.toMap(QuestionLikeCount::getQuestionId, QuestionLikeCount::getLikeCount));
    }

    public void delete(QuestionLike like) {
        questionLikeRepository.delete(like);
    }

    public void deleteByMemberId(Long memberId) {
        questionLikeRepository.deleteByMemberId(memberId);
    }
}
