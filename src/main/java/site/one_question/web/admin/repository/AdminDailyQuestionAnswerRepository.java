package site.one_question.web.admin.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import site.one_question.api.question.domain.DailyQuestionAnswer;

public interface AdminDailyQuestionAnswerRepository extends JpaRepository<DailyQuestionAnswer, Long> {
}
