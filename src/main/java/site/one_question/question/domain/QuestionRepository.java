package site.one_question.question.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionRepository extends JpaRepository<Question, Long> {

    List<Question> findAllByStatus(QuestionStatus status);

    List<Question> findAllByStatusAndIdNotIn(QuestionStatus status, List<Long> excludedIds);

    List<Question> findAllByStatusAndIdNot(QuestionStatus status, Long excludeId);
}
