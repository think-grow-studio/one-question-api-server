package site.one_question.api.publicquestion.domain;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import site.one_question.api.member.domain.Member;

@Service
@RequiredArgsConstructor
public class PublicDailyQuestionAnswerLikeService {

    private final PublicDailyQuestionAnswerLikeRepository publicDailyQuestionAnswerLikeRepository;

    public PublicDailyQuestionAnswerLike save(PublicDailyQuestionAnswerLike like) {
        return publicDailyQuestionAnswerLikeRepository.save(like);
    }

    public Optional<PublicDailyQuestionAnswerLike> findByAnswerAndMember(
            PublicDailyQuestionAnswer answer, Member member) {
        return publicDailyQuestionAnswerLikeRepository.findByPublicDailyQuestionAnswerAndMember(answer, member);
    }

    public boolean isLikedBy(PublicDailyQuestionAnswer answer, Member member) {
        return publicDailyQuestionAnswerLikeRepository.existsByPublicDailyQuestionAnswerAndMember(answer, member);
    }

    public long countBy(PublicDailyQuestionAnswer answer) {
        return publicDailyQuestionAnswerLikeRepository.countByPublicDailyQuestionAnswer(answer);
    }

    public void delete(PublicDailyQuestionAnswerLike like) {
        publicDailyQuestionAnswerLikeRepository.delete(like);
    }

    public void deleteByAnswer(PublicDailyQuestionAnswer answer) {
        publicDailyQuestionAnswerLikeRepository.deleteByPublicDailyQuestionAnswer(answer);
    }
}
