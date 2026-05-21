package site.one_question.integrate.publicquestion;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import site.one_question.api.publicquestion.application.PublicDailyQuestionProvisionApplication;
import site.one_question.api.publicquestion.domain.PublicDailyQuestion;
import site.one_question.api.publicquestion.domain.PublicDailyQuestionRepository;
import site.one_question.api.question.domain.Question;
import site.one_question.api.question.domain.QuestionStatus;
import site.one_question.integrate.test_config.IntegrateTest;

@DisplayName("공개 일일 질문 스케줄러 통합 테스트")
class PublicDailyQuestionProvisionSchedulerIntegrateTest extends IntegrateTest {

    private static final String LOCALE_KO = Locale.KOREA.toLanguageTag();
    private static final int BUFFER_DAYS = 7;

    @Autowired
    private PublicDailyQuestionProvisionApplication provisionApplication;

    @Autowired
    private PublicDailyQuestionRepository publicDailyQuestionRepository;

    @Test
    @DisplayName("빈 DB 에서 실행 시 [today, today+6] 7일치 PDQ 를 생성한다")
    void provisionsSevenDays_whenDbEmpty() {
        // given - ko-KR ACTIVE 질문 1개
        testQuestionUtils.createSave();

        // when
        provisionApplication.provision();

        // then
        LocalDate todayUtc = LocalDate.now(ZoneOffset.UTC);
        List<PublicDailyQuestion> all = publicDailyQuestionRepository.findAll();
        assertThat(all).hasSize(BUFFER_DAYS);

        for (int i = 0; i < BUFFER_DAYS; i++) {
            LocalDate expected = todayUtc.plusDays(i);
            assertThat(all).anyMatch(pdq ->
                    pdq.getQuestionDate().equals(expected) && pdq.getLocale().equals(LOCALE_KO)
            );
        }
    }

    @Test
    @DisplayName("이미 존재하는 날짜는 건너뛰고 누락된 날짜만 생성한다")
    void skipsExistingDates_whenSomePdqsAlreadyExist() {
        // given - 질문 1개 + today, today+2 에 PDQ 가 이미 존재
        Question q = testQuestionUtils.createSave();
        LocalDate todayUtc = LocalDate.now(ZoneOffset.UTC);
        publicDailyQuestionRepository.save(PublicDailyQuestion.publish(q, todayUtc));
        publicDailyQuestionRepository.save(PublicDailyQuestion.publish(q, todayUtc.plusDays(2)));

        // when
        provisionApplication.provision();

        // then - 총 7개 (기존 2 + 신규 5)
        List<PublicDailyQuestion> all = publicDailyQuestionRepository.findAll();
        assertThat(all).hasSize(BUFFER_DAYS);
        for (int i = 0; i < BUFFER_DAYS; i++) {
            LocalDate expected = todayUtc.plusDays(i);
            assertThat(all).anyMatch(pdq -> pdq.getQuestionDate().equals(expected));
        }
    }

    @Test
    @DisplayName("사용 횟수가 가장 적은 question_number 들 중에서만 선택한다")
    void picksFromLeastUsedQuestionNumbers() {
        // given - 4개의 ko-KR ACTIVE 질문 (questionNumber = 1, 2, 3, 4)
        Question q1 = testQuestionUtils.createSave_With_QuestionNumber(1);
        Question q2 = testQuestionUtils.createSave_With_QuestionNumber(2);
        Question q3 = testQuestionUtils.createSave_With_QuestionNumber(3);
        Question q4 = testQuestionUtils.createSave_With_QuestionNumber(4);

        // q1, q2 는 과거에 각 7번씩 사용됨 (today 이전 날짜)
        LocalDate base = LocalDate.now(ZoneOffset.UTC).minusDays(100);
        int endDays = 7;
        IntStream.range(0, endDays).forEach(i -> {
            publicDailyQuestionRepository.save(PublicDailyQuestion.publish(q1, base.plusDays(i)));
            publicDailyQuestionRepository.save(PublicDailyQuestion.publish(q2, base.plusDays(i + endDays)));
        });
        // q3, q4 는 사용 횟수 0

        // when
        provisionApplication.provision();

        // then - today 이후 신규 PDQ 들은 모두 q3 또는 q4 만 가리켜야 함
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        transactionTemplate.execute(status -> {
            List<PublicDailyQuestion> recent = publicDailyQuestionRepository.findAll().stream()
                    .filter(p -> !p.getQuestionDate().isBefore(today))
                    .toList();

            assertThat(recent).hasSize(BUFFER_DAYS);
            assertThat(recent).allMatch(p ->
                    p.getQuestion().getQuestionNumber() == 3 || p.getQuestion().getQuestionNumber() == 4
            );
            // q1, q2 (이미 7회 사용된 question_number) 는 신규 PDQ 에 절대 등장하지 않음
            assertThat(recent).noneMatch(p ->
                    p.getQuestion().getQuestionNumber() == 1 || p.getQuestion().getQuestionNumber() == 2
            );
            return null;
        });
    }

