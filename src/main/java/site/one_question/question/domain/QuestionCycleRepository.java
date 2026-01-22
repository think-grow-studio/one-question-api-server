package site.one_question.question.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuestionCycleRepository extends JpaRepository<QuestionCycle, Long> {

    List<QuestionCycle> findByMemberIdOrderByCycleNumberDesc(Long memberId);
}
