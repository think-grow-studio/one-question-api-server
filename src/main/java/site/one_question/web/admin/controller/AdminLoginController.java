package site.one_question.web.admin.controller;

import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.time.LocalDate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import site.one_question.web.admin.service.AdminService;
import site.one_question.global.security.service.JwtService;
import site.one_question.api.member.domain.MemberPermission;

@Controller
@RequestMapping("/admin")
public class AdminLoginController {

    private static final String ADMIN_COOKIE_NAME = "ADMIN_TOKEN";

    private final JwtService jwtService;
    private final AdminService adminService;
    private final String adminEmail;

    public AdminLoginController(
            JwtService jwtService,
            AdminService adminService,
            @Value("${admin.email}") String adminEmail) {
        this.jwtService = jwtService;
        this.adminService = adminService;
        this.adminEmail = adminEmail;
    }

    @GetMapping("/login")
    public String loginPage(Authentication authentication) {
        if (authentication != null && authentication.isAuthenticated()) {
            return "redirect:/admin";
        }
        return "admin/login";
    }

    @PostMapping("/login")
    public String login(@RequestParam String email, HttpServletResponse response) {
        if (!adminEmail.equals(email)) {
            return "redirect:/admin/login?error";
        }

        String token = jwtService.issueAccessToken(0L, email, MemberPermission.FREE);

        ResponseCookie cookie = ResponseCookie.from(ADMIN_COOKIE_NAME, token)
                .httpOnly(true)
                .secure(true)
                .sameSite("Lax")
                .path("/admin")
                .maxAge(Duration.ofHours(1))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        return "redirect:/admin";
    }

    @GetMapping
    public String dashboard(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            Model model) {
        LocalDate today = LocalDate.now();
        if (endDate == null) endDate = today;
        if (startDate == null) startDate = endDate.minusDays(6);

        model.addAttribute("historyByDate", adminService.getAiPersonaHistory(startDate, endDate));
        model.addAttribute("startDate", startDate);
        model.addAttribute("endDate", endDate);
        return "admin/dashboard";
    }

    @PostMapping("/logout")
    public String logout(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(ADMIN_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(true)
                .sameSite("Lax")
                .path("/admin")
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        return "redirect:/admin/login";
    }
}
