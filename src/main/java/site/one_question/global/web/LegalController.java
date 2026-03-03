package site.one_question.global.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 법적 문서(이용약관, 개인정보 처리방침) 관련 컨트롤러
 */
@Controller
public class LegalController {

    /**
     * 이용약관 및 개인정보 처리방침 통합 페이지
     */
    @GetMapping("/legal-document")
    public String legalDocument() {
        return "legal-document";
    }
}
