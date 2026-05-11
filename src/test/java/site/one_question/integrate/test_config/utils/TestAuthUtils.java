package site.one_question.integrate.test_config.utils;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import site.one_question.security.service.JwtService;
import site.one_question.api.member.domain.Member;

@Component
@RequiredArgsConstructor
public class TestAuthUtils {

    private final JwtService jwtService;

    public String createBearerToken(Member member) {
        String token = jwtService.issueAccessToken(
                member.getId(),
                member.getEmail(),
                member.getPermission()
        );
        return "Bearer " + token;
    }
}