    @Test
    @DisplayName("ko-KR 이 아닌 다른 locale 의 질문은 PDQ 풀에 포함하지 않는다")
    void usesOnlyKoKrQuestions_whenOtherLocalesExist() {
        // given - ko-KR 1개 + en-US 1개
        Question koQuestion = testQuestionUtils.createSave();
        testQuestionUtils.createSave_With_Locale("en-US");

        // when
        provisionApplication.provision();

        // then - 모든 PDQ 가 ko-KR 질문을 가리킴
        List<PublicDailyQuestion> all = publicDailyQuestionRepository.findAll();
        assertThat(all).hasSize(BUFFER_DAYS);
        assertThat(all).allMatch(p -> p.getQuestion().getId().equals(koQuestion.getId()));
        assertThat(all).allMatch(p -> p.getLocale().equals(LOCALE_KO));
    }

    @Test
    @DisplayName("INACTIVE 상태의 질문은 PDQ 풀에서 제외한다")
    void usesOnlyActiveQuestions_whenInactiveQuestionsExist() {
        // given - ACTIVE 1개 + INACTIVE 1개
        Question active = testQuestionUtils.createSave();
        Question inactive = testQuestionUtils.createSave();
        transactionTemplate.executeWithoutResult(status ->
                entityManager.createQuery("UPDATE Question q SET q.status = :s WHERE q.id = :id")
                        .setParameter("s", QuestionStatus.INACTIVE)
                        .setParameter("id", inactive.getId())
                        .executeUpdate()
        );

        // when
        provisionApplication.provision();

        // then - 모든 PDQ 가 ACTIVE 질문만 가리킴
        List<PublicDailyQuestion> all = publicDailyQuestionRepository.findAll();
        assertThat(all).hasSize(BUFFER_DAYS);
        assertThat(all).allMatch(p -> p.getQuestion().getId().equals(active.getId()));
    }

    @Test
    @DisplayName("7일치 PDQ 가 이미 모두 존재하면 새로 생성하지 않는다")
    void skipsAll_whenAllDatesAlreadyProvisioned() {
        // given - 7일치 전부 미리 생성
        Question q = testQuestionUtils.createSave();
        LocalDate todayUtc = LocalDate.now(ZoneOffset.UTC);
        for (int i = 0; i < BUFFER_DAYS; i++) {
            publicDailyQuestionRepository.save(PublicDailyQuestion.publish(q, todayUtc.plusDays(i)));
        }

        // when
        provisionApplication.provision();

        // then - 여전히 7개
        assertThat(publicDailyQuestionRepository.findAll()).hasSize(BUFFER_DAYS);
    }

    @Test
    @DisplayName("스케줄러를 두 번 실행해도 PDQ 가 중복 생성되지 않는다")
    void idempotent_whenCalledTwice() {
        // given
        testQuestionUtils.createSave();

        // when
        provisionApplication.provision();
        provisionApplication.provision();

        // then
        assertThat(publicDailyQuestionRepository.findAll()).hasSize(BUFFER_DAYS);
    }

    @Test
    @DisplayName("질문이 1개뿐이면 7일치 PDQ 가 모두 같은 질문을 가리킨다")
    void allPdqsPointToSameQuestion_whenOnlyOneQuestionExists() {
        // given
        Question q = testQuestionUtils.createSave();

        // when
        provisionApplication.provision();

        // then
        List<PublicDailyQuestion> all = publicDailyQuestionRepository.findAll();
        assertThat(all).hasSize(BUFFER_DAYS);
        assertThat(all).allMatch(p -> p.getQuestion().getId().equals(q.getId()));
    }

    @Test
    @DisplayName("가용 ko-KR ACTIVE 질문이 없으면 PDQ 를 생성하지 않는다")
    void doesNotProvision_whenNoActiveKoQuestionsAvailable() {
        // given - 다른 locale 만 존재 (ko-KR ACTIVE 없음)
        testQuestionUtils.createSave_With_Locale("en-US");

        // when - 예외 없이 정상 종료해야 함
        provisionApplication.provision();

        // then - PDQ 생성 0개
        assertThat(publicDailyQuestionRepository.findAll()).isEmpty();
    }


}
