package site.one_question.web.admin.controller;

import java.time.LocalDate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import site.one_question.web.admin.service.AdminPublicQuestionService;

@Slf4j
@Controller
@RequestMapping("/admin/public-questions")
public class AdminPublicQuestionController {

    private final AdminPublicQuestionService adminPublicQuestionService;

    public AdminPublicQuestionController(AdminPublicQuestionService adminPublicQuestionService) {
        this.adminPublicQuestionService = adminPublicQuestionService;
    }

    @GetMapping
    public String view(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            Model model) {
        log.info("[Admin] 공개 일일 질문 조회 시작 date={}", date);
        AdminPublicQuestionService.PublicQuestionPageData data =
                adminPublicQuestionService.getPageData(date);
        model.addAttribute("selectedDate", data.selectedDate());
        model.addAttribute("dailyCounts", data.dailyCounts());
        model.addAttribute("maxDailyCount", data.maxDailyCount());
        model.addAttribute("questionContent", data.questionContent());
        model.addAttribute("answers", data.answers());
        log.info("[Admin] 공개 일일 질문 조회 완료 selected={}, answers={}",
                data.selectedDate(), data.answers().size());
        return "admin/public-questions";
    }
}
