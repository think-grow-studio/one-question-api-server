package site.one_question.web.admin.repository;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import site.one_question.api.member.domain.AuthSocialProvider;
import site.one_question.api.question.domain.DailyQuestion;

public interface AdminDailyQuestionRepository extends JpaRepository<DailyQuestion, Long> {

    @Query("""
        SELECT dq FROM DailyQuestion dq
        JOIN FETCH dq.member m
        JOIN FETCH dq.question q
        LEFT JOIN FETCH dq.answer a
        LEFT JOIN FETCH a.answerPost ap
        WHERE m.provider = :provider
        AND dq.questionDate BETWEEN :startDate AND :endDate
        ORDER BY dq.questionDate DESC
        """)
    List<DailyQuestion> findAllByProviderAndDateBetween(
        @Param("provider") AuthSocialProvider provider,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );
}
