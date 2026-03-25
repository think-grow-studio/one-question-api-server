package site.one_question.admin.application;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.one_question.member.domain.AuthSocialProvider;
import site.one_question.question.domain.DailyQuestion;
import site.one_question.question.domain.DailyQuestionRepository;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminApplication {

    private final DailyQuestionRepository dailyQuestionRepository;

    public Map<LocalDate, List<AiPersonaRow>> getAiPersonaHistory(LocalDate startDate, LocalDate endDate) {
        List<DailyQuestion> dailyQuestions =
                dailyQuestionRepository.findAllByMemberProviderAndDateBetween(
                        AuthSocialProvider.AI_PERSONA, startDate, endDate);

        return dailyQuestions.stream()
                .map(dq -> new AiPersonaRow(
                        dq.getQuestionDate(),
                        dq.getMember().getFullName(),
                        dq.getQuestion().getContent(),
                        dq.getAnswer() != null ? dq.getAnswer().getContent() : null
                ))
                .sorted(Comparator.comparing(AiPersonaRow::fullName))
                .collect(Collectors.groupingBy(
                        AiPersonaRow::questionDate,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
    }

    public record AiPersonaRow(
            LocalDate questionDate,
            String fullName,
            String questionContent,
            String answerContent
    ) {}
}
