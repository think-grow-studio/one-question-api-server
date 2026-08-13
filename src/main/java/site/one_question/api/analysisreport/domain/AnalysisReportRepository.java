package site.one_question.api.analysisreport.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AnalysisReportRepository extends JpaRepository<AnalysisReport, Long> {

    @Query("""
            SELECT ar FROM AnalysisReport ar
            JOIN FETCH ar.member member
            LEFT JOIN FETCH ar.sources
            WHERE ar.id = :id
              AND member.id = :memberId
            """)
    Optional<AnalysisReport> findWithSourcesByIdAndMemberId(
            @Param("id") Long id,
            @Param("memberId") Long memberId
    );

    @Query("""
            SELECT ar FROM AnalysisReport ar
            WHERE ar.member.id = :memberId
              AND (:cursor IS NULL OR ar.id < :cursor)
            ORDER BY ar.id DESC
            """)
    List<AnalysisReport> findAllByMemberIdBeforeCursor(
            @Param("memberId") Long memberId,
            @Param("cursor") Long cursor,
            Pageable pageable
    );
}
