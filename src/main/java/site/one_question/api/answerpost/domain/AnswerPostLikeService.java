package site.one_question.api.answerpost.domain;

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
public class AnswerPostLikeService {

    private final AnswerPostLikeRepository answerPostLikeRepository;

    public AnswerPostLike save(AnswerPostLike like) {
        return answerPostLikeRepository.save(like);
    }

    public Optional<AnswerPostLike> findByAnswerPostAndMember(AnswerPost answerPost, Member member) {
        return answerPostLikeRepository.findByAnswerPostAndMember(answerPost, member);
    }

    public Map<Long, Long> countByAnswerPostIds(List<Long> postIds) { // Map<postId,LikeCount>
        if (postIds.isEmpty()) {
            return Map.of();
        }
        return answerPostLikeRepository.countByAnswerPostIds(postIds).stream()
                .collect(Collectors.toMap(
                        row -> (Long) row[0],
                        row -> (Long) row[1]
                ));
    }

    public Set<Long> findLikedPostIdsByMember(List<Long> postIds, Long memberId) {
        if (postIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(answerPostLikeRepository.findLikedPostIdsByMember(postIds, memberId));
    }

    public void delete(AnswerPostLike like) {
        answerPostLikeRepository.delete(like);
    }

    public void deleteByMemberId(Long memberId) {
        answerPostLikeRepository.deleteByMemberId(memberId);
    }
}
