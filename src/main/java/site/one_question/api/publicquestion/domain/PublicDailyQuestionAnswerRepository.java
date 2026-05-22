package site.one_question.api.publicquestion.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import site.one_question.api.member.domain.Member;

public interface PublicDailyQuestionAnswerRepository extends JpaRepository<PublicDailyQuestionAnswer, Long> {

    boolean existsByPublicDailyQuestionAndMember(PublicDailyQuestion publicDailyQuestion, Member member);
}
