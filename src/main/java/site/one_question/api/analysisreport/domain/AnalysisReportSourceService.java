package site.one_question.api.analysisreport.domain;

import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import site.one_question.api.analysisreport.domain.exception.AnalysisReportSourceAnswerNotOwnedException;
import site.one_question.api.question.domain.DailyQuestionAnswer;

@Service
@RequiredArgsConstructor
public class AnalysisReportSourceService {

    private final AnalysisReportSourceRepository analysisReportSourceRepository;

    public List<AnalysisReportSource> createAll(
            AnalysisReport analysisReport,
            Long memberId,
            List<DailyQuestionAnswer> answers
    ) {
        boolean allOwned = answers.stream().allMatch(answer -> answer.isOwnedBy(memberId));
        if (!allOwned) {
            throw new AnalysisReportSourceAnswerNotOwnedException(memberId);
        }

        List<DailyQuestionAnswer> orderedAnswers = answers.stream()
                .sorted(Comparator.comparing(
                        (DailyQuestionAnswer answer) -> answer.getDailyQuestion().getQuestionDate()).reversed())
                .toList();
        List<AnalysisReportSource> sources = IntStream.range(0, orderedAnswers.size())
                .mapToObj(index -> AnalysisReportSource.create(analysisReport, orderedAnswers.get(index), index + 1))
                .toList();
        return analysisReportSourceRepository.saveAll(sources);
    }
}
