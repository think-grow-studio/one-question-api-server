package site.one_question.api.publicquestion.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import site.one_question.api.member.domain.Member;

public interface PublicDailyQuestionAnswerLikeRepository extends JpaRepository<PublicDailyQuestionAnswerLike, Long> {

    Optional<PublicDailyQuestionAnswerLike> findByPublicDailyQuestionAnswerAndMember(
            PublicDailyQuestionAnswer publicDailyQuestionAnswer, Member member);
}
