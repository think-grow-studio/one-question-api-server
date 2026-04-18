package site.one_question.web.admin.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import site.one_question.web.admin.service.AdminDashboardService;

@Slf4j
@Controller
@RequestMapping("/admin/dashboard")
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService;

    public AdminDashboardController(AdminDashboardService adminDashboardService) {
        this.adminDashboardService = adminDashboardService;
    }

    @GetMapping
    public String dashboard(Model model) {
        log.info("[Admin] 대시보드 조회 시작");
        AdminDashboardService.DashboardData data = adminDashboardService.getDashboardData();
        model.addAttribute("stats", data.stats());
        model.addAttribute("dailyTrend", data.dailyTrend());
        model.addAttribute("maxDailyCount", data.maxDailyCount());
        model.addAttribute("leaderboard", data.leaderboard());
        model.addAttribute("recentAnswers", data.recentAnswers());
        model.addAttribute("wauMembers", data.wauMembers());
        log.info("[Admin] 대시보드 조회 완료");
        return "admin/dashboard";
    }
}
