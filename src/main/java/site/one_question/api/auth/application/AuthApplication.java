package site.one_question.api.auth.application;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.one_question.api.auth.domain.RefreshTokenService;
import site.one_question.api.auth.domain.exception.AccountAlreadyLinkedException;
import site.one_question.api.auth.domain.exception.AppleAccountAlreadyExistsException;
import site.one_question.api.auth.domain.exception.GoogleAccountAlreadyExistsException;
import site.one_question.api.auth.domain.exception.InvalidTokenException;
import site.one_question.global.security.service.JwtService;
import site.one_question.api.auth.infrastructure.oauth.AppleAuthClient;
import site.one_question.api.auth.infrastructure.oauth.AppleTokenVerifier;
import site.one_question.api.auth.infrastructure.oauth.AppleTokenVerifier.AppleTokenPayload;
import site.one_question.api.auth.infrastructure.oauth.FirebaseTokenVerifier;
import site.one_question.api.auth.infrastructure.oauth.FirebaseTokenVerifier.FirebaseTokenPayload;
import site.one_question.api.auth.infrastructure.oauth.GoogleTokenVerifier;
import site.one_question.api.auth.presentation.request.AnonymousAuthRequest;
import site.one_question.api.auth.presentation.request.AppleAuthRequest;
import site.one_question.api.auth.presentation.request.CheckAppleLinkRequest;
import site.one_question.api.auth.presentation.request.GoogleAuthRequest;
import site.one_question.api.auth.presentation.request.CheckGoogleLinkRequest;
import site.one_question.api.auth.presentation.request.LinkToAppleRequest;
import site.one_question.api.auth.presentation.request.LinkToGoogleRequest;
import site.one_question.api.auth.presentation.request.ReissueAuthTokenRequest;
import site.one_question.api.auth.presentation.response.AuthResponse;
import site.one_question.api.auth.presentation.response.CheckAppleLinkResponse;
import site.one_question.api.auth.presentation.response.CheckGoogleLinkResponse;
import site.one_question.api.auth.presentation.response.ReissueAuthTokenResponse;
import site.one_question.api.member.domain.AuthSocialProvider;
import site.one_question.api.member.domain.Member;
import site.one_question.api.member.domain.MemberService;
import site.one_question.api.answerpost.domain.AnswerPostLikeService;
import site.one_question.api.answerpost.domain.AnswerPostService;
import site.one_question.api.question.domain.QuestionLikeService;
import site.one_question.api.question.domain.DailyQuestionAnswerService;
import site.one_question.api.question.domain.DailyQuestionCandidateRepository;
import site.one_question.api.question.domain.DailyQuestionService;
import site.one_question.api.notification.domain.FcmTokenService;
import site.one_question.api.notification.domain.QuestionReminderSettingService;
import site.one_question.api.question.domain.QuestionCycleService;
import site.one_question.global.i18n.LocaleNormalizer;

@Service
@Transactional
@RequiredArgsConstructor
public class AuthApplication {

    private final GoogleTokenVerifier googleTokenVerifier;
    private final AppleTokenVerifier appleTokenVerifier;
    private final AppleAuthClient appleAuthClient;
    private final FirebaseTokenVerifier firebaseTokenVerifier;
    private final JwtService jwtService;
    private final MemberService memberService;
    private final RefreshTokenService refreshTokenService;
    private final QuestionCycleService questionCycleService;
    private final DailyQuestionService dailyQuestionService;
    private final DailyQuestionAnswerService dailyQuestionAnswerService;
    private final DailyQuestionCandidateRepository dailyQuestionCandidateRepository;
    private final AnswerPostLikeService answerPostLikeService;
    private final QuestionLikeService questionLikeService;
    private final AnswerPostService answerPostService;
    private final QuestionReminderSettingService questionReminderSettingService;
    private final FcmTokenService fcmTokenService;

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
        AppleTokenPayload payload = appleTokenVerifier.verify(request.identityToken(), request.rawNonce());
        LocalDate joinedDate = LocalDate.now(ZoneId.of(timezone));

        MemberLookup result = findOrCreateMember(
                AuthSocialProvider.APPLE,
                payload.providerId(),
                payload.email(),
                request.name(),
                locale,
                joinedDate,
                timezone
        );

        // 회원 탈퇴 시 Apple revoke를 위해 authorizationCode를 refresh_token으로 교환해 저장.
        // 이미 refresh_token이 있는 회원은 덮어쓰지 않는다 (Apple은 한 사용자당 활성 refresh_token 1개를 보장).
        if (result.member().getAppleRefreshToken() == null) {
            exchangeAndStoreAppleRefreshToken(result.member(), request.authorizationCode());
        }

