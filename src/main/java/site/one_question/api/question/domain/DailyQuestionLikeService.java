package site.one_question.api.question.domain;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import site.one_question.api.member.domain.Member;

@Service
@RequiredArgsConstructor
public class DailyQuestionLikeService {

    private final DailyQuestionLikeRepository dailyQuestionLikeRepository;

    public DailyQuestionLike save(DailyQuestionLike like) {
        return dailyQuestionLikeRepository.save(like);
    }

    public Optional<DailyQuestionLike> findByDailyQuestionAndMember(DailyQuestion dailyQuestion, Member member) {
        return dailyQuestionLikeRepository.findByDailyQuestionAndMember(dailyQuestion, member);
    }

    public void delete(DailyQuestionLike like) {
        dailyQuestionLikeRepository.delete(like);
    }

    public void deleteByMemberId(Long memberId) {
        dailyQuestionLikeRepository.deleteByMemberId(memberId);
    }
}
