package site.one_question.api.publicquestion.domain;

import java.util.Optional;
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
