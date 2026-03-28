package site.one_question.web.admin.controller;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import site.one_question.web.admin.service.AdminAnswerPostService;

@Controller
@RequestMapping("/admin/answer-posts")
public class AdminAnswerPostController {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final AdminAnswerPostService adminAnswerPostService;

    public AdminAnswerPostController(AdminAnswerPostService adminAnswerPostService) {
        this.adminAnswerPostService = adminAnswerPostService;
    }

    @GetMapping
    public String list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            Model model) {
        LocalDate today = LocalDate.now();
        if (endDate == null) endDate = today;
        if (startDate == null) startDate = endDate.minusDays(6);

        model.addAttribute("historyByDate", adminAnswerPostService.getAiPersonaAnswerPosts(startDate, endDate));
        model.addAttribute("startDate", startDate);
        model.addAttribute("endDate", endDate);
        return "admin/answer-posts";
    }

    @PostMapping("/{answerId}/publish")
    public String publish(
            @PathVariable Long answerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime postedAt,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        Instant instant = (postedAt != null) ? postedAt.atZone(KST).toInstant() : null;
        adminAnswerPostService.publishAiPersonaAnswer(answerId, instant);
        return buildRedirect(startDate, endDate);
    }

    @PostMapping("/{answerId}/unpublish")
    public String unpublish(
            @PathVariable Long answerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        adminAnswerPostService.unpublishAiPersonaAnswer(answerId);
        return buildRedirect(startDate, endDate);
    }

    private String buildRedirect(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null) {
            return "redirect:/admin/answer-posts?startDate=" + startDate + "&endDate=" + endDate;
        }
        return "redirect:/admin/answer-posts";
    }
}
