package site.one_question.api.publicquestion.domain;

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
public class PublicDailyQuestionAnswerLikeService {

    private final PublicDailyQuestionAnswerLikeRepository publicDailyQuestionAnswerLikeRepository;

    public boolean toggle(PublicDailyQuestionAnswer answer, Member member) {
        Optional<PublicDailyQuestionAnswerLike> existing =
                publicDailyQuestionAnswerLikeRepository.findByPublicDailyQuestionAnswerAndMember(answer, member);
        if (existing.isPresent()) {
            publicDailyQuestionAnswerLikeRepository.delete(existing.get());
            return false;
        }
        publicDailyQuestionAnswerLikeRepository.save(PublicDailyQuestionAnswerLike.create(answer, member));
        return true;
    }

    public boolean isLikedBy(PublicDailyQuestionAnswer answer, Member member) {
        return publicDailyQuestionAnswerLikeRepository.existsByPublicDailyQuestionAnswerAndMember(answer, member);
    }

    public long countBy(PublicDailyQuestionAnswer answer) {
        return publicDailyQuestionAnswerLikeRepository.countByPublicDailyQuestionAnswer(answer);
    }

    public Map<Long, Long> countByAnswerIds(List<Long> answerIds) {
        if (answerIds.isEmpty()) {
            return Map.of();
        }
        return publicDailyQuestionAnswerLikeRepository.countByAnswerIds(answerIds).stream()
                .collect(Collectors.toMap(AnswerLikeCount::answerId, AnswerLikeCount::count));
    }

    public Set<Long> findLikedAnswerIdsByMember(List<Long> answerIds, Long memberId) {
        if (answerIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(
                publicDailyQuestionAnswerLikeRepository.findLikedAnswerIdsByMember(answerIds, memberId));
    }

    public void deleteByAnswer(PublicDailyQuestionAnswer answer) {
        publicDailyQuestionAnswerLikeRepository.deleteByPublicDailyQuestionAnswer(answer);
    }

    public void deleteByMemberId(Long memberId) {
        publicDailyQuestionAnswerLikeRepository.deleteByMemberId(memberId);
    }

    public void deleteByAnswerOwnerId(Long memberId) {
        publicDailyQuestionAnswerLikeRepository.deleteByAnswerOwnerId(memberId);
    }
}
