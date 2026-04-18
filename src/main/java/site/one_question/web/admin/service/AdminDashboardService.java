package site.one_question.web.admin.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.one_question.api.member.domain.Member;
import site.one_question.api.question.domain.DailyQuestionAnswer;
import site.one_question.web.admin.repository.AdminDailyQuestionAnswerRepository;
import site.one_question.web.admin.repository.AdminMemberRepository;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminDashboardService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final AdminDailyQuestionAnswerRepository dqaRepository;
    private final AdminMemberRepository memberRepository;

    public record DashboardStats(long totalMembers, long totalAnswers,
                                  long todayAnswers, long todayExistingAnswers, long todayNewAnswers,
                                  int wauCount) {}

    public record RecentAnswerRow(Long memberId, String fullName, LocalDate joinedDate,
                                  int changeCount, String questionContent,
                                  String answerContent, LocalDateTime answeredAt) {}

    public record MemberAnswerCountRow(Long memberId, String fullName, LocalDate joinedDate,
                                       long answerCount, LocalDateTime lastAnsweredAt) {}

    public record DailyAnswerCountRow(LocalDate date, long count, long existingCount, long newCount) {}

    public record WauMemberRow(Long memberId, String fullName, long answerCount) {}

    public record DashboardData(
            DashboardStats stats,
            List<DailyAnswerCountRow> dailyTrend,
            long maxDailyCount,
            List<MemberAnswerCountRow> leaderboard,
            List<RecentAnswerRow> recentAnswers,
            List<WauMemberRow> wauMembers
    ) {}

    public DashboardData getDashboardData() {
        LocalDate today = LocalDate.now(KST);

        log.info("[Dashboard] findAllWithMember 시작");
        List<DailyQuestionAnswer> allAnswers = dqaRepository.findAllWithMember();
        log.info("[Dashboard] findAllWithMember 완료 rows={}", allAnswers.size());

        log.info("[Dashboard] buildWauMembers 시작");
        List<WauMemberRow> wauMembers = buildWauMembers(today, allAnswers);
        log.info("[Dashboard] buildWauMembers 완료 wau={}", wauMembers.size());

        log.info("[Dashboard] buildDailyTrend 시작");
        List<DailyAnswerCountRow> dailyTrend = buildDailyTrend(today, allAnswers);
        long maxDailyCount = dailyTrend.stream().mapToLong(DailyAnswerCountRow::count).max().orElse(1L);
        log.info("[Dashboard] buildDailyTrend 완료");

        log.info("[Dashboard] buildLeaderboard 시작");
        List<MemberAnswerCountRow> leaderboard = buildLeaderboard(allAnswers);
        log.info("[Dashboard] buildLeaderboard 완료 members={}", leaderboard.size());

        log.info("[Dashboard] findRecentAnswers 시작");
        List<RecentAnswerRow> recentAnswers = buildRecentAnswers();
        log.info("[Dashboard] findRecentAnswers 완료 rows={}", recentAnswers.size());

        log.info("[Dashboard] buildStats 시작");
        DashboardStats stats = buildStats(today, allAnswers, wauMembers.size());
        log.info("[Dashboard] buildStats 완료");

        log.info("[Dashboard] 완료 | totalMembers={}, totalAnswers={}, todayAnswers={} (existing={}, new={}), wau={}",
                stats.totalMembers(), stats.totalAnswers(),
                stats.todayAnswers(), stats.todayExistingAnswers(), stats.todayNewAnswers(),
                stats.wauCount());

        return new DashboardData(stats, dailyTrend, maxDailyCount, leaderboard, recentAnswers, wauMembers);
    }

    private DashboardStats buildStats(LocalDate today, List<DailyQuestionAnswer> allAnswers, int wauCount) {
        long totalMembers = memberRepository.countExcludingAdmin();
        long totalAnswers = allAnswers.size();
        List<DailyQuestionAnswer> todayList = allAnswers.stream()
                .filter(dqa -> dqa.getAnsweredAt().atZone(KST).toLocalDate().equals(today))
                .toList();
        long todayAnswers = todayList.size();
        long todayNew = todayList.stream()
                .filter(dqa -> dqa.getMember().getJoinedDate().equals(today))
                .count();
        long todayExisting = todayAnswers - todayNew;
        return new DashboardStats(totalMembers, totalAnswers, todayAnswers, todayExisting, todayNew, wauCount);
    }

    private List<DailyAnswerCountRow> buildDailyTrend(LocalDate today, List<DailyQuestionAnswer> allAnswers) {
        LocalDate thirtyDaysAgo = today.minusDays(29);
        Instant from = thirtyDaysAgo.atStartOfDay(KST).toInstant();
        Map<LocalDate, long[]> dailyMap = new HashMap<>();
        allAnswers.stream()
                .filter(dqa -> !dqa.getAnsweredAt().isBefore(from))
                .forEach(dqa -> {
                    LocalDate answerDate = dqa.getAnsweredAt().atZone(KST).toLocalDate();
                    boolean isNew = dqa.getMember().getJoinedDate().equals(answerDate);
                    long[] c = dailyMap.computeIfAbsent(answerDate, d -> new long[2]);
                    if (isNew) c[1]++; else c[0]++;
                });
        return thirtyDaysAgo.datesUntil(today.plusDays(1))
                .map(d -> {
                    long[] c = dailyMap.getOrDefault(d, new long[2]);
                    return new DailyAnswerCountRow(d, c[0] + c[1], c[0], c[1]);
                })
                .sorted(Comparator.comparing(DailyAnswerCountRow::date).reversed())
                .toList();
    }

    private List<MemberAnswerCountRow> buildLeaderboard(List<DailyQuestionAnswer> allAnswers) {
        return allAnswers.stream()
                .collect(Collectors.groupingBy(dqa -> dqa.getMember().getId()))
                .entrySet().stream()
                .map(e -> {
                    List<DailyQuestionAnswer> memberAnswers = e.getValue();
                    Member m = memberAnswers.get(0).getMember();
                    Instant lastAnsweredAt = memberAnswers.stream()
                            .map(DailyQuestionAnswer::getAnsweredAt)
                            .max(Comparator.naturalOrder())
                            .orElseThrow();
                    return new MemberAnswerCountRow(
                            m.getId(),
                            m.getFullName(),
                            m.getJoinedDate(),
                            memberAnswers.size(),
                            LocalDateTime.ofInstant(lastAnsweredAt, KST));
                })
                .sorted(Comparator.comparingLong(MemberAnswerCountRow::answerCount).reversed())
                .toList();
    }

    private List<RecentAnswerRow> buildRecentAnswers() {
        return dqaRepository.findRecentAnswers(Pageable.ofSize(50)).stream()
                .map(dqa -> new RecentAnswerRow(
                        dqa.getMember().getId(),
                        dqa.getMember().getFullName(),
                        dqa.getMember().getJoinedDate(),
                        dqa.getDailyQuestionId().getChangeCount(),
                        dqa.getDailyQuestionId().getQuestion().getContent(),
                        dqa.getContent(),
                        LocalDateTime.ofInstant(dqa.getAnsweredAt(), KST)))
                .toList();
    }

    private List<WauMemberRow> buildWauMembers(LocalDate today, List<DailyQuestionAnswer> allAnswers) {
        Instant from = today.minusDays(7).atStartOfDay(KST).toInstant();
        Instant to = today.atStartOfDay(KST).toInstant();
        return allAnswers.stream()
                .filter(dqa -> !dqa.getAnsweredAt().isBefore(from) && dqa.getAnsweredAt().isBefore(to))
                .collect(Collectors.groupingBy(dqa -> dqa.getMember().getId()))
                .entrySet().stream()
                .filter(e -> e.getValue().size() >= 3)
                .map(e -> {
                    Member m = e.getValue().get(0).getMember();
                    return new WauMemberRow(m.getId(), m.getFullName(), e.getValue().size());
                })
                .sorted(Comparator.comparing(WauMemberRow::memberId))
                .toList();
    }
}
