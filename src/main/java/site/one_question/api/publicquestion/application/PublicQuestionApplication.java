package site.one_question.api.publicquestion.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.one_question.api.member.domain.Member;
import site.one_question.api.member.domain.MemberService;
import site.one_question.api.publicquestion.domain.PublicDailyQuestion;
import site.one_question.api.publicquestion.domain.PublicDailyQuestionAnswer;
import site.one_question.api.publicquestion.domain.PublicDailyQuestionAnswerLikeService;
import site.one_question.api.publicquestion.domain.PublicDailyQuestionAnswerService;
import site.one_question.api.publicquestion.domain.PublicDailyQuestionService;
import site.one_question.api.publicquestion.presentation.response.CreatePublicDailyQuestionAnswerResponse;
import site.one_question.api.publicquestion.presentation.response.GetPublicDailyQuestionAnswersResponse;
import site.one_question.api.publicquestion.presentation.response.GetPublicDailyQuestionResponse;
import site.one_question.api.publicquestion.presentation.response.ToggleLikeResponse;
import site.one_question.api.publicquestion.presentation.response.UpdatePublicDailyQuestionAnswerResponse;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class PublicQuestionApplication {

    private final MemberService memberService;
    private final PublicDailyQuestionService publicDailyQuestionService;
    private final PublicDailyQuestionAnswerService publicDailyQuestionAnswerService;
    private final PublicDailyQuestionAnswerLikeService publicDailyQuestionAnswerLikeService;

    public GetPublicDailyQuestionResponse getPublicDailyQuestion(Long memberId, LocalDate date) {
        Member member = memberService.findById(memberId);
        PublicDailyQuestion pdq = publicDailyQuestionService.findByDateAndMember(date, member);
        PublicDailyQuestionAnswer answer = publicDailyQuestionAnswerService.findBy(pdq, member).orElse(null);

        long likeCount = 0L;
        boolean liked = false;
        if (answer != null) {
            likeCount = publicDailyQuestionAnswerLikeService.countBy(answer);
            liked = publicDailyQuestionAnswerLikeService.isLikedBy(answer, member);
        }

        return GetPublicDailyQuestionResponse.from(pdq, answer, likeCount, liked);
    }

    @Transactional
    public CreatePublicDailyQuestionAnswerResponse createAnswer(
            Long memberId,
            Long pdqId,
            String content,
            String timezone
    ) {
        Member member = memberService.findById(memberId);
        PublicDailyQuestion pdq = publicDailyQuestionService.findById(pdqId);
        PublicDailyQuestionAnswer saved = publicDailyQuestionAnswerService.createAndSave(pdq, member, content, timezone);
        return CreatePublicDailyQuestionAnswerResponse.from(saved, timezone);
    }

    @Transactional
    public UpdatePublicDailyQuestionAnswerResponse updateAnswer(
            Long memberId,
            Long pdqId,
            Long answerId,
            String content,
            String timezone
    ) {
        PublicDailyQuestionAnswer answer = publicDailyQuestionAnswerService
                .findOwnedByIdAndPdqIdOrThrow(answerId, pdqId, memberId);
        answer.updateContent(content);
        return UpdatePublicDailyQuestionAnswerResponse.from(answer, timezone);
    }

    @Transactional
    public void deleteAnswer(Long memberId, Long pdqId, Long answerId) {
        PublicDailyQuestionAnswer answer = publicDailyQuestionAnswerService
                .findOwnedByIdAndPdqIdOrThrow(answerId, pdqId, memberId);
        publicDailyQuestionAnswerLikeService.deleteByAnswer(answer);
        publicDailyQuestionAnswerService.delete(answer);
    }

    public GetPublicDailyQuestionAnswersResponse getAnswers(
            Long memberId,
            Long pdqId,
            Instant cursorAnsweredAt,
            Long cursorId,
            int size
    ) {
        // PDQ 존재 확인 (없으면 404)
        publicDailyQuestionService.findById(pdqId);

        Instant effectiveAnsweredAt = cursorAnsweredAt;
        if (effectiveAnsweredAt == null) {
            effectiveAnsweredAt = Instant.now();
        }
        Long effectiveId = cursorId;
        if (effectiveId == null) {
            effectiveId = Long.MAX_VALUE;
        }

        List<PublicDailyQuestionAnswer> fetched = publicDailyQuestionAnswerService.findFeed(
                pdqId, memberId, effectiveAnsweredAt, effectiveId, size + 1);

        boolean hasNext = fetched.size() > size;
        List<PublicDailyQuestionAnswer> items = fetched;
        if (hasNext) {
            items = fetched.subList(0, size);
        }

        List<Long> answerIds = items.stream().map(PublicDailyQuestionAnswer::getId).toList();
        Map<Long, Long> likeCounts = publicDailyQuestionAnswerLikeService.countByAnswerIds(answerIds);
        Set<Long> likedIds = publicDailyQuestionAnswerLikeService.findLikedAnswerIdsByMember(answerIds, memberId);

        return GetPublicDailyQuestionAnswersResponse.from(items, likeCounts, likedIds, hasNext);
    }

    @Transactional
    public ToggleLikeResponse toggleLike(Long memberId, Long pdqId, Long answerId) {
        PublicDailyQuestionAnswer answer = publicDailyQuestionAnswerService
                .findByIdAndPdqIdOrThrow(answerId, pdqId);
        Member member = memberService.findById(memberId);
        boolean liked = publicDailyQuestionAnswerLikeService.toggle(answer, member);
        return new ToggleLikeResponse(liked);
    }
}
