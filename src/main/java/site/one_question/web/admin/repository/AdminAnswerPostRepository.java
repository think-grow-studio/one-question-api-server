package site.one_question.web.admin.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import site.one_question.api.answerpost.domain.AnswerPost;
import site.one_question.api.question.domain.DailyQuestionAnswer;

public interface AdminAnswerPostRepository extends JpaRepository<AnswerPost, Long> {

    Optional<AnswerPost> findByQuestionAnswer(DailyQuestionAnswer questionAnswer);
}
