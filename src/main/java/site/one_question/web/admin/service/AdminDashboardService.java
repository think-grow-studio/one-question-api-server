package site.one_question.web.admin.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.one_question.web.admin.dto.AnswerDateRow;
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

    public record WauMemberRow(Long memberId, String fullName, LocalDate joinedDate, long answerCount) {}

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
        Instant todayStart = today.atStartOfDay(KST).toInstant();
        Instant tomorrowStart = today.plusDays(1).atStartOfDay(KST).toInstant();
        Instant thirtyDaysAgo = today.minusDays(29).atStartOfDay(KST).toInstant();
        Instant sevenDaysAgo = today.minusDays(7).atStartOfDay(KST).toInstant();

        List<WauMemberRow> wauMembers = buildWauMembers(sevenDaysAgo, todayStart);
        List<DailyAnswerCountRow> dailyTrend = buildDailyTrend(today, thirtyDaysAgo);
        long maxDailyCount = dailyTrend.stream().mapToLong(DailyAnswerCountRow::count).max().orElse(1L);
        List<MemberAnswerCountRow> leaderboard = buildLeaderboard();
        List<RecentAnswerRow> recentAnswers = buildRecentAnswers();
        DashboardStats stats = buildStats(today, todayStart, tomorrowStart, wauMembers.size());

        log.info("[Dashboard] 완료 | totalMembers={}, totalAnswers={}, todayAnswers={} (existing={}, new={}), wau={}",
                stats.totalMembers(), stats.totalAnswers(),
                stats.todayAnswers(), stats.todayExistingAnswers(), stats.todayNewAnswers(),
                stats.wauCount());

        return new DashboardData(stats, dailyTrend, maxDailyCount, leaderboard, recentAnswers, wauMembers);
    }

    private DashboardStats buildStats(LocalDate today, Instant todayStart, Instant tomorrowStart, int wauCount) {
        long totalMembers = memberRepository.countExcludingAdmin();
        long totalAnswers = dqaRepository.countAllAnswers();
        long todayAnswers = dqaRepository.countAnswersBetween(todayStart, tomorrowStart);
        long todayNew = dqaRepository.countNewAnswersBetween(todayStart, tomorrowStart, today);
        long todayExisting = todayAnswers - todayNew;
        return new DashboardStats(totalMembers, totalAnswers, todayAnswers, todayExisting, todayNew, wauCount);
    }

    private List<DailyAnswerCountRow> buildDailyTrend(LocalDate today, Instant from) {
        LocalDate thirtyDaysAgo = today.minusDays(29);
        Map<LocalDate, long[]> dailyMap = new HashMap<>();
        for (AnswerDateRow row : dqaRepository.findAnswerDatesAndJoinedDatesSince(from)) {
            LocalDate answerDate = row.answeredAt().atZone(KST).toLocalDate();
            boolean isNew = row.joinedDate().equals(answerDate);
            long[] c = dailyMap.computeIfAbsent(answerDate, d -> new long[2]);
            if (isNew) c[1]++; else c[0]++;
        }
        return thirtyDaysAgo.datesUntil(today.plusDays(1))
                .map(d -> {
                    long[] c = dailyMap.getOrDefault(d, new long[2]);
                    return new DailyAnswerCountRow(d, c[0] + c[1], c[0], c[1]);
                })
                .sorted(Comparator.comparing(DailyAnswerCountRow::date).reversed())
                .toList();
    }

    private List<MemberAnswerCountRow> buildLeaderboard() {
        return dqaRepository.findLeaderboardData().stream()
                .map(row -> new MemberAnswerCountRow(
                        row.memberId(),
                        row.fullName(),
                        row.joinedDate(),
                        row.answerCount(),
                        LocalDateTime.ofInstant(row.lastAnsweredAt(), KST)))
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

    private List<WauMemberRow> buildWauMembers(Instant from, Instant to) {
        return dqaRepository.findWauData(from, to).stream()
                .map(row -> new WauMemberRow(row.memberId(), row.fullName(), row.joinedDate(), row.answerCount()))
                .toList();
    }
}
