package site.one_question.auth.application;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.one_question.auth.domain.RefreshTokenService;
import site.one_question.auth.domain.exception.InvalidTokenException;
import site.one_question.global.security.service.JwtService;
import site.one_question.auth.infrastructure.oauth.AppleTokenVerifier;
import site.one_question.auth.infrastructure.oauth.AppleTokenVerifier.AppleTokenPayload;
import site.one_question.auth.infrastructure.oauth.GoogleTokenVerifier;
import site.one_question.auth.presentation.request.AppleAuthRequest;
import site.one_question.auth.presentation.request.GoogleAuthRequest;
import site.one_question.auth.presentation.request.ReissueAuthTokenRequest;
import site.one_question.auth.presentation.response.AuthResponse;
import site.one_question.auth.presentation.response.ReissueAuthTokenResponse;
import site.one_question.member.domain.AuthSocialProvider;
import site.one_question.member.domain.Member;
import site.one_question.member.domain.MemberService;
import site.one_question.question.domain.DailyQuestionAnswerService;
import site.one_question.question.domain.DailyQuestionService;
import site.one_question.question.domain.QuestionCycleService;

@Service
@Transactional
@RequiredArgsConstructor
public class AuthApplication {

    private final GoogleTokenVerifier googleTokenVerifier;
    private final AppleTokenVerifier appleTokenVerifier;
    private final JwtService jwtService;
    private final MemberService memberService;
    private final RefreshTokenService refreshTokenService;
    private final QuestionCycleService questionCycleService;
    private final DailyQuestionService dailyQuestionService;
    private final DailyQuestionAnswerService dailyQuestionAnswerService;

    public AuthResponse googleAuth(GoogleAuthRequest request, String locale, String timezone) {
        GoogleIdToken.Payload payload = googleTokenVerifier.verify(request.idToken());

        String providerId = payload.getSubject();
        String email = payload.getEmail();
        String name = request.name() != null ? request.name() : (String) payload.get("name");
        LocalDate joinedDate = LocalDate.now(ZoneId.of(timezone));

        return loginOrSignup(
                AuthSocialProvider.GOOGLE,
                providerId,
                email,
                name,
                locale,
                joinedDate,
                timezone
        );
    }

    public AuthResponse appleAuth(AppleAuthRequest request, String locale, String timezone) {
        AppleTokenPayload payload = appleTokenVerifier.verify(request.identityToken());
        LocalDate joinedDate = LocalDate.now(ZoneId.of(timezone));

        return loginOrSignup(
                AuthSocialProvider.APPLE,
                payload.providerId(),
                payload.email(),
                request.name(),
                locale,
                joinedDate,
                timezone
        );
    }

    private AuthResponse loginOrSignup(
            AuthSocialProvider provider,
            String providerId,
            String email,
            String name,
            String locale,
            LocalDate localDate,
            String timezone
    ) {
        Optional<Member> existingMember = memberService.findByProviderAndProviderId(provider, providerId);
        boolean isNewMember = existingMember.isEmpty();

        Member member = existingMember.orElseGet(() -> {
            Member newMember = memberService.createMember(
                    email,
                    name != null ? name : "Member",
                    provider,
                    providerId,
                    locale,
                    localDate
            );
            questionCycleService.createFirstCycle(newMember, timezone);
            return newMember;
        });

        String accessToken = jwtService.issueAccessToken(member.getId(),member.getEmail(),member.getPermission());
        String refreshToken = jwtService.issueRefreshToken(member.getId(),member.getEmail(),member.getPermission());
        refreshTokenService.save(member, refreshToken, jwtService.extractExpiration(refreshToken));

        return new AuthResponse(accessToken, refreshToken, isNewMember);
    }

    public ReissueAuthTokenResponse reissueToken(ReissueAuthTokenRequest request) {
        String token = request.refreshToken();

        if (!jwtService.isValid(token) || !jwtService.isRefreshToken(token)) {
            throw new InvalidTokenException("Invalid refresh token");
        }

        Long memberId = jwtService.extractMemberId(token);
        refreshTokenService.validateAndGet(memberId, token);

        Member member = memberService.findById(memberId);

        String newAccessToken = jwtService.issueAccessToken(memberId,member.getEmail(),member.getPermission());
        String newRefreshToken = jwtService.issueRefreshToken(memberId,member.getEmail(),member.getPermission());
        refreshTokenService.save(member, newRefreshToken, jwtService.extractExpiration(newRefreshToken));

        return new ReissueAuthTokenResponse(newAccessToken, newRefreshToken);
    }

    public void logout(Long memberId) {
        refreshTokenService.deleteByMemberId(memberId);
    }

    public void withdraw(Long memberId) {
        refreshTokenService.deleteByMemberId(memberId);
        dailyQuestionAnswerService.deleteByMemberId(memberId);
        dailyQuestionService.deleteByMemberId(memberId);
        questionCycleService.deleteByMemberId(memberId);
        memberService.withdraw(memberId);
    }
}
