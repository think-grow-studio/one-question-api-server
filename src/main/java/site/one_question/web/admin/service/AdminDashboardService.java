package site.one_question.web.admin.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.one_question.api.member.domain.Member;
import site.one_question.api.question.domain.DailyQuestionAnswer;
import site.one_question.web.admin.repository.AdminDailyQuestionAnswerRepository;
import site.one_question.web.admin.repository.AdminMemberRepository;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminDashboardService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final AdminDailyQuestionAnswerRepository dqaRepository;
    private final AdminMemberRepository memberRepository;

    public record DashboardStats(long totalMembers, long totalAnswers, long todayAnswers, int wauCount) {}

    public record RecentAnswerRow(Long memberId, String fullName, LocalDate joinedDate,
                                  int changeCount, String questionContent,
                                  String answerContent, LocalDateTime answeredAt) {}

    public record MemberAnswerCountRow(Long memberId, String fullName, LocalDate joinedDate,
                                       long answerCount, LocalDateTime lastAnsweredAt) {}

    public record DailyAnswerCountRow(LocalDate date, long count) {}

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
        List<DailyQuestionAnswer> allAnswers = dqaRepository.findAllWithMember();
        List<WauMemberRow> wauMembers = buildWauMembers(today, allAnswers);
        List<DailyAnswerCountRow> dailyTrend = buildDailyTrend(today);
        long maxDailyCount = dailyTrend.stream()
                .mapToLong(DailyAnswerCountRow::count)
                .max()
                .orElse(1L);

        return new DashboardData(
                buildStats(today, allAnswers, wauMembers.size()),
                dailyTrend,
                maxDailyCount,
                buildLeaderboard(allAnswers),
                buildRecentAnswers(),
                wauMembers);
    }

    private DashboardStats buildStats(LocalDate today, List<DailyQuestionAnswer> allAnswers, int wauCount) {
        long totalMembers = memberRepository.countExcludingAdmin();
        long totalAnswers = allAnswers.size();
        long todayAnswers = allAnswers.stream()
                .filter(dqa -> dqa.getAnsweredAt().atZone(KST).toLocalDate().equals(today))
                .count();
        return new DashboardStats(totalMembers, totalAnswers, todayAnswers, wauCount);
    }

    private List<DailyAnswerCountRow> buildDailyTrend(LocalDate today) {
        LocalDate thirtyDaysAgo = today.minusDays(29);
        Instant from = thirtyDaysAgo.atStartOfDay(KST).toInstant();
        Map<LocalDate, Long> dailyMap = dqaRepository.findAnsweredAtsFrom(from).stream()
                .collect(Collectors.groupingBy(
                        inst -> inst.atZone(KST).toLocalDate(),
                        Collectors.counting()));
        return thirtyDaysAgo.datesUntil(today.plusDays(1))
                .map(d -> new DailyAnswerCountRow(d, dailyMap.getOrDefault(d, 0L)))
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
        return dqaRepository.findRecentAnswers(Pageable.ofSize(100)).stream()
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
