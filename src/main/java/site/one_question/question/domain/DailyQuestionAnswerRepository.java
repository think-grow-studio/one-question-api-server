package site.one_question.question.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DailyQuestionAnswerRepository extends JpaRepository<DailyQuestionAnswer, Long> {

    boolean existsByDailyQuestionId(DailyQuestion dailyQuestion);

    Optional<DailyQuestionAnswer> findByDailyQuestionId(DailyQuestion dailyQuestion);
}
