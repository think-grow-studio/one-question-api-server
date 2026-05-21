package site.one_question.api.publicquestion.application;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import site.one_question.api.publicquestion.domain.PublicDailyQuestion;
import site.one_question.api.publicquestion.domain.PublicDailyQuestionRepository;
import site.one_question.api.publicquestion.domain.QuestionNumberUsage;
import site.one_question.api.question.domain.Question;
import site.one_question.api.question.domain.QuestionRepository;
import site.one_question.api.question.domain.QuestionStatus;

@Slf4j
@Component
@RequiredArgsConstructor
public class PublicDailyQuestionProvisionApplication {

    private static final String LOCALE_KO = Locale.KOREA.toLanguageTag();
    private static final int BUFFER_DAYS = 7;

    private final QuestionRepository questionRepository;
    private final PublicDailyQuestionRepository publicDailyQuestionRepository;

    @Transactional
    public void provision() {
        LocalDate todayUtc = LocalDate.now(ZoneOffset.UTC);
        int provisioned = 0;
        int skipped = 0;
        int failed = 0;

        for (int i = 0; i < BUFFER_DAYS; i++) {
            LocalDate targetDate = todayUtc.plusDays(i);
            try {
                if (publicDailyQuestionRepository.existsByQuestionDateAndLocale(targetDate, LOCALE_KO)) {
                    skipped++;
                    continue;
                }
                provisionOne(targetDate, LOCALE_KO);
                provisioned++;
            } catch (Exception e) {
                failed++;
                log.error("PDQ 생성 실패. date={} locale={}", targetDate, LOCALE_KO, e);
            }
        }

        log.info("PDQ 스케줄러 완료: provisioned={} skipped={} failed={}",
                provisioned, skipped, failed);
    }

    private void provisionOne(LocalDate targetDate, String locale) {
        List<QuestionNumberUsage> usages = publicDailyQuestionRepository
                .findQuestionNumberUsageCounts(locale, QuestionStatus.ACTIVE);

        if (usages.isEmpty()) {
            log.error("공개 질문 생성에 사용 가능한 질문 없음. locale={}", locale);
            return;
        }

        long minCount = usages.stream()
                .mapToLong(QuestionNumberUsage::count)
                .min()
                .orElseThrow();

        List<Integer> leastUsedNumbers = usages.stream()
                .filter(u -> u.count() == minCount)
                .map(QuestionNumberUsage::questionNumber)
                .toList();

        int chosenNumber = leastUsedNumbers.get(
                ThreadLocalRandom.current().nextInt(leastUsedNumbers.size())
        );

        Question question = questionRepository
                .findFirstByQuestionNumberAndLocaleAndStatus(chosenNumber, locale, QuestionStatus.ACTIVE)
                .orElseThrow(() -> new IllegalStateException(
                        "ACTIVE 질문을 찾을 수 없음: questionNumber=" + chosenNumber + " locale=" + locale));

        PublicDailyQuestion pdq = PublicDailyQuestion.publish(question, targetDate);
        publicDailyQuestionRepository.save(pdq);

        log.info("PDQ 생성: date={} locale={} questionNumber={} questionId={} usageMinCount={}",
                targetDate, locale, chosenNumber, question.getId(), minCount);
    }
}