        return issueAuthResponse(result.member(), result.isNewMember());
    }

    private void exchangeAndStoreAppleRefreshToken(Member member, String authorizationCode) {
        if (authorizationCode == null || authorizationCode.isBlank()) {
            return;
        }
        String refreshToken = appleAuthClient.exchangeCodeForRefreshToken(authorizationCode);
        if (refreshToken != null) {
            member.updateAppleRefreshToken(refreshToken);
        }
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
        MemberLookup result = findOrCreateMember(provider, providerId, email, name, locale, localDate, timezone);
        return issueAuthResponse(result.member(), result.isNewMember());
    }

    private MemberLookup findOrCreateMember(
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

        return new MemberLookup(member, isNewMember);
    }

    private AuthResponse issueAuthResponse(Member member, boolean isNewMember) {
        String accessToken = jwtService.issueAccessToken(member.getId(), member.getEmail(), member.getPermission());
        String refreshToken = jwtService.issueRefreshToken(member.getId(), member.getEmail(), member.getPermission());
        refreshTokenService.save(member, refreshToken, jwtService.extractExpiration(refreshToken));
        return new AuthResponse(accessToken, refreshToken, isNewMember);
    }

    public AuthResponse anonymousAuth(AnonymousAuthRequest request, String locale, String timezone) {
        FirebaseTokenPayload payload = firebaseTokenVerifier.verify(request.idToken());

        String providerId = payload.uid();
        LocalDate joinedDate = LocalDate.now(ZoneId.of(timezone));

        return loginOrSignup(
                AuthSocialProvider.ANONYMOUS,
                providerId,
                null,
                "Anonymous",
                locale,
                joinedDate,
                timezone
        );
    }

    @Transactional(readOnly = true)
    public CheckGoogleLinkResponse checkGoogleLinking(CheckGoogleLinkRequest request) {
        GoogleIdToken.Payload payload = googleTokenVerifier.verify(request.idToken());
        String providerId = payload.getSubject();

        boolean exists = memberService.existsByProviderAndProviderId(AuthSocialProvider.GOOGLE, providerId);
        return new CheckGoogleLinkResponse(exists);
    }

    public AuthResponse linkToGoogle(Long memberId, LinkToGoogleRequest request) {
        Member member = memberService.findById(memberId);

        if (!member.isAnonymous()) {
            throw new AccountAlreadyLinkedException();
        }

        GoogleIdToken.Payload payload = googleTokenVerifier.verify(request.idToken());
        String googleProviderId = payload.getSubject();
        String email = payload.getEmail();
        String name = request.name() != null ? request.name() : (String) payload.get("name");

        Optional<Member> existing = memberService.findByProviderAndProviderId(
                AuthSocialProvider.GOOGLE, googleProviderId
        );
        if (existing.isPresent()) {
            throw new GoogleAccountAlreadyExistsException();
        }

        member.linkToGoogle(email, name, googleProviderId);

        return issueAuthResponse(member, false);
    }

    @Transactional(readOnly = true)
    public CheckAppleLinkResponse checkAppleLinking(CheckAppleLinkRequest request) {
        AppleTokenPayload payload = appleTokenVerifier.verify(request.identityToken(), request.rawNonce());
        String providerId = payload.providerId();

        boolean exists = memberService.existsByProviderAndProviderId(AuthSocialProvider.APPLE, providerId);
        return new CheckAppleLinkResponse(exists);
    }

    public AuthResponse linkToApple(Long memberId, LinkToAppleRequest request) {
        Member member = memberService.findById(memberId);

        if (!member.isAnonymous()) {
            throw new AccountAlreadyLinkedException();
        }

        AppleTokenPayload payload = appleTokenVerifier.verify(request.identityToken(), request.rawNonce());
        String appleProviderId = payload.providerId();
        String email = payload.email();
        String name = request.name();

        Optional<Member> existing = memberService.findByProviderAndProviderId(
                AuthSocialProvider.APPLE, appleProviderId
        );
        if (existing.isPresent()) {
            throw new AppleAccountAlreadyExistsException();
        }

        member.linkToApple(email, name, appleProviderId);
        exchangeAndStoreAppleRefreshToken(member, request.authorizationCode());

        return issueAuthResponse(member, false);
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
        fcmTokenService.deleteByMemberId(memberId);
    }

    public void withdraw(Long memberId) {
        // App Store Review Guideline 5.1.1(v): Apple 로그인 사용자의 경우 탈퇴 시 Apple 권한도 revoke해야 한다.
        // 실패해도 회원 탈퇴 흐름은 계속 진행 (best-effort)
        Member member = memberService.findById(memberId);
        if (member.isApple() && member.getAppleRefreshToken() != null) {
            appleAuthClient.revokeRefreshToken(member.getAppleRefreshToken());
        }

        refreshTokenService.deleteByMemberId(memberId);
        fcmTokenService.deleteByMemberId(memberId);
        questionReminderSettingService.deleteByMemberId(memberId);
        answerPostLikeService.deleteByMemberId(memberId);
        questionLikeService.deleteByMemberId(memberId);
        answerPostService.deleteByMemberId(memberId);
        dailyQuestionAnswerService.deleteByMemberId(memberId);
        dailyQuestionCandidateRepository.deleteByMemberId(memberId);
        dailyQuestionService.deleteByMemberId(memberId);
        questionCycleService.deleteByMemberId(memberId);
        memberService.withdraw(memberId);
    }

    private record MemberLookup(Member member, boolean isNewMember) {}
}
