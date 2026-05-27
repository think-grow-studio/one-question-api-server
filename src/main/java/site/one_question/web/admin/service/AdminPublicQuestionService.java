package site.one_question.web.admin.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.one_question.web.admin.dto.PublicQuestionDailyCountRow;
import site.one_question.web.admin.repository.AdminPublicDailyQuestionAnswerRepository;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminPublicQuestionService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final int RECENT_DAYS = 14;
    private static final int ANSWER_LIMIT = 30;

    private final AdminPublicDailyQuestionAnswerRepository repository;

    public PublicQuestionPageData getPageData(LocalDate selectedDate) {
        LocalDate today = LocalDate.now(KST);
        LocalDate from = today.minusDays(RECENT_DAYS - 1L);

        List<PublicQuestionDailyCountRow> dailyCounts = buildDailyCounts(from, today);
        long maxDailyCount = dailyCounts.stream().mapToLong(PublicQuestionDailyCountRow::count).max().orElse(1L);

        LocalDate effectiveDate = (selectedDate != null) ? selectedDate : today;
        String questionContent = null;
        List<PublicAnswerRow> answers = List.of();

        var answerEntities = repository.findAnswersByQuestionDate(
                effectiveDate, PageRequest.of(0, ANSWER_LIMIT));
        if (!answerEntities.isEmpty()) {
            questionContent = answerEntities.get(0)
                    .getPublicDailyQuestion()
                    .getQuestion()
                    .getContent();
        }
        answers = answerEntities.stream()
                .map(a -> new PublicAnswerRow(
                        a.getId(),
                        a.getMember().getId(),
                        a.getAnonymousNickname(),
                        a.getContent(),
                        LocalDateTime.ofInstant(a.getAnsweredAt(), KST)
                ))
                .toList();

        return new PublicQuestionPageData(
                effectiveDate,
                dailyCounts,
                maxDailyCount,
                questionContent,
                answers
        );
    }

    private List<PublicQuestionDailyCountRow> buildDailyCounts(LocalDate from, LocalDate to) {
        Map<LocalDate, Long> map = new HashMap<>();
        for (PublicQuestionDailyCountRow row : repository.findDailyCounts(from, to)) {
            map.put(row.date(), row.count());
        }
        return from.datesUntil(to.plusDays(1))
                .map(d -> new PublicQuestionDailyCountRow(d, map.getOrDefault(d, 0L)))
                .sorted(Comparator.comparing(PublicQuestionDailyCountRow::date).reversed())
                .toList();
    }

    public record PublicAnswerRow(
            Long answerId,
            Long memberId,
            String anonymousNickname,
            String content,
            LocalDateTime answeredAt
    ) {}

    public record PublicQuestionPageData(
            LocalDate selectedDate,
            List<PublicQuestionDailyCountRow> dailyCounts,
            long maxDailyCount,
            String questionContent,
            List<PublicAnswerRow> answers
    ) {}
}
